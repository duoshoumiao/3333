package com.pcrjjc.app.util  
  
import android.util.Log  
import okhttp3.MediaType.Companion.toMediaType  
import okhttp3.MultipartBody  
import okhttp3.OkHttpClient  
import okhttp3.RequestBody.Companion.toRequestBody  
import okhttp3.Request  
import org.json.JSONObject  
import java.util.concurrent.TimeUnit  
  
/**  
 * 竞技场"怎么拆"查询客户端。  
 * 将截图发送到服务器，由服务器完成图像识别 + 作业查询。  
 */  
class ArenaQueryClient {  
  
    companion object {  
        private const val TAG = "ArenaQueryClient"  
        private const val SERVER_URL = "http://119.91.249.245:8020"  
    }  
  
    data class ArenaResult(  
        val atkUnits: List<Int>,  // 进攻角色 baseId 列表  
        val upVote: Int,  
        val downVote: Int,  
        val score: Double,  
        val updated: String  
    )  
  
    data class TeamResult(  
        val defenseIndex: Int,  
        val defenseIds: List<Int>,  
        val attacks: List<ArenaResult>  
    )  
  
    data class ServerArenaResponse(  
        val code: Int,  
        val message: String,  
        val defenseTeams: List<List<Int>>,  
        val results: List<TeamResult>  
    )  
  
    private val client = OkHttpClient.Builder()  
        .connectTimeout(30, TimeUnit.SECONDS)  
        .readTimeout(30, TimeUnit.SECONDS)  
        .build()  
  
    /**  
     * 将截图发送到服务器进行识别 + 查询。  
     *  
     * @param imageBytes 截图的 PNG 字节数组  
     * @param region 服务器区域: 1=全服, 2=B服, 3=台服, 4=日服  
     * @return ServerArenaResponse 包含识别到的防守阵容和进攻推荐  
     */  
    fun queryByImage(imageBytes: ByteArray, region: Int = 2): ServerArenaResponse {  
        try {  
            val requestBody = MultipartBody.Builder()  
                .setType(MultipartBody.FORM)  
                .addFormDataPart(  
                    "image", "screenshot.png",  
                    imageBytes.toRequestBody("image/png".toMediaType())  
                )  
                .addFormDataPart("region", region.toString())  
                .build()  
  
            val request = Request.Builder()  
                .url("$SERVER_URL/api/arena/query_image")  
                .post(requestBody)  
                .build()  
  
            Log.i(TAG, "发送截图到服务器, region=$region, 图片大小=${imageBytes.size}")  
  
            val response = client.newCall(request).execute()  
            response.use { resp ->  
                if (!resp.isSuccessful) {  
                    Log.e(TAG, "服务器请求失败: ${resp.code}")  
                    return ServerArenaResponse(-1, "服务器请求失败: ${resp.code}", emptyList(), emptyList())  
                }  
  
                val body = resp.body?.string()  
                if (body.isNullOrEmpty()) {  
                    return ServerArenaResponse(-1, "服务器返回空数据", emptyList(), emptyList())  
                }  
  
                Log.i(TAG, "服务器返回: ${body.take(200)}")  
                return parseServerResponse(body)  
            }  
        } catch (e: Exception) {  
            Log.e(TAG, "queryByImage 失败", e)  
            return ServerArenaResponse(-1, "网络错误: ${e.message}", emptyList(), emptyList())  
        }  
    }  
  
    private fun parseServerResponse(body: String): ServerArenaResponse {  
        val json = JSONObject(body)  
        val code = json.optInt("code", -1)  
        val message = json.optString("message", "")  
  
        if (code != 0) {  
            return ServerArenaResponse(code, message, emptyList(), emptyList())  
        }  
  
        // 解析 defense 二维数组  
        val defenseArray = json.optJSONArray("defense") ?: org.json.JSONArray()  
        val defenseTeams = mutableListOf<List<Int>>()  
        for (i in 0 until defenseArray.length()) {  
            val team = defenseArray.getJSONArray(i)  
            val ids = (0 until team.length()).map { team.getInt(it) }  
            defenseTeams.add(ids)  
        }  
  
        // 解析 results  
        val resultsArray = json.optJSONArray("results") ?: org.json.JSONArray()  
        val results = mutableListOf<TeamResult>()  
        for (i in 0 until resultsArray.length()) {  
            val item = resultsArray.getJSONObject(i)  
            val defIndex = item.optInt("defense_index", 0)  
            val defIds = item.optJSONArray("defense_ids")?.let { arr ->  
                (0 until arr.length()).map { arr.getInt(it) }  
            } ?: emptyList()  
  
            val attacksArr = item.optJSONArray("attacks") ?: org.json.JSONArray()  
            val attacks = mutableListOf<ArenaResult>()  
            for (j in 0 until attacksArr.length()) {  
                val atk = attacksArr.getJSONObject(j)  
                val atkUnits = atk.optJSONArray("atk_units")?.let { arr ->  
                    (0 until arr.length()).map { arr.getInt(it) }  
                } ?: emptyList()  
                attacks.add(  
                    ArenaResult(  
                        atkUnits = atkUnits,  
                        upVote = atk.optInt("up", 0),  
                        downVote = atk.optInt("down", 0),  
                        score = atk.optDouble("score", 0.0),  
                        updated = atk.optString("updated", "")  
                    )  
                )  
            }  
            results.add(TeamResult(defIndex, defIds, attacks))  
        }  
  
        return ServerArenaResponse(code, message, defenseTeams, results)  
    }  
}