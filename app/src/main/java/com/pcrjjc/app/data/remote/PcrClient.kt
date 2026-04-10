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
    private val httpClient: OkHttpClient = sharedHttpClient  
) {  
    companion object {  
        private const val TAG = "PcrClient"  
  
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
  
    var viewerId: Long? = null                    // ← 修复: Long? 而非 Long  
    private var uid: String = ""  
    private var accessKey: String = ""  
    private val callLock = Mutex()  
  
    private val headers = mutableMapOf(  
        "User-Agent" to "Dalvik/2.1.0 (Linux, U, Android 5.1.1, PCRT00 Build/LMY48Z)",  
        "X-Unity-Version" to "2018.4.30f1",  
        "APP-VER" to "11.7.1",  
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
  
    @Suppress("UNCHECKED_CAST")  
    suspend fun callApi(  
        apiUrl: String,  
        request: MutableMap<String, Any?>,  
        crypted: Boolean = true,  
        noerr: Boolean = true,  
        maxRetries: Int = 3  
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
                        .url(getApiRoot(biliAuth.qudao) + apiUrl)  
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
  
                    // Update SID                          ← 修复: 只保留一个 val sid  
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
  
                    if (!noerr && data.containsKey("server_error")) {  
                        val error = data["server_error"] as? Map<String, Any?> ?: emptyMap()  
                        Log.i(TAG, "$apiUrl api failed $error")  
                        throw ApiException(  
                            error["message"]?.toString() ?: "Unknown error",  
                            (error["status"] as? Number)?.toInt() ?: 500  
                        )  
                    }  
  
                    return@withLock data  
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
  
    suspend fun login() {  
        val (loginUid, loginAccessKey) = biliAuth.bLogin()  
        this.uid = loginUid  
        this.accessKey = loginAccessKey  
  
        headers.remove("REQUEST-ID")  
  
        val manifest = callApi(  
            "/source_ini/get_maintenance_status?format=json",  
            mutableMapOf(), crypted = false  
        )  
  
        val ver = manifest["required_manifest_ver"]?.toString() ?: ""  
        Log.i(TAG, "using manifest ver = $ver")  
        headers["MANIFEST-VER"] = ver  
  
        val lres = callApi(  
            "/tool/sdk_login",  
            mutableMapOf(  
                "uid" to uid,  
                "access_key" to accessKey,  
                "channel" to "1",  
                "platform" to biliAuth.platform  
            )  
        )  
  
        val isRisk = (lres["is_risk"] as? Number)?.toInt()  
        if (isRisk == 1) {  
            throw ApiException("账号存在风险", 403)  
        }  
        if (lres.containsKey("maintenance_message")) {  
            throw ApiException("服务器在维护", 503)  
        }  
  
        val gameStart = callApi(  
            "/check/game_start",  
            mutableMapOf(  
                "apptype" to 0,  
                "campaign_data" to "",  
                "campaign_user" to (0..99999).random()  
            )  
        )  
  
        val nowTutorial = gameStart["now_tutorial"]  
        if (nowTutorial != null && nowTutorial == false) {  
            throw ApiException("该账号没过完教程!", 403)  
        }  
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