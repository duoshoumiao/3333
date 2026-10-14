package com.pcrjjc.app.data.remote  
  
import android.util.Log  
import com.pcrjjc.app.data.local.entity.PcrBind  
import com.pcrjjc.app.domain.QueryEngine  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.delay  
import kotlinx.coroutines.withContext  
import okhttp3.MediaType.Companion.toMediaType  
import okhttp3.OkHttpClient  
import okhttp3.Request  
import okhttp3.RequestBody.Companion.toRequestBody  
import org.json.JSONArray  
import org.json.JSONObject  
import java.util.concurrent.TimeUnit  
  
/**  
 * 服务器端 pcrjjc2 批量查询客户端。  
 * 对应 pcrjjc2/query.py 的 query2: POST {server}/batch?priority=N  
 * 响应为流式 SSE 风格数据块，每块 "data: {...}"：  
 *   code == 0   -> data 为 user_info（viewer_id 可能是数字或 {"id": idx}）  
 *   code == 503 -> 服务器维护中，响应头 retry-after 为秒数  
 */  
class RemotePcrClient(private val serverUrl: String) {  
  
    companion object {  
        private const val TAG = "RemotePcrClient"  
        const val PRIORITY_QUERY_ALL = 10  // 与 pcrjjc2 Priority.query_all 保持一致  
    }  
  
    private val client = OkHttpClient.Builder()  
        .connectTimeout(30, TimeUnit.SECONDS)  
        .writeTimeout(60, TimeUnit.SECONDS)  
        .readTimeout(300, TimeUnit.SECONDS)  // 服务端排队返回，读超时拉长  
        .build()  
  
    /**  
     * 批量查询 binds 的 profile，每个结果回调一次（等价本地 queryAll 逐个返回）。  
     * 返回 retry-after 秒数（503 维护时），正常返回 null。  
     */  
    suspend fun queryAll(  
        binds: List<PcrBind>,  
        priority: Int = PRIORITY_QUERY_ALL,  
        onResult: suspend (QueryEngine.QueryResult) -> Unit  
    ): Long? = withContext(Dispatchers.IO) {  
        if (binds.isEmpty()) return@withContext null  
  
        val pcrids = JSONArray()  
        binds.forEach { pcrids.put(it.pcrid) }  
  
        val request = Request.Builder()  
            .url("$serverUrl/batch?priority=$priority")  
            .post(pcrids.toString().toRequestBody("application/json".toMediaType()))  
            .build()  
  
        var retryAfter: Long? = null  
  
        try {  
            client.newCall(request).execute().use { resp ->  
                val source = resp.body?.source() ?: return@use  
                // 流式逐行读取，服务端按 chunk 推送 "data: {json}"  
                while (!source.exhausted()) {  
                    val line = source.readUtf8Line() ?: break  
                    if (line.isBlank()) continue  
                    val payload = if (line.startsWith("data:")) line.substring(5).trim() else line.trim()  
                    if (payload.isEmpty()) continue  
  
                    try {  
                        val result = JSONObject(payload)  
                        when (result.optInt("code", -1)) {  
                            0 -> {  
                                val data = result.getJSONObject("data")  
                                val bind = matchBind(binds, data)  
                                if (bind == null) {  
                                    Log.w(TAG, "无法匹配 viewer_id: $data")  
                                    continue  
                                }  
                                val userInfo = jsonToMap(data)  
                                onResult(  
                                    QueryEngine.QueryResult(  
                                        bind = bind,  
                                        userInfo = userInfo,  
                                        fullResponse = mapOf("user_info" to userInfo)  
                                    )  
                                )  
                            }  
                            503 -> {  
                                retryAfter = resp.header("retry-after")?.toLongOrNull()  
                                Log.w(TAG, "服务器维护中，retry-after=$retryAfter")  
                                return@use  // 结束本轮  
                            }  
                            else -> Log.w(TAG, "服务器异常 code=${result.optInt("code")}")  
                        }  
                    } catch (e: org.json.JSONException) {  
                        // 与非 JSON 块/心跳一致，忽略（对应 query2 的 JSONDecodeError 分支）  
                        continue  
                    }  
                }  
            }  
        } catch (e: Exception) {  
            Log.e(TAG, "竞技场查询失败: ${e.message}", e)  
        }  
        retryAfter  
    }  
  
    /**  
     * 把返回的 data.viewer_id 映射回 bind。  
     * pcrjjc2 中 data["viewer_id"] 可能是数字，也可能是 {"id": index_in_query_list}。  
     */  
    private fun matchBind(binds: List<PcrBind>, data: JSONObject): PcrBind? {  
        return when (val v = data.opt("viewer_id")) {  
            is Number -> binds.firstOrNull { it.pcrid == v.toLong() }  
            is JSONObject -> {  
                val idx = v.optInt("id", -1)  
                when {  
                    idx in binds.indices -> binds[idx]  
                    else -> {  
                        val vid = v.optLong("viewer_id", v.optLong("uid", -1))  
                        binds.firstOrNull { it.pcrid == vid }  
                    }  
                }  
            }  
            else -> null  
        }  
    }  
  
    private fun jsonToMap(obj: JSONObject): Map<String, Any?> {  
        val map = mutableMapOf<String, Any?>()  
        for (key in obj.keys()) {  
            map[key] = if (obj.isNull(key)) null else obj.get(key)  
        }  
        return map  
    }  
}