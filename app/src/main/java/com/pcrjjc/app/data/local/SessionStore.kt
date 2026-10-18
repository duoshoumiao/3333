package com.pcrjjc.app.data.local

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 登录会话持久化。
 *
 * 目的：把「一次登录」的结果（B 站 uid / access_key + 游戏 SID / viewer_id 等）
 * 存到磁盘，App 冷启动或被系统回收后直接复用，不必重新走账号密码登录，
 * 更不会反复触发验证码。
 *
 * 只有服务器判定会话失效（被顶下线 / 过期）时才会清除，清除后由
 * ClientManager 用缓存 token 快速重登，token 也失效时才回落到账号密码登录。
 */
@Singleton
class SessionStore @Inject constructor(@ApplicationContext context: Context) {

    companion object {
        private const val TAG = "SessionStore"
        private const val PREF_NAME = "pcr_session"
        private const val KEY_PREFIX = "session_"
    }

    data class SavedSession(
        val accountId: Int,
        /** 账号/密码/平台指纹，账号被修改后旧会话自动作废 */
        val fingerprint: String,
        val platform: Int,
        val uid: String,
        val accessKey: String,
        val viewerId: Long,
        val sid: String,
        val requestId: String,
        val manifestVer: String,
        val appVer: String,
        val apiRoot: String,
        val savedAt: Long
    ) {
        /** 有 B 站 SDK token，可以免账号密码快速重登 */
        val hasSdkToken: Boolean get() = uid.isNotBlank() && accessKey.isNotBlank()

        /** 有完整游戏会话，可以直接复用，一次网络请求都不用 */
        val hasGameSession: Boolean get() = hasSdkToken && sid.isNotBlank()
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun fingerprint(account: String, password: String, platform: Int): String =
        md5("$account|$password|$platform")

    fun load(accountId: Int, fingerprint: String): SavedSession? {
        val raw = prefs.getString(KEY_PREFIX + accountId, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val saved = SavedSession(
                accountId = accountId,
                fingerprint = obj.optString("fingerprint"),
                platform = obj.optInt("platform", -1),
                uid = obj.optString("uid"),
                accessKey = obj.optString("accessKey"),
                viewerId = obj.optLong("viewerId", 0L),
                sid = obj.optString("sid"),
                requestId = obj.optString("requestId"),
                manifestVer = obj.optString("manifestVer"),
                appVer = obj.optString("appVer"),
                apiRoot = obj.optString("apiRoot"),
                savedAt = obj.optLong("savedAt", 0L)
            )
            // 账号密码改过就不再复用旧会话
            if (saved.fingerprint != fingerprint) {
                Log.i(TAG, "session fingerprint mismatch for account $accountId, ignore")
                null
            } else {
                saved
            }
        } catch (e: Exception) {
            Log.w(TAG, "load session failed for account $accountId", e)
            null
        }
    }

    fun save(session: SavedSession) {
        try {
            val obj = JSONObject().apply {
                put("fingerprint", session.fingerprint)
                put("platform", session.platform)
                put("uid", session.uid)
                put("accessKey", session.accessKey)
                put("viewerId", session.viewerId)
                put("sid", session.sid)
                put("requestId", session.requestId)
                put("manifestVer", session.manifestVer)
                put("appVer", session.appVer)
                put("apiRoot", session.apiRoot)
                put("savedAt", session.savedAt)
            }
            prefs.edit().putString(KEY_PREFIX + session.accountId, obj.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "save session failed for account ${session.accountId}", e)
        }
    }

    fun clear(accountId: Int) {
        try {
            prefs.edit().remove(KEY_PREFIX + accountId).apply()
        } catch (e: Exception) {
            Log.w(TAG, "clear session failed for account $accountId", e)
        }
    }

    fun clearAll() {
        try {
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            Log.w(TAG, "clear all sessions failed", e)
        }
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
