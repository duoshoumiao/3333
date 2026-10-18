package com.pcrjjc.app.domain

import android.util.Log
import com.pcrjjc.app.data.local.SessionStore
import com.pcrjjc.app.data.local.entity.Account
import com.pcrjjc.app.data.remote.ApiException
import com.pcrjjc.app.data.remote.BiliAuth
import com.pcrjjc.app.data.remote.CaptchaRequiredException
import com.pcrjjc.app.data.remote.PcrClient
import com.pcrjjc.app.data.remote.SessionExpiredException
import com.pcrjjc.app.data.remote.TwPcrClient
import com.pcrjjc.app.util.Platform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局唯一的登录会话中心。
 *
 * 设计要点（解决"每进一个功能都要重新登录"）：
 *  1. 同一账号的客户端在进程内只登录一次，所有功能（我的支援 / EX管理 /
 *     好友 / 透视 / 黎明界 / 会战监控 …）共享同一个实例；
 *  2. 登录结果（uid / access_key / SID / viewer_id）持久化到 [SessionStore]，
 *     冷启动后直接恢复，不发任何登录请求，也不会反复触发验证码；
 *  3. 只有服务器明确判定会话失效（被顶下线 / 过期）时才重登，且用缓存 token
 *     快速重登，不需要账号密码；
 *  4. 重登是单飞的：多个页面同时发现掉线时只重登一次，其余页面复用结果。
 */
