package com.pcrjjc.app.data.remote

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class PcrClient(
    private val biliAuth: BiliAuth,
    private val httpClient: OkHttpClient = sharedHttpClient,
    private val accountId: Int = 0,
    preferredApiRoot: String? = null
) {
    companion object {
        private const val TAG = "PcrClient"

        /**
         * 服务器判定「会话失效」的 result_code：
         * 6002 / 4 对应 PPPPPP clientpool.SessionErrorHandler 的重登条件。
         */
        private val SESSION_ERROR_CODES = setOf(4, 6002)

        /**
         * 会话落盘最小间隔：REQUEST-ID 几乎每个响应都会变，
         * 不能每次请求都写磁盘，否则 IO 抖动明显。
         */
        private const val PERSIST_INTERVAL_MS = 60_000L

        val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

        private val API_ROOTS_B = listOf(
            "https://le1-prod-all-gs-gzlj.bilibiligame.net",
            "https://l2-prod-all-gs-gzlj.bilibiligame.net",
            "https://l3-prod-all-gs-gzlj.bilibiligame.net"
        )

        private val API_ROOTS_QU = listOf(
            "https://l1-prod-uo-gs-gzlj.bilibiligame.net",
            "https://l2-prod-uo-gs-gzlj.bilibiligame.net",
            "https://l3-prod-uo-gs-gzlj.bilibiligame.net"
        )

        fun getApiRoot(qudao: Int): String {
            return if (qudao == 0) API_ROOTS_B.random() else API_ROOTS_QU.random()
        }
    }

    /** 可持久化的会话快照（不含密码），用于跨页面 / 跨进程复用登录状态 */
    data class SessionSnapshot(
        val uid: String,
        val accessKey: String,
        val viewerId: Long,
        val sid: String,
        val requestId: String,
        val manifestVer: String,
        val appVer: String,
        val apiRoot: String
    )

    /** 会话信息发生变化（SID / REQUEST-ID 更新）时回调，由 ClientManager 落盘 */
    var onSessionUpdated: ((SessionSnapshot) -> Unit)? = null

    // 实例级别固定 API 节点，避免每次请求随机选服导致会话不一致.
    // 恢复会话时沿用上次的节点，保证 SID 与服务器一致。
    private val apiRoot: String =
        preferredApiRoot?.takeIf { it.isNotBlank() } ?: getApiRoot(biliAuth.qudao)

    var viewerId: Long? = null
    private var uid: String = ""
    private var accessKey: String = ""
    private val callLock = Mutex()

    /** 已落盘的会话指纹，避免每次请求都重复写入 */
    private var lastPersistedKey: String = ""
    private var lastPersistAt: Long = 0L

    private val headers = mutableMapOf(
        "User-Agent" to "Dalvik/2.1.0 (Linux, U, Android 5.1.1, PCRT00 Build/LMY48Z)",
        "X-Unity-Version" to "2018.4.30f1",
        "APP-VER" to "11.7.2",
        "BATTLE-LOGIC-VERSION" to "4",
        "BUNDLE-VER" to "",
        "DEVICE" to "2",
        "DEVICE-ID" to "7b1703a5d9b394e24051d7a5d5518f17",
        "DEVICE-NAME" to "OPPO PCRT00",
        "EXCEL-VER" to "1.0.0",
        "GRAPHICS-DEVICE-NAME" to "Adreno (TM) 640",
        "IP-ADDRESS" to "10.0.2.15",
        "KEYCHAIN" to "",
        "LOCALE" to "CN",
        "PLATFORM-OS-VERSION" to "Android OS 5.1.1 / API-22 (LMY48Z/rel.se.infra.20200612.100533)",
        "REGION-CODE" to "",
        "RES-KEY" to "ab00a0a6dd915a052a2ef7fd649083e5",
        "RES-VER" to "10002200",
        "SHORT-UDID" to "0",
        "CHANNEL-ID" to "1",
        "PLATFORM" to "2",
        "Connection" to "Keep-Alive"
    )

    init {
        headers["PLATFORM-ID"] = biliAuth.platform
        if (biliAuth.qudao == 1) {
            headers["RES-KEY"] = "d145b29050641dac2f8b19df0afe0e59"
        }
    }

    // ==================== 会话复用 / 持久化 ====================

    /**
     * 直接用已保存的会话恢复客户端：不发任何网络请求。
     * 若会话其实已失效，第一次 callApi 会抛 [SessionExpiredException]，
     * 由 ClientManager 用 token 快速重登后重试。
     */
    fun restoreSession(snapshot: SessionSnapshot) {
        viewerId = snapshot.viewerId.takeIf { it > 0L }
        uid = snapshot.uid
        accessKey = snapshot.accessKey
        if (snapshot.sid.isNotBlank()) headers["SID"] = snapshot.sid
        if (snapshot.requestId.isNotBlank()) headers["REQUEST-ID"] = snapshot.requestId
        if (snapshot.manifestVer.isNotBlank()) headers["MANIFEST-VER"] = snapshot.manifestVer
        if (snapshot.appVer.isNotBlank()) headers["APP-VER"] = snapshot.appVer
        lastPersistedKey = snapshotKey()
        lastPersistAt = System.currentTimeMillis()
        Log.i(TAG, "restored session for account $accountId, viewerId=${snapshot.viewerId}")
    }

    fun sessionSnapshot(): SessionSnapshot = SessionSnapshot(
        uid = uid,
        accessKey = accessKey,
        viewerId = viewerId ?: 0L,
        sid = headers["SID"].orEmpty(),
        requestId = headers["REQUEST-ID"].orEmpty(),
        manifestVer = headers["MANIFEST-VER"].orEmpty(),
        appVer = headers["APP-VER"].orEmpty(),
        apiRoot = apiRoot
    )

    private fun snapshotKey(): String =
        "${headers["SID"].orEmpty()}|${headers["REQUEST-ID"].orEmpty()}|$viewerId|$uid"

    /** 会话信息有变化才回调，避免每个请求都写磁盘 */
    private fun persistIfChanged(force: Boolean = false) {
        val key = snapshotKey()
        val now = System.currentTimeMillis()
        val unchanged = key == lastPersistedKey
        if (unchanged && !force) return
        if (!force && now - lastPersistAt < PERSIST_INTERVAL_MS) return
        lastPersistedKey = key
        lastPersistAt = now
        runCatching { onSessionUpdated?.invoke(sessionSnapshot()) }
    }

    // ==================== API 调用 ====================

    /** 正在执行登录流程：此时不再自愈，避免无限递归 */
    @Volatile
    private var isLoggingIn: Boolean = false

    /** 自愈单飞锁：多个协程同时发现掉线时只重登一次 */
    private val healLock = Mutex()

    /** 每次成功自愈后递增，用来判断"别人已经帮我重登过了" */
    @Volatile
    private var healGeneration: Int = 0

    /** 是否还能自愈（有缓存 token 且不是登录过程中的调用） */
    private val canSelfHeal: Boolean
        get() = !isLoggingIn && uid.isNotBlank() && accessKey.isNotBlank()

    /**
     * 调用游戏 API。
     *
     * 被顶下线 / 登录过期时先在这里自愈：用缓存的 B 站 token 静默重登一次再重试，
     * 因此大多数页面根本感知不到掉线。自愈失败才抛 [SessionExpiredException]，
     * 由 ClientManager 重建客户端。
     */
    suspend fun callApi(
        apiUrl: String,
        request: MutableMap<String, Any?>,
        crypted: Boolean = true,
        noerr: Boolean = true,
        maxRetries: Int = 1
    ): Map<String, Any?> {
        val generation = healGeneration
        return try {
            callApiLocked(apiUrl, request, crypted, noerr, maxRetries)
        } catch (e: SessionExpiredException) {
            if (!canSelfHeal) throw e
            healLock.withLock {
                // 等锁期间别的协程可能已经重登成功，直接重试即可
                if (healGeneration == generation) {
                    Log.w(TAG, "$apiUrl session expired, silent re-login with cached token")
                    try {
                        loginWithToken(uid, accessKey)
                        healGeneration = generation + 1
                    } catch (loginError: Exception) {
                        Log.w(TAG, "silent re-login failed: ${loginError.message}")
                        throw e
                    }
                }
            }
            Log.i(TAG, "session restored, retrying $apiUrl")
            callApiLocked(apiUrl, request, crypted, noerr, maxRetries)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun callApiLocked(
        apiUrl: String,
        request: MutableMap<String, Any?>,
        crypted: Boolean,
        noerr: Boolean,
        maxRetries: Int
    ): Map<String, Any?> {
        return callLock.withLock {
            val key = CryptoUtils.createKey()

            var lastException: Exception? = null
            for (attempt in 0 until maxRetries) {
                try {
                    if (viewerId != null) {
                        request["viewer_id"] = if (crypted) {
                            Base64.encodeToString(
                                CryptoUtils.encrypt(viewerId.toString(), key),
                                Base64.NO_WRAP
                            )
                        } else {
                            viewerId.toString()
                        }
                    }

                    val body = if (crypted) {
                        CryptoUtils.pack(request, key)
                    } else {
                        request.toString().toByteArray(Charsets.UTF_8)
                    }

                    val requestBuilder = Request.Builder()
                        .url(apiRoot + apiUrl)  // ← 使用实例固定的 apiRoot
                        .post(body.toRequestBody("application/octet-stream".toMediaType()))

                    headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

                    val response = httpClient.newCall(requestBuilder.build()).execute()
                    val responseBody = response.body?.bytes()
                        ?: throw ApiException("Empty response", 500)

                    val parsed = if (crypted) {
                        CryptoUtils.unpack(responseBody).first
                    } else {
                        val jsonStr = String(responseBody, Charsets.UTF_8)
                        parseJsonToMap(jsonStr)
                    }

                    val dataHeaders = parsed["data_headers"] as? Map<String, Any?> ?: emptyMap()

                    dataHeaders["viewer_id"]?.let {
                        val vid = (it as? Number)?.toLong() ?: it.toString().toLongOrNull()
                        if (vid != null) {
                            this.viewerId = vid
                        }
                    }

                    // Update SID
                    val sid = dataHeaders["sid"]?.toString()
                    if (!sid.isNullOrEmpty()) {
                        val md5 = MessageDigest.getInstance("MD5")
                        md5.update((sid + "c!SID!n").toByteArray(Charsets.UTF_8))
                        headers["SID"] = md5.digest().joinToString("") { "%02x".format(it) }
                    }

                    dataHeaders["request_id"]?.let {
                        headers["REQUEST-ID"] = it.toString()
                    }

                    val storeUrl = dataHeaders["store_url"]?.toString()
                    if (storeUrl != null) {
                        val match = Regex("_v?(\\d+\\.\\d+\\.\\d+).*?_").find(storeUrl)
                        match?.let { headers["APP-VER"] = it.groupValues[1] }
                    }

                    val data = parsed["data"] as? Map<String, Any?> ?: emptyMap()

                    // ★ 会话失效统一识别：被顶下线 / 登录过期。
                    //   注意这里不受 noerr 影响——否则调用方只会拿到空 Map，
                    //   表现为"进入功能一片空白，只能重新登录"。
                    detectSessionExpiry(dataHeaders, data)?.let { reason ->
                        Log.w(TAG, "$apiUrl session expired: $reason")
                        throw SessionExpiredException(reason)
                    }

                    if (!noerr && data.containsKey("server_error")) {
                        val error = data["server_error"] as? Map<String, Any?> ?: emptyMap()
                        Log.i(TAG, "$apiUrl api failed $error")
                        throw ApiException(
                            error["message"]?.toString() ?: "Unknown error",
                            (error["status"] as? Number)?.toInt() ?: 500
                        )
                    }

                    // 会话（SID / REQUEST-ID / viewer_id）有变化就落盘，供下次冷启动复用
                    persistIfChanged()

                    return@withLock data
                } catch (e: SessionExpiredException) {
                    // 会话失效不重试，交给 ClientManager 重登后再执行
                    throw e
                } catch (e: java.net.SocketTimeoutException) {
                    lastException = e
                    if (attempt < maxRetries - 1) {
                        val waitTime = (attempt + 1) * 500L
                        Log.w(TAG, "API call failed, retrying in ${waitTime}ms: ${e.message}")
                        delay(waitTime)
                    }
                } catch (e: java.io.IOException) {
                    lastException = e
                    if (attempt < maxRetries - 1) {
                        val waitTime = (attempt + 1) * 500L
                        Log.w(TAG, "API call failed, retrying in ${waitTime}ms: ${e.message}")
                        delay(waitTime)
                    }
                } catch (e: ApiException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "API call unknown error: ${e.message}", e)
                    throw ApiException("Unknown error: ${e.message}", 501)
                }
            }
            throw ApiException("Network error: ${lastException?.message}", 503)
        }
    }

    /**
     * 判断响应是否表示会话失效。
     *
     * 对齐 PPPPPP clientpool.SessionErrorHandler：
     *  - result_code 6002 / 4 需要重登；
     *  - sessionmgr 里 status == 3 也会被当成会话失效；
     *  - "回到标题界面" 这类提示同样意味着登录没了。
     *
     * 注意：result_code 只在确实带 server_error 时才作为判据，
     * 避免正常响应里恰好出现同名 code 导致误重登。
     * 另外被顶下线时也可能表现为「空数据 + 失效 result_code」，一并识别。
     */
    private fun detectSessionExpiry(
        dataHeaders: Map<String, Any?>,
        data: Map<String, Any?>
    ): String? {
        val resultCode = (dataHeaders["result_code"] as? Number)?.toInt()
        val serverError = data["server_error"] as? Map<String, Any?>
        val status = (serverError?.get("status") as? Number)?.toInt()
        val message = serverError?.get("message")?.toString().orEmpty()

        val messageSaysExpired = message.contains("回到标题") ||
            message.contains("标题界面") ||
            message.contains("標題畫面")

        val expired = when {
            serverError != null ->
                resultCode in SESSION_ERROR_CODES || status == 3 || messageSaysExpired
            // 无 server_error 但数据为空且 result_code 是会话失效码
            data.isEmpty() && resultCode in SESSION_ERROR_CODES -> true
            else -> false
        }
        if (!expired) return null
        return if (message.isNotBlank()) message else "登录状态已失效，可能已在其他设备登录"
    }

    // ==================== 登录 ====================

    suspend fun loginWithCredentials(loginUid: String, loginAccessKey: String) {
        doGameLogin(loginUid, loginAccessKey)
    }

    suspend fun login() {
        val (loginUid, loginAccessKey) = biliAuth.bLogin()
        doGameLogin(loginUid, loginAccessKey)
    }

    /**
     * 用已缓存的 B 站 token 快速重登：跳过账号密码 + 验证码，
     * 被顶下线后恢复登录状态走这条路。
     */
    suspend fun loginWithToken(loginUid: String, loginAccessKey: String) {
        doGameLogin(loginUid, loginAccessKey)
    }

    private suspend fun doGameLogin(loginUid: String, loginAccessKey: String) {
        this.uid = loginUid
        this.accessKey = loginAccessKey
        // 全新会话：清掉旧会话标识，避免服务器把新登录当成旧会话续期。
        // viewerId 也要清空：否则 manifest 这类明文请求会带上旧 viewer_id。
        this.viewerId = null
        headers.remove("REQUEST-ID")
        headers.remove("SID")

        isLoggingIn = true
        try {
            val manifest = callApi("/source_ini/get_maintenance_status?format=json", mutableMapOf(), crypted = false)
            val ver = manifest["required_manifest_ver"]?.toString() ?: ""
            Log.i(TAG, "using manifest ver = $ver")
            headers["MANIFEST-VER"] = ver

            val lres = callApi("/tool/sdk_login", mutableMapOf(
                "uid" to uid, "access_key" to accessKey,
                "channel" to "1", "platform" to biliAuth.platform
            ))

            val isRisk = (lres["is_risk"] as? Number)?.toInt()
            if (isRisk == 1) throw ApiException("账号存在风险", 403)
            if (lres.containsKey("maintenance_message")) throw ApiException("服务器在维护", 503)

            val gameStart = callApi("/check/game_start", mutableMapOf(
                "apptype" to 0, "campaign_data" to "", "campaign_user" to (0..99999).random()
            ))
            val nowTutorial = gameStart["now_tutorial"]
            if (nowTutorial != null && nowTutorial == false) throw ApiException("该账号没过完教程!", 403)
        } finally {
            isLoggingIn = false
        }

        // 登录完成立即落盘，保证下次冷启动能直接复用
        lastPersistedKey = ""
        persistIfChanged(force = true)
    }

    /**
     * 发送好友申请（移植自 PPPPPP tools.py 的 add_friend）
     * 使用当前已登录账号向 targetViewerId 发送好友请求
     */
    suspend fun sendFriendRequest(targetViewerId: Long) {
        callApi(
            "/friend/request",
            mutableMapOf("target_viewer_id" to targetViewerId),
            noerr = false
        )
    }

    /**
     * 获取好友列表（对应 PPPPPP friend/friend_list，FriendFriendListRequest）
     * 返回 friend_list 元素为 Map，字段见 PPPPPP common.py FriendInfo
     */
    suspend fun getFriendList(): List<Map<String, Any?>> {
        val resp = callApi("/friend/friend_list", mutableMapOf(), noerr = false)
        @Suppress("UNCHECKED_CAST")
        return (resp["friend_list"] as? List<Map<String, Any?>>) ?: emptyList()
    }

    /**
     * 删除好友（对应 PPPPPP friend/remove，FriendRemoveRequest）
     */
    suspend fun removeFriend(targetViewerId: Long) {
        callApi(
            "/friend/remove",
            mutableMapOf("target_viewer_id" to targetViewerId),
            noerr = false
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseJsonToMap(json: String): Map<String, Any?> {
        try {
            val jsonObj = org.json.JSONObject(json)
            return jsonToMap(jsonObj)
        } catch (e: Exception) {
            return mapOf("raw" to json)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun jsonToMap(jsonObj: org.json.JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = jsonObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObj.get(key)
            map[key] = when (value) {
                is org.json.JSONObject -> jsonToMap(value)
                is org.json.JSONArray -> jsonArrayToList(value)
                org.json.JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }

    private fun jsonArrayToList(jsonArray: org.json.JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until jsonArray.length()) {
            val value = jsonArray.get(i)
            list.add(
                when (value) {
                    is org.json.JSONObject -> jsonToMap(value)
                    is org.json.JSONArray -> jsonArrayToList(value)
                    org.json.JSONObject.NULL -> null
                    else -> value
                }
            )
        }
        return list
    }
}
