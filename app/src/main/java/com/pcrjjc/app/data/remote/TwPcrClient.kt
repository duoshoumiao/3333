package com.pcrjjc.app.data.remote  
  
import android.util.Base64  
import android.util.Log  
import okhttp3.MediaType.Companion.toMediaType  
import okhttp3.OkHttpClient  
import okhttp3.Request  
import okhttp3.RequestBody.Companion.toRequestBody  
import java.security.MessageDigest  
import java.util.concurrent.TimeUnit  
  
class TwPcrClient(  
    private val udid: String,  
    private val shortUdid: String,  
    viewerId: String,  
    private val platformId: Int  
) {  
    companion object {  
        private const val TAG = "TwPcrClient"  
        private const val ALPHABET = "0123456789"  
  
        val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()  
            .connectTimeout(5, TimeUnit.SECONDS)  
            .readTimeout(5, TimeUnit.SECONDS)  
            .build()  
    }  
  
    var viewerId: String = viewerId  
        private set  
    var shouldLogin: Boolean = false  
        private set  
  
    private val httpClient = sharedHttpClient  
  
    private val apiRoot: String = if (platformId == 1) {  
        "https://api-pc.so-net.tw"  
    } else {  
        "https://api5-pc.so-net.tw"  
    }  
  
    private val headers = mutableMapOf(  
        "User-Agent" to "Dalvik/2.1.0 (Linux, U, Android 5.1.1, PCRT00 Build/LMY48Z)",  
        "Content-Type" to "application/octet-stream",  
        "Expect" to "100-continue",  
        "X-Unity-Version" to "2018.4.21f1",  
        "APP-VER" to "4.9.1",  
        "BATTLE-LOGIC-VERSION" to "4",  
        "BUNDLE-VER" to "",  
        "DEVICE" to "2",  
        "DEVICE-ID" to "7b1703a5d9b394e24051d7a5d4818f17",  
        "DEVICE-NAME" to "OPPO PCRT00",  
        "GRAPHICS-DEVICE-NAME" to "Adreno (TM) 640",  
        "IP-ADDRESS" to "10.0.2.15",  
        "KEYCHAIN" to "",  
        "LOCALE" to "Jpn",  
        "PLATFORM-OS-VERSION" to "Android OS 5.1.1 / API-22 (LMY48Z/rel.se.infra.20200612.100533)",  
        "REGION-CODE" to "",  
        "RES-VER" to "00017004",  
        "platform" to "2"  
    )  
  
    init {  
        headers["SID"] = makeMd5(viewerId + udid)  
    }  
  
    private fun makeMd5(str: String): String {  
        val md5 = MessageDigest.getInstance("MD5")  
        md5.update((str + "r!I@nt8e5i=").toByteArray(Charsets.UTF_8))  
        return md5.digest().joinToString("") { "%02x".format(it) }  
    }  
  
    private fun getIv(): ByteArray {  
        return udid.replace("-", "").substring(0, 16).toByteArray(Charsets.UTF_8)  
    }  
  
    private fun encode(dat: String): String {  
        val sb = StringBuilder()  
        sb.append(String.format("%04x", dat.length))  
        for (i in 0 until dat.length * 4) {  
            if (i % 4 == 2) {  
                sb.append((dat[i / 4].code + 10).toChar())  
            } else {  
                sb.append(ALPHABET.random())  
            }  
        }  
        repeat(32) { sb.append(ALPHABET.random()) }  
        return sb.toString()  
    }  
  
    @Suppress("UNCHECKED_CAST")  
    suspend fun callApi(apiUrl: String, request: MutableMap<String, Any?>, noerr: Boolean = false): Map<String, Any?> {  
        val key = CryptoUtils.createKey()  
        val iv = getIv()  
  
        try {  
            if (viewerId.isNotEmpty()) {  
                request["viewer_id"] = Base64.encodeToString(  
                    CryptoUtils.encryptWithIv(viewerId, key, iv),  
                    Base64.NO_WRAP  
                )  
                request["tw_server_id"] = platformId.toString()  
            }  
  
            val (packed, crypted) = CryptoUtils.packWithIv(request, key, iv)  
  
            val paramData = (udid + apiUrl + Base64.encodeToString(packed, Base64.NO_WRAP) + viewerId)  
            val sha1 = MessageDigest.getInstance("SHA-1")  
            sha1.update(paramData.toByteArray(Charsets.UTF_8))  
            headers["PARAM"] = sha1.digest().joinToString("") { "%02x".format(it) }  
            headers["SHORT-UDID"] = encode(shortUdid)  
  
            val requestBuilder = Request.Builder()  
                .url(apiRoot + apiUrl)  
                .post(crypted.toRequestBody("application/octet-stream".toMediaType()))  
  
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }  
  
            val response = httpClient.newCall(requestBuilder.build()).execute()  
            val responseBody = response.body?.bytes()  
                ?: throw ApiException("Empty response", 500)  
  
            val (parsed, _) = CryptoUtils.unpackWithIv(responseBody, iv)  
  
            val dataHeaders = parsed["data_headers"] as? Map<String, Any?> ?: emptyMap()  
  
            dataHeaders["viewer_id"]?.let {  
                this.viewerId = it.toString()  
            }  
            dataHeaders["required_res_ver"]?.let {  
                headers["RES-VER"] = it.toString()  
            }  
  
            val data = parsed["data"] as? Map<String, Any?> ?: emptyMap()

            // 会话失效识别：result_code 6002/4 或 status==3 才需要重登
            val resultCode = (dataHeaders["result_code"] as? Number)?.toInt()
            val serverError = data["server_error"] as? Map<String, Any?>
            val status = (serverError?.get("status") as? Number)?.toInt()
            if (serverError != null &&
                (resultCode == 4 || resultCode == 6002 || status == 3)
            ) {
                shouldLogin = true
                throw SessionExpiredException(
                    serverError["message"]?.toString()
                        ?: "登录状态已失效，可能已在其他设备登录"
                )
            }

            if (!noerr && serverError != null) {
                // 业务错误（好友已存在、上限…）不代表会话失效，不能置 shouldLogin，
                // 否则一次失败就会让所有功能重新登录。
                Log.e(TAG, "$apiUrl api failed code = $resultCode, $serverError")
                throw ApiException(
                    serverError["message"]?.toString() ?: "Unknown error",
                    (serverError["status"] as? Number)?.toInt() ?: 500
                )
            }

            return data
        } catch (e: SessionExpiredException) {
            shouldLogin = true
            throw e
        } catch (e: ApiException) {
            // 业务错误：保持会话可用
            throw e
        } catch (e: Exception) {
            // 网络/解密异常：会话可能已不可用，下次调用重新登录
            shouldLogin = true
            throw e
        }
    }  
  
    suspend fun sendFriendRequest(targetViewerId: Long) {  
        callApi(  
            "/friend/request",  
            mutableMapOf("target_viewer_id" to targetViewerId),  
            noerr = false  
        )  
    }
	suspend fun getFriendList(): List<Map<String, Any?>> {  
        val resp = callApi("/friend/friend_list", mutableMapOf())  
        @Suppress("UNCHECKED_CAST")  
        return (resp["friend_list"] as? List<Map<String, Any?>>) ?: emptyList()  
    }  
  
    suspend fun removeFriend(targetViewerId: Long) {  
        callApi(  
            "/friend/remove",  
            mutableMapOf("target_viewer_id" to targetViewerId)  
        )  
    }
	suspend fun login() {  
        callApi("/check/check_agreement", mutableMapOf())  
        callApi("/check/game_start", mutableMapOf())  
        callApi("/load/index", mutableMapOf("carrier" to "Android"))  
        shouldLogin = false  
    }  
}