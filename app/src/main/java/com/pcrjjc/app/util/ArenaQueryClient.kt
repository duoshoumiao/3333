package com.pcrjjc.app.util  
  
import android.util.Log  
import okhttp3.MediaType.Companion.toMediaType  
import okhttp3.MultipartBody  
import okhttp3.OkHttpClient  
import okhttp3.Request  
import okhttp3.RequestBody.Companion.toRequestBody  
import org.json.JSONObject  
import java.util.concurrent.TimeUnit  
  
class ArenaQueryClient(private val serverUrl: String? = null) {  
  
    companion object {  
        private const val TAG = "ArenaQueryClient"  
    }  
  
    data class ArenaResult(  
        val atkUnits: List<Int>,  
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
        val teamCount: Int,  
        val defenseTeams: List<TeamResult>,  
        val results: List<TeamResult>,  
        val image: String?  
		val highlightImage: String? = null,   // ★ 新增  
		val compareImage: String? = null      // ★ 新增 
    )  
  
    private val client = OkHttpClient.Builder()  
        .connectTimeout(30, TimeUnit.SECONDS)  
        .writeTimeout(60, TimeUnit.SECONDS)  
        .readTimeout(180, TimeUnit.SECONDS)  
        .build()  
  
    fun queryByImage(imageBytes: ByteArray, region: Int = 2): ServerArenaResponse {  
        val url = serverUrl  
        if (url.isNullOrBlank()) {  
            return ServerArenaResponse(  
                code = -1,  
                message = "未配置服务器地址，请在设置中填写",  
                teamCount = 0,  
                defenseTeams = emptyList(),  
                results = emptyList(),  
                image = null  
            )  
        }  
  
        try {  
            val body = MultipartBody.Builder()  
                .setType(MultipartBody.FORM)  
                .addFormDataPart(  
                    "image", "screenshot.png",  
                    imageBytes.toRequestBody("image/png".toMediaType())  
                )  
                .addFormDataPart("region", region.toString())  
                .build()  
  
            val request = Request.Builder()  
                .url("$url/api/arena/query_image")  
                .post(body)  
                .build()  
  
            Log.i(TAG, "发送图片到服务器: $url, 大小=${imageBytes.size}, region=$region")  
  
            val response = client.newCall(request).execute()  
            response.use { resp ->  
                if (!resp.isSuccessful) {  
                    Log.e(TAG, "服务器请求失败: ${resp.code}")  
                    return ServerArenaResponse(  
                        code = resp.code,  
                        message = "服务器请求失败: ${resp.code}",  
                        teamCount = 0,  
                        defenseTeams = emptyList(),  
                        results = emptyList(),  
                        image = null  
                    )  
                }  
                val responseBody = resp.body?.string() ?: return ServerArenaResponse(  
                    code = -1, message = "空响应", teamCount = 0,  
                    defenseTeams = emptyList(), results = emptyList(), image = null  
                )  
                return parseServerResponse(responseBody)  
            }  
        } catch (e: Exception) {  
            Log.e(TAG, "查询失败", e)  
            return ServerArenaResponse(  
                code = -1,  
                message = "请求异常: ${e.message}",  
                teamCount = 0,  
                defenseTeams = emptyList(),  
                results = emptyList(),  
                image = null  
            )  
        }  
    }  
  
    private fun parseServerResponse(responseBody: String): ServerArenaResponse {  
        val json = JSONObject(responseBody)  
        val code = json.optInt("code", -1)  
        val message = json.optString("message", "")  
        val image = json.optString("image", null)  
        val highlightImage = if (json.isNull("highlight_image")) null else json.optString("highlight_image", null)  
		val compareImage = if (json.isNull("compare_image")) null else json.optString("compare_image", null)
		val teamCount = json.optInt("team_count", 0)  
  
        val defenseTeams = mutableListOf<TeamResult>()  
        val defenseArray = json.optJSONArray("defense")  
        if (defenseArray != null) {  
            for (i in 0 until defenseArray.length()) {  
                val teamObj = defenseArray.getJSONObject(i)  
                val defIndex = teamObj.optInt("index", i)  
                val defIds = mutableListOf<Int>()  
                val idsArray = teamObj.optJSONArray("ids")  
                if (idsArray != null) {  
                    for (j in 0 until idsArray.length()) {  
                        defIds.add(idsArray.getInt(j))  
                    }  
                }  
                defenseTeams.add(TeamResult(defIndex, defIds, emptyList()))  
            }  
        }  
  
        val results = mutableListOf<TeamResult>()  
        val resultsArray = json.optJSONArray("results")  
        if (resultsArray != null) {  
            for (i in 0 until resultsArray.length()) {  
                val teamObj = resultsArray.getJSONObject(i)  
                val defIndex = teamObj.optInt("defense_index", i)  
                val defIds = mutableListOf<Int>()  
                val defIdsArray = teamObj.optJSONArray("defense_ids")  
                if (defIdsArray != null) {  
                    for (j in 0 until defIdsArray.length()) {  
                        defIds.add(defIdsArray.getInt(j))  
                    }  
                }  
  
                val attacks = mutableListOf<ArenaResult>()  
                val attacksArray = teamObj.optJSONArray("attacks")  
                if (attacksArray != null) {  
                    for (j in 0 until attacksArray.length()) {  
                        val atkObj = attacksArray.getJSONObject(j)  
                        val atkUnits = mutableListOf<Int>()  
                        val unitsArray = atkObj.optJSONArray("atk_units")  
                        if (unitsArray != null) {  
                            for (k in 0 until unitsArray.length()) {  
                                atkUnits.add(unitsArray.getInt(k))  
                            }  
                        }  
                        attacks.add(  
                            ArenaResult(  
                                atkUnits = atkUnits,  
                                upVote = atkObj.optInt("up", 0),  
                                downVote = atkObj.optInt("down", 0),  
                                score = atkObj.optDouble("score", 0.0),  
                                updated = atkObj.optString("updated", "")  
                            )  
                        )  
                    }  
                }  
                results.add(TeamResult(defIndex, defIds, attacks))  
            }  
        }  
  
        return ServerArenaResponse(  
			code = code,  
			message = message,  
			teamCount = teamCount,  
			defenseTeams = defenseTeams,  
			results = results,  
			image = image,  
			highlightImage = highlightImage,   // ★ 新增  
			compareImage = compareImage        // ★ 新增  
		)  
    }  
}