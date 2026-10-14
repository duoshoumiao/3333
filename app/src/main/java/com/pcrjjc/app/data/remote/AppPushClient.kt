package com.pcrjjc.app.data.remote  
  
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
  
    /** 长轮询，返回服务端推送文本列表；超时/出错返回空列表 */  
    suspend fun poll(userId: Long, timeout: Long = 30): List<String> =  
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
                    (0 until arr.length()).map { arr.getString(it) }  
                }  
            } catch (e: Exception) {  
                emptyList()  
            }  
        }  
}