package com.pcrjjc.app.data.remote

import android.util.Log
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Bilibili game SDK authentication corresponding to pcrjjc2/client/bsgamesdk.py
 */
class BiliAuth(
    val account: String,
    val password: String,
    val qudao: Int
) {
    companion object {
        private const val TAG = "BiliAuth"
        private const val BILI_LOGIN_URL = "https://line1-sdk-center-login-sh.biligame.net/"
        private const val SALT = "fe8aac4e02f845b8ad67c427d48bfaf1"

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val platform: String = if (qudao == 0) "2" else "4"

    private val modolRsa = mapOf(
        "operators" to "5", "merchant_id" to "1", "isRoot" to "0",
        "domain_switch_count" to "0", "sdk_type" to "1", "sdk_log_type" to "1",
        "timestamp" to "1613035485639", "support_abis" to "x86,armeabi-v7a,armeabi",
        "access_key" to "", "sdk_ver" to "3.4.2", "oaid" to "",
        "dp" to "1280*720", "original_domain" to "", "imei" to "227656364311444",
        "version" to "1", "udid" to "KREhESMUIhUjFnJKNko2TDQFYlZkB3cdeQ==",
        "apk_sign" to "e89b158e4bcf988ebd09eb83f5378e87", "platform_type" to "3",
        "old_buvid" to "XZA2FA4AC240F665E2F27F603ABF98C615C29",
        "android_id" to "84567e2dda72d1d4", "fingerprint" to "",
        "mac" to "08:00:27:53:DD:12", "server_id" to "1592",
        "domain" to "line1-sdk-center-login-sh.biligame.net", "app_id" to "1370",
        "version_code" to "90", "net" to "4", "pf_ver" to "6.0.1",
        "cur_buvid" to "XZA2FA4AC240F665E2F27F603ABF98C615C29",
        "c" to "1", "brand" to "Android", "client_timestamp" to "1613035486888",
        "channel_id" to "1", "uid" to "", "game_id" to "1370",
        "ver" to "2.4.10", "model" to "MuMu"
    )

    private val modolLogin = mapOf(
        "operators" to "5", "merchant_id" to "1", "isRoot" to "0",
        "domain_switch_count" to "0", "sdk_type" to "1", "sdk_log_type" to "1",
        "timestamp" to "1613035508188", "support_abis" to "x86,armeabi-v7a,armeabi",
        "access_key" to "", "sdk_ver" to "3.4.2", "oaid" to "",
        "dp" to "1280*720", "original_domain" to "", "imei" to "227656364311444",
        "gt_user_id" to "fac83ce4326d47e1ac277a4d552bd2af", "seccode" to "",
        "version" to "1", "udid" to "KREhESMUIhUjFnJKNko2TDQFYlZkB3cdeQ==",
        "apk_sign" to "e89b158e4bcf988ebd09eb83f5378e87", "platform_type" to "3",
        "old_buvid" to "XZA2FA4AC240F665E2F27F603ABF98C615C29",
        "android_id" to "84567e2dda72d1d4", "fingerprint" to "", "validate" to "",
        "mac" to "08:00:27:53:DD:12", "server_id" to "1592",
        "domain" to "line1-sdk-center-login-sh.biligame.net", "app_id" to "1370",
        "pwd" to "", "version_code" to "90", "net" to "4", "pf_ver" to "6.0.1",
        "cur_buvid" to "XZA2FA4AC240F665E2F27F603ABF98C615C29",
        "c" to "1", "brand" to "Android", "client_timestamp" to "1613035509437",
        "channel_id" to "1", "uid" to "", "captcha_type" to "1",
        "game_id" to "1370", "challenge" to "", "user_id" to "",
        "ver" to "2.4.10", "model" to "MuMu"
    )

    private val modolCaptch = mapOf(
        "operators" to "5", "merchant_id" to "1", "isRoot" to "0",
        "domain_switch_count" to "0", "sdk_type" to "1", "sdk_log_type" to "1",
        "timestamp" to "1613035486182", "support_abis" to "x86,armeabi-v7a,armeabi",
        "access_key" to "", "sdk_ver" to "3.4.2", "oaid" to "",
        "dp" to "1280*720", "original_domain" to "", "imei" to "227656364311444",
        "version" to "1", "udid" to "KREhESMUIhUjFnJKNko2TDQFYlZkB3cdeQ==",
        "apk_sign" to "e89b158e4bcf988ebd09eb83f5378e87", "platform_type" to "3",
        "old_buvid" to "XZA2FA4AC240F665E2F27F603ABF98C615C29",
        "android_id" to "84567e2dda72d1d4", "fingerprint" to "",
        "mac" to "08:00:27:53:DD:12", "server_id" to "1592",
        "domain" to "line1-sdk-center-login-sh.biligame.net", "app_id" to "1370",
        "version_code" to "90", "net" to "4", "pf_ver" to "6.0.1",
        "cur_buvid" to "XZA2FA4AC240F665E2F27F603ABF98C615C29",
        "c" to "1", "brand" to "Android", "client_timestamp" to "1613035487431",
        "channel_id" to "1", "uid" to "", "game_id" to "1370",
        "ver" to "2.4.10", "model" to "MuMu"
    )

    /**
     * Set sign for Bilibili SDK requests (corresponds to setsign() in bsgamesdk.py)
     */
    private fun setSign(data: MutableMap<String, String>): String {
        data["timestamp"] = (System.currentTimeMillis() / 1000).toString()
        data["client_timestamp"] = (System.currentTimeMillis() / 1000).toString()

        val dataStr = StringBuilder()
        for ((key, value) in data) {
            if (key == "pwd") {
                val pwd = URLEncoder.encode(value, "UTF-8")
                dataStr.append("$key=$pwd&")
            }
            dataStr.append("$key=$value&")
        }

        val sign = data.toSortedMap().values.joinToString("") + SALT
        val md5 = MessageDigest.getInstance("MD5")
            .digest(sign.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        dataStr.append("sign=$md5")
        return dataStr.toString()
    }

    private fun sendPost(url: String, data: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .post(data.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .addHeader("User-Agent", "Mozilla/5.0 BSGameSDK")
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .addHeader("Host", "line1-sdk-center-login-sh.biligame.net")
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: "{}"
        return JSONObject(responseBody)
    }


    /**
     * Internal login with optional captcha (corresponds to _login() in bsgamesdk.py)
     */
    private fun internalLogin(
        account: String,
        password: String,
        challenge: String = "",
        gtUser: String = "",
        validate: String = ""
    ): JSONObject {
        val rsaData = mutableMapOf<String, String>()
        rsaData.putAll(modolRsa)
        val rsaResponse = sendPost(BILI_LOGIN_URL + "api/client/rsa", setSign(rsaData))
        val publicKey = rsaResponse.getString("rsa_key")
        val hash = rsaResponse.getString("hash")

        val loginData = mutableMapOf<String, String>()
        loginData.putAll(modolLogin)
        loginData["access_key"] = ""
        loginData["gt_user_id"] = gtUser
        loginData["uid"] = ""
        loginData["challenge"] = challenge
        loginData["user_id"] = account
        loginData["validate"] = validate
        if (validate.isNotEmpty()) {
            loginData["seccode"] = "$validate|jordan"
        }
        loginData["pwd"] = RsaCrypto.rsaEncrypt(hash + password, publicKey)

        return sendPost(BILI_LOGIN_URL + "api/client/login", setSign(loginData))
    }

    /**
     * Bilibili login (corresponds to login() in bsgamesdk.py)
     */
    suspend fun bLogin(): Pair<String, String> {
        if (qudao == 0) {
            // B server login
            for (i in 0 until 3) {
                val resp = internalLogin(account, password)
                val code = resp.optInt("code", -1)
                val message = resp.optString("message", "")

                if (message == "用户名或密码错误") {
                    throw ApiException("用户名或密码错误", 401)
                }

                if (code == 0) {
                    Log.i(TAG, "Login succeeded")
                    return Pair(resp.getString("uid"), resp.getString("access_key"))
                }

                if (code == 200000) {
                    // Captcha required - try auto-captcha
                    Log.w(TAG, "Captcha required, attempting auto-verify")
                    val captchaData = mutableMapOf<String, String>()
                    captchaData.putAll(modolCaptch)
                    val cap = sendPost(
                        BILI_LOGIN_URL + "api/client/start_captcha",
                        setSign(captchaData)
                    )

                    try {
                        val result = captchaVerify(
                            cap.getString("gt"),
                            cap.getString("challenge"),
                            cap.getString("gt_user_id")
                        )
                        val loginSta = internalLogin(
                            account, password,
                            result.first, result.second, result.third
                        )
                        if (loginSta.optInt("code") == 0) {
                            return Pair(
                                loginSta.getString("uid"),
                                loginSta.getString("access_key")
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Captcha verification failed: ${e.message}")
                    }
                }
            }
            throw ApiException("登录失败", 401)
        } else {
            // Channel server: account is uid, password is access_key
            return Pair(account, password)
        }
    }

    /**
     * Auto captcha verification (corresponds to captchaVerifier() in bsgamesdk.py)
     */
    private fun captchaVerify(
        gt: String,
        challenge: String,
        userId: String
    ): Triple<String, String, String> {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("https://pcrd.tencentbot.top/geetest_renew?captcha_type=1&challenge=$challenge&gt=$gt&userid=$userId&gs=1")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "pcrjjc2/1.0.0")
            .build()

        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "{}")
        val uuid = json.getString("uuid")

        for (i in 0 until 10) {
            val checkRequest = Request.Builder()
                .url("https://pcrd.tencentbot.top/check/$uuid")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "pcrjjc2/1.0.0")
                .build()

            val checkResponse = client.newCall(checkRequest).execute()
            val checkJson = JSONObject(checkResponse.body?.string() ?: "{}")

            if (checkJson.has("queue_num")) {
                val waitTime = minOf(checkJson.getInt("queue_num"), 3) * 10000L
                Thread.sleep(waitTime)
                continue
            }

            val info = checkJson.get("info")
            if (info is JSONObject && info.has("validate")) {
                return Triple(
                    info.getString("challenge"),
                    info.getString("gt_user_id"),
                    info.getString("validate")
                )
            }

            if (info is String && (info == "fail" || info == "url invalid")) {
                throw Exception("Auto captcha failed")
            }

            if (info is String && info == "in running") {
                Thread.sleep(5000)
            }
        }

        throw Exception("Auto captcha failed after multiple attempts")
    }
}