@Singleton
class ClientManager @Inject constructor(
    private val sessionStore: SessionStore
) {

    companion object {
        private const val TAG = "ClientManager"
    }

    /** 进程内共享的客户端实例：accountId -> PcrClient / TwPcrClient */
    private val clients = HashMap<Int, Any>()

    /** 账号级锁：同一账号的登录 / 重登串行化，不同账号互不阻塞 */
    private val accountLocks = HashMap<Int, Mutex>()

    private fun lockFor(accountId: Int): Mutex = synchronized(accountLocks) {
        accountLocks.getOrPut(accountId) { Mutex() }
    }

    private fun cached(account: Account): Any? = synchronized(clients) {
        val existing = clients[account.id]
        // TwPcrClient 自己标记了需要重登时直接作废
        if (existing is TwPcrClient && existing.shouldLogin) {
            Log.i(TAG, "TwPcrClient for account ${account.id} needs re-login")
            clients.remove(account.id)
            null
        } else {
            existing
        }
    }

    private fun store(account: Account, client: Any): Any = storeById(account.id, client)

    private fun storeById(accountId: Int, client: Any): Any {
        synchronized(clients) { clients[accountId] = client }
        return client
    }

    /**
     * 取得账号对应的客户端。
     *
     * 默认走缓存（进程内实例 → 磁盘会话），只有 [forceRelogin] 为 true
     * 或缓存确实不可用时才会重新登录。业务代码请一律使用默认参数。
     */
    suspend fun getClient(account: Account, forceRelogin: Boolean = false): Any {
        if (!forceRelogin) {
            cached(account)?.let {
                Log.d(TAG, "Reusing cached client for account ${account.id}")
                return it
            }
        }
        return lockFor(account.id).withLock {
            if (!forceRelogin) {
                cached(account)?.let { return@withLock it }
            } else {
                synchronized(clients) { clients.remove(account.id) }
                Log.i(TAG, "Force re-login for account ${account.id}")
            }
            // forceRelogin 时优先用缓存 token 快速重建，避免不必要的账号密码登录
            val created = createAndLogin(account, preferCachedToken = forceRelogin)
            store(account, created)
        }
    }

    /** 强制重登（优先用缓存 token，不再走账号密码 / 验证码） */
    suspend fun relogin(account: Account): Any {
        return lockFor(account.id).withLock {
            synchronized(clients) { clients.remove(account.id) }
            val created = createAndLogin(account, preferCachedToken = true)
            store(account, created)
        }
    }

    /**
     * 用账号执行一段需要登录的请求，并在会话被顶下线时自动重登后重试一次。
     * 业务代码应优先使用它，而不是自己判断异常 + clearClient。
     */
    suspend fun <T> withClient(account: Account, block: suspend (Any) -> T): T {
        val client = getClient(account)
        return try {
            block(client)
        } catch (e: SessionExpiredException) {
            Log.w(TAG, "session expired for account ${account.id}, auto re-login and retry")
            val fresh = recover(account, client)
            block(fresh)
        }
    }

    /** 会话失效后的单飞重登：如果别的协程已经换好了客户端，直接复用 */
    private suspend fun recover(account: Account, failed: Any): Any {
        return lockFor(account.id).withLock {
            val current = synchronized(clients) { clients.get(account.id) }
            if (current != null && current !== failed) {
                Log.i(TAG, "client for account ${account.id} already recovered by another caller")
                return@withLock current
            }
            synchronized(clients) { clients.remove(account.id) }
            val created = createAndLogin(account, preferCachedToken = true)
            store(account, created)
        }
    }

    // ==================== 登录实现 ====================

    private fun fingerprintOf(account: Account): String =
        sessionStore.fingerprint(account.account, account.password, account.platform)

    private suspend fun createAndLogin(
        account: Account,
        preferCachedToken: Boolean = false
    ): Any {
        if (account.platform == Platform.TW_SERVER.id) {
            return createTwClient(account)
        }
        return createBiliClient(account, preferCachedToken)
    }

    private suspend fun createTwClient(account: Account): TwPcrClient {
        Log.i(TAG, "Creating new TwPcrClient for account ${account.id}")
        val twPlatform = (account.viewerId.toLong() / 1000000000).toInt()
        val twClient = TwPcrClient(
            account.account,
            account.password,
            account.viewerId,
            twPlatform
        )
        twClient.login()
        return twClient
    }

    private suspend fun createBiliClient(
        account: Account,
        preferCachedToken: Boolean
    ): PcrClient {
        val fingerprint = fingerprintOf(account)
        val saved = sessionStore.load(account.id, fingerprint)

        // ① 磁盘上有完整游戏会话：直接恢复，一个登录请求都不发
        if (!preferCachedToken && saved != null && saved.hasGameSession) {
            Log.i(TAG, "Restoring persisted session for account ${account.id}")
            val restored = newPcrClient(account, saved.apiRoot)
            restored.restoreSession(
                PcrClient.SessionSnapshot(
                    uid = saved.uid,
                    accessKey = saved.accessKey,
                    viewerId = saved.viewerId,
                    sid = saved.sid,
                    requestId = saved.requestId,
                    manifestVer = saved.manifestVer,
                    appVer = saved.appVer,
                    apiRoot = saved.apiRoot
                )
            )
            return restored
        }

        // ② 有 B 站 token：跳过账号密码与验证码，直接换游戏会话
        if (saved != null && saved.hasSdkToken) {
            Log.i(TAG, "Fast re-login with cached token for account ${account.id}")
            val client = newPcrClient(account, saved.apiRoot)
            try {
                client.loginWithToken(saved.uid, saved.accessKey)
                return client
            } catch (e: CaptchaRequiredException) {
                // 需要人工过码：不能悄悄降级成账号密码登录，交给界面处理
                throw e
            } catch (e: Exception) {
                // token 失效（改密码 / 被注销）才回落到账号密码登录
                Log.w(TAG, "cached token rejected for account ${account.id}: ${e.message}")
                sessionStore.clear(account.id)
            }
        }

        // ③ 全新登录（可能触发验证码，由界面处理 CaptchaRequiredException）
        Log.i(TAG, "Full login for account ${account.id}, platform ${account.platform}")
        val client = newPcrClient(account, null)
        client.login()
        return client
    }

    private fun newPcrClient(account: Account, apiRoot: String?): PcrClient {
        val biliAuth = BiliAuth(account.account, account.password, account.platform)
        val client = PcrClient(
            biliAuth = biliAuth,
            accountId = account.id,
            preferredApiRoot = apiRoot
        )
        val fingerprint = fingerprintOf(account)
        // 会话变化（SID / REQUEST-ID 更新）时落盘，供冷启动直接复用
        client.onSessionUpdated = { snapshot ->
            sessionStore.save(
                SessionStore.SavedSession(
                    accountId = account.id,
                    fingerprint = fingerprint,
                    platform = account.platform,
                    uid = snapshot.uid,
                    accessKey = snapshot.accessKey,
                    viewerId = snapshot.viewerId,
                    sid = snapshot.sid,
                    requestId = snapshot.requestId,
                    manifestVer = snapshot.manifestVer,
                    appVer = snapshot.appVer,
                    apiRoot = snapshot.apiRoot,
                    savedAt = System.currentTimeMillis()
                )
            )
        }
        return client
    }

    suspend fun loginWithCaptchaResult(
        accountId: Int,
        account: String,
        password: String,
        platform: Int,
        challenge: String,
        gtUserId: String,
        validate: String
    ): Any {
        return lockFor(accountId).withLock {
            val biliAuth = BiliAuth(account, password, platform)
            val client = PcrClient(biliAuth, accountId = accountId)
            val fingerprint = sessionStore.fingerprint(account, password, platform)
            client.onSessionUpdated = { snapshot ->
                sessionStore.save(
                    SessionStore.SavedSession(
                        accountId = accountId,
                        fingerprint = fingerprint,
                        platform = platform,
                        uid = snapshot.uid,
                        accessKey = snapshot.accessKey,
                        viewerId = snapshot.viewerId,
                        sid = snapshot.sid,
                        requestId = snapshot.requestId,
                        manifestVer = snapshot.manifestVer,
                        appVer = snapshot.appVer,
                        apiRoot = snapshot.apiRoot,
                        savedAt = System.currentTimeMillis()
                    )
                )
            }
            val (uid, accessKey) = biliAuth.bLoginWithValidate(challenge, gtUserId, validate)
            client.loginWithCredentials(uid, accessKey)
            storeById(accountId, client)
            Log.i(TAG, "Manual captcha login succeeded for account $accountId")
            client
        }
    }

    /**
     * 仅丢弃内存中的客户端实例（下次调用会先用磁盘会话静默恢复）。
     * 用于页面级异常兜底，不会让用户重新登录。
     */
    suspend fun clearClient(accountId: Int) {
        synchronized(clients) { clients.remove(accountId) }
        Log.i(TAG, "Cleared cached client for account $accountId (session kept)")
    }

    /** 彻底登出：丢弃内存实例并清除磁盘会话，下次必须重新登录 */
    suspend fun logout(accountId: Int) {
        synchronized(clients) { clients.remove(accountId) }
        sessionStore.clear(accountId)
        Log.i(TAG, "Logged out account $accountId")
    }

    suspend fun clearAll() {
        synchronized(clients) { clients.clear() }
        Log.i(TAG, "All clients cleared")
    }

    /** 账号被删除 / 修改时调用，避免继续使用旧凭据 */
    suspend fun forgetAccount(accountId: Int) {
        logout(accountId)
    }
}
