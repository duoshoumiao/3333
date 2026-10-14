package com.pcrjjc.app.data.remote  
 
import com.pcrjjc.app.data.remote.AppPushClient 
import com.pcrjjc.app.data.local.entity.PcrBind  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.withContext  
import okhttp3.MediaType.Companion.toMediaType  
import okhttp3.OkHttpClient  
import okhttp3.Request  
import okhttp3.RequestBody.Companion.toRequestBody  
import org.json.JSONArray  
import org.json.JSONObject  
import java.util.concurrent.TimeUnit  
  
/**  
 * 服务端 pcrjjc2 推送客户端（对应 pcrjjc2/app_push.py，端口 8090）。  
 * register: 把本地绑定同步到服务端 pcrbind 表（group=-1 标记为 App）  
 * poll:     长轮询收取 sendNotice 推送下来的文本  
 */  
class AppPushClient(private val serverUrl: String) {  
  
    private val client = OkHttpClient.Builder()  
        .connectTimeout(15, TimeUnit.SECONDS)  
        .readTimeout(70, TimeUnit.SECONDS)  // 必须大于 poll 的 timeout  
        .build()  
  
    suspend fun register(userId: Long, binds: List<PcrBind>): Boolean =  
        withContext(Dispatchers.IO) {  
            val items = JSONArray()  
            binds.forEach {  
                items.put(JSONObject().apply {  
                    put("platform", it.platform)  
                    put("pcrid", it.pcrid)  
                    put("name", it.name ?: "")  
                    put("jjc_notice", it.jjcNotice)  
                    put("pjjc_notice", it.pjjcNotice)  
                    put("up_notice", it.upNotice)  
                    put("online_notice", it.onlineNotice)  
                })
            }  
            val body = JSONObject()  
                .put("user_id", userId)  
                .put("items", items)  
                .toString()  
                .toRequestBody("application/json".toMediaType())  
            try {  
                client.newCall(  
                    Request.Builder().url("$serverUrl/app/register").post(body).build()  
                ).execute().use { resp ->  
                    resp.isSuccessful && JSONObject(resp.body!!.string())  
                        .optInt("code") == 0  
                }  
            } catch (e: Exception) {  
                false  
            }  
        }  
  
    /** 服务端推送的一条消息，对应 push_to_app 的 payload */  
    data class RemoteNotice(  
        val msg: String,  
        val pcrid: Long,  
        val name: String,  
        val platform: Int,  
        val item: Int,       // 0=jjc 1=pjjc 2=online（与服务端 NoticeType 一致）  
        val before: Int,  
        val after: Int,  
        val date: Long  
    )  
  
    /** 长轮询，返回结构化推送列表；超时/出错返回空列表。  
     * 兼容旧协议：元素是纯字符串时包装成只有 msg 的 RemoteNotice */  
    suspend fun poll(userId: Long, timeout: Long = 30): List<RemoteNotice> =  
        withContext(Dispatchers.IO) {  
            try {  
                client.newCall(  
                    Request.Builder()  
                        .url("$serverUrl/app/poll?uid=$userId&timeout=$timeout")  
                        .get().build()  
                ).execute().use { resp ->  
                    if (!resp.isSuccessful) return@withContext emptyList()  
                    val arr = JSONObject(resp.body!!.string()).optJSONArray("msgs")  
                        ?: return@withContext emptyList()  
                    (0 until arr.length()).mapNotNull { i ->  
                        when (val e = arr.opt(i)) {  
                            is JSONObject -> RemoteNotice(  
                                msg = e.optString("msg"),  
                                pcrid = e.optLong("pcrid"),  
                                name = e.optString("name"),  
                                platform = e.optInt("platform"),  
                                item = e.optInt("item"),  
                                before = e.optInt("before"),  
                                after = e.optInt("after"),  
                                date = e.optLong("date")  
                            )  
                            is String -> RemoteNotice(  
                                msg = e, pcrid = 0, name = "", platform = 0,  
                                item = 0, before = 0, after = 0, date = 0  
                            )  
                            else -> null  
                        }  
                    }  
                }  
            } catch (e: Exception) {  
                emptyList()  
            }  
        }
		
    /** 即时查询：返回 /profile/get_profile 的完整 res（含 user_info），失败返回 null */  
    suspend fun queryProfile(platform: Int, pcrid: Long): Map<String, Any?>? =  
        withContext(Dispatchers.IO) {  
            val body = JSONObject()  
                .put("platform", platform)  
                .put("pcrids", JSONArray().put(pcrid))  
                .toString().toRequestBody("application/json".toMediaType())  
            try {  
                client.newCall(  
                    Request.Builder().url("$serverUrl/app/query").post(body).build()  
                ).execute().use { resp ->  
                    if (!resp.isSuccessful) return@withContext null  
                    val arr = JSONObject(resp.body!!.string()).optJSONArray("results")  
                    if (arr == null || arr.length() == 0) return@withContext null  
                    jsonToMap(arr.getJSONObject(0))  
                }  
            } catch (e: Exception) {  
                null  
            }  
        }  
  
    private fun jsonToMap(obj: JSONObject): Map<String, Any?> {  
        val map = mutableMapOf<String, Any?>()  
        for (key in obj.keys()) map[key] = jsonElem(obj, key)  
        return map  
    }  
  
    private fun jsonElem(obj: JSONObject, key: String): Any? {  
        if (obj.isNull(key)) return null  
        return when (val v = obj.get(key)) {  
            is JSONObject -> jsonToMap(v)  
            is JSONArray -> (0 until v.length()).map { arrElem(v, it) }  
            else -> v  
        }  
    }  
  
    private fun arrElem(arr: JSONArray, i: Int): Any? = when (val v = arr.opt(i)) {  
        is JSONObject -> jsonToMap(v)  
        is JSONArray -> (0 until v.length()).map { arrElem(v, it) }  
        JSONObject.NULL -> null  
        else -> v  
    }
}