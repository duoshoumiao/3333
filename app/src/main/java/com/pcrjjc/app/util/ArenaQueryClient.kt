package com.pcrjjc.app.util  
  
import android.util.Log  
import okhttp3.MediaType.Companion.toMediaType  
import okhttp3.OkHttpClient  
import okhttp3.Request  
import okhttp3.RequestBody.Companion.toRequestBody  
import org.json.JSONObject  
import java.util.concurrent.TimeUnit  
import kotlin.math.ln  
import kotlin.random.Random  
  
/**  
 * 竞技场"怎么拆"查询客户端。  
 * 移植自 arena.py，调用 pcrdfans.com API 查询进攻阵容推荐。  
 */  
class ArenaQueryClient {  
  
    companion object {  
        private const val TAG = "ArenaQueryClient"  
        private const val API_URL = "https://api.pcrdfans.com/x/v1/search"  
    }  
  
    data class ArenaResult(  
        val atkUnits: List<Int>,  // 进攻角色 baseId 列表  
        val upVote: Int,  
        val downVote: Int,  
        val score: Double,  
        val updated: String  
    )  
  
    private val client = OkHttpClient.Builder()  
        .connectTimeout(10, TimeUnit.SECONDS)  
        .readTimeout(10, TimeUnit.SECONDS)  
        .build()  
  
    /**  
     * 查询怎么拆。  
     *  
     * @param defenseIds 防守角色 baseId 列表（4~5个）  
     * @param region 服务器区域: 1=全服, 2=B服, 3=台服, 4=日服  
     * @param sort 排序方式: 1=按时间  
     * @return 进攻阵容推荐列表，按推荐度降序  
     */  
    fun query(defenseIds: List<Int>, region: Int = 2, sort: Int = 1): List<ArenaResult> {  
        if (defenseIds.size < 4 || defenseIds.size > 5) {  
            Log.w(TAG, "防守阵容数量不对: ${defenseIds.size}，需要4~5个")  
            return emptyList()  
        }  
  
        try {  
            // 构造请求体，与 arena.py 一致  
            // def 字段: baseId * 100 + 1  
            val defArray = defenseIds.map { it * 100 + 1 }  
  
            val payload = JSONObject().apply {  
                put("_sign", "a")  
                put("def", org.json.JSONArray(defArray))  
                put("nonce", "a")  
                put("page", 1)  
                put("sort", sort)  
                put("ts", System.currentTimeMillis() / 1000)  
                put("region", region)  
            }  
  
            val body = payload.toString()  
                .toRequestBody("application/json; charset=utf-8".toMediaType())  
  
            val request = Request.Builder()  
                .url(API_URL)  
                .post(body)  
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.87 Safari/537.36")  
                .header("authorization", "") // pcrdfans 公开 API，部分接口不需要 key  
                .build()  
  
            Log.i(TAG, "查询防守阵容: $defenseIds -> API def=$defArray")  
  
            val response = client.newCall(request).execute()  
            response.use { resp ->  
                if (!resp.isSuccessful) {  
                    Log.e(TAG, "API 请求失败: ${resp.code}")  
                    return emptyList()  
                }  
  
                val responseBody = resp.body?.string() ?: return emptyList()  
                return parseResponse(responseBody)  
            }  
        } catch (e: Exception) {  
            Log.e(TAG, "查询失败", e)  
            return emptyList()  
        }  
    }  
  
    /**  
     * 解析 pcrdfans API 返回的 JSON。  
     * 返回格式:  
     * {  
     *   "code": 0,  
     *   "message": "success",  
     *   "data": {  
     *     "result": [  
     *       {  
     *         "id": "...",  
     *         "atk": [{"id": 106001, "star": 6, "equip": 0}, ...],  
     *         "def": [{"id": 107001, "star": 6, "equip": 0}, ...],  
     *         "up": 10,  
     *         "down": 2,  
     *         "updated": "2024-01-01 12:00:00"  
     *       },  
     *       ...  
     *     ]  
     *   }  
     * }  
     */  
    private fun parseResponse(responseBody: String): List<ArenaResult> {  
        val json = JSONObject(responseBody)  
        val code = json.optInt("code", -1)  
        if (code != 0) {  
            Log.w(TAG, "API 返回错误: code=$code, message=${json.optString("message")}")  
            return emptyList()  
        }  
  
        val data = json.optJSONObject("data") ?: return emptyList()  
        val resultArray = data.optJSONArray("result") ?: return emptyList()  
  
        val results = mutableListOf<ArenaResult>()  
        for (i in 0 until resultArray.length()) {  
            val entry = resultArray.getJSONObject(i)  
  
            // 解析进攻阵容  
            val atkArray = entry.optJSONArray("atk") ?: continue  
            val atkUnits = mutableListOf<Int>()  
            for (j in 0 until atkArray.length()) {  
                val unit = atkArray.getJSONObject(j)  
                val unitId = unit.getInt("id")  
                atkUnits.add(unitId / 100) // 转为 baseId  
            }  
  
            val up = entry.optInt("up", 0)  
            val down = entry.optInt("down", 0)  
            val updated = entry.optString("updated", "")  
  
            // 计算推荐度，与 arena.py 的 caculateVal 一致  
            val score = calculateVal(up, down)  
  
            results.add(  
                ArenaResult(  
                    atkUnits = atkUnits,  
                    upVote = up,  
                    downVote = down,  
                    score = score,  
                    updated = updated  
                )  
            )  
        }  
  
        // 按推荐度降序排列  
        return results.sortedByDescending { it.score }.take(10)  
    }  
  
    /**  
     * 移植自 arena.py 的 caculateVal 函数。  
     * 计算阵容推荐度权值。  
     *  
     * val_1 = up / (down + up + 0.0001) * 2 - 1   // 赞踩比 [-1, 1]  
     * val_2 = log(up + down + 0.01, 100)            // 置信度 [-1, +inf]  
     * return val_1 + val_2 + random()/1000  
     */  
    private fun calculateVal(up: Int, down: Int): Double {  
        val total = up + down  
        val val1 = up.toDouble() / (total + 0.0001) * 2.0 - 1.0  
        val val2 = ln(total + 0.01) / ln(100.0)  
        return val1 + val2 + Random.nextDouble() / 1000.0  
    }  
}