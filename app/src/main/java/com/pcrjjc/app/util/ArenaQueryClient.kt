package com.pcrjjc.app.util  
  
import android.util.Log  
import okhttp3.MediaType.Companion.toMediaType  
import okhttp3.MultipartBody  
import okhttp3.OkHttpClient  
import okhttp3.RequestBody.Companion.toRequestBody  
import okhttp3.Request  
import org.json.JSONObject  
import java.util.concurrent.TimeUnit  
  
class ArenaQueryClient {  
  
    companion object {  
        private const val TAG = "ArenaQueryClient"  
        private const val SERVER_URL = "http://IP:端口"  
    }  
  
    data class ArenaResult(  
        val atkUnits: List<Int>,  
        val upVote: Int,  
        val downVote: Int,  
        val score: Double,  
        val updated: String,  
        val teamType: String = "normal"  
    )  
  
    data class TeamResult(  
        val defenseIndex: Int,  
        val defenseIds: List<Int>,  
        val attacks: List<ArenaResult>  
    )  
  
    /** 服务器完整响应 */  
    data class ServerArenaResponse(  
        val code: Int,  
        val message: String,  
        val teamCount: Int,  
        val defenseTeams: List<List<Int>>,  
        val results: List<TeamResult>,  
        val image: String?  // base64 编码的 PNG 图片，PJJC 无冲配队渲染结果  
    )  
  
    private val client = OkHttpClient.Builder()  
        .connectTimeout(30, TimeUnit.SECONDS)  
        .writeTimeout(60, TimeUnit.SECONDS)  
        .readTimeout(180, TimeUnit.SECONDS)  
        .build()  
  
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
                    return ServerArenaResponse(-1, "服务器请求失败: ${resp.code}", 0, emptyList(), emptyList(), null)  
                }  
  
                val body = resp.body?.string()  
                if (body.isNullOrEmpty()) {  
                    return ServerArenaResponse(-1, "服务器返回空数据", 0, emptyList(), emptyList(), null)  
                }  
  
                Log.i(TAG, "服务器返回: ${body.take(200)}")  
                return parseServerResponse(body)  
            }  
        } catch (e: Exception) {  
            Log.e(TAG, "queryByImage 失败", e)  
            return ServerArenaResponse(-1, "网络错误: ${e.message}", 0, emptyList(), emptyList(), null)  
        }  
    }  
  
    private fun parseServerResponse(body: String): ServerArenaResponse {  
        val json = JSONObject(body)  
        val code = json.optInt("code", -1)  
        val message = json.optString("message", "")  
        val teamCount = json.optInt("team_count", 0)  
        val imageB64 = json.optString("image", null)  // 可能为 null  
  
        if (code != 0) {  
            return ServerArenaResponse(code, message, teamCount, emptyList(), emptyList(), imageB64)  
        }  
  
        // 解析 defense  
        val defenseArray = json.optJSONArray("defense") ?: org.json.JSONArray()  
        val defenseTeams = mutableListOf<List<Int>>()  
        for (i in 0 until defenseArray.length()) {  
            val team = defenseArray.getJSONArray(i)  
            defenseTeams.add((0 until team.length()).map { team.getInt(it) })  
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
                attacks.add(parseArenaResult(attacksArr.getJSONObject(j)))  
            }  
            results.add(TeamResult(defIndex, defIds, attacks))  
        }  
  
        return ServerArenaResponse(code, message, teamCount, defenseTeams, results, imageB64)  
    }  
  
    private fun parseArenaResult(atk: JSONObject): ArenaResult {  
        val atkUnits = atk.optJSONArray("atk_units")?.let { arr ->  
            (0 until arr.length()).map { arr.getInt(it) }  
        } ?: emptyList()  
        return ArenaResult(  
            atkUnits = atkUnits,  
            upVote = atk.optInt("up", 0),  
            downVote = atk.optInt("down", 0),  
            score = atk.optDouble("score", 0.0),  
            updated = atk.optString("updated", ""),  
            teamType = atk.optString("team_type", "normal")  
        )  
    }  
}