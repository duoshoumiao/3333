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
 * 将截图发送到服务器，由服务器完成图像识别 + 作业查询 + 无冲配队。  
 */  
class ArenaQueryClient {  
  
    companion object {  
        private const val TAG = "ArenaQueryClient"  
        private const val SERVER_URL = "http://119.91.249.245:8020"  
    }  
  
    /** 单条进攻阵容推荐 */  
    data class ArenaResult(  
        val atkUnits: List<Int>,   // 进攻角色 baseId 列表  
        val upVote: Int,  
        val downVote: Int,  
        val score: Double,  
        val updated: String,  
        val teamType: String = "normal"  
    )  
  
    /** 单队的查询结果（防守队伍 + 对应的进攻推荐列表） */  
    data class TeamResult(  
        val defenseIndex: Int,  
        val defenseIds: List<Int>,  
        val attacks: List<ArenaResult>  
    )  
  
    /** 无冲配队中的一个进攻队伍条目 */  
    data class CollisionFreeEntry(  
        val defenseIndex: Int,  
        val atkUnits: List<Int>,  
        val upVote: Int,  
        val downVote: Int,  
        val score: Double,  
        val teamType: String,  
        val updated: String  
    )  
  
    /** 一组无冲配队方案（每个防守队伍对应一个进攻队伍，角色互不重复） */  
    data class CollisionFreeSet(  
        val teams: List<CollisionFreeEntry>  
    )  
  
    /** 服务器完整响应 */  
    data class ServerArenaResponse(  
        val code: Int,  
        val message: String,  
        val teamCount: Int,                          // 1=JJC, 2-3=PJJC  
        val defenseTeams: List<List<Int>>,            // 识别到的防守队伍  
        val results: List<TeamResult>,                // 逐队独立查询结果  
        val collisionFreeSets: List<CollisionFreeSet> // PJJC 无冲配队方案  
    )  
  
    private val client = OkHttpClient.Builder()  
        .connectTimeout(120, TimeUnit.SECONDS)  
        .readTimeout(180, TimeUnit.SECONDS)  
        .build()  
  
    /**  
     * 将截图发送到服务器进行识别 + 查询。  
     *  
     * @param imageBytes 截图的 PNG 字节数组  
     * @param region 服务器区域: 1=全服, 2=B服, 3=台服, 4=日服  
     * @return ServerArenaResponse 包含识别到的防守阵容、进攻推荐、无冲配队  
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
                    return emptyResponse("服务器请求失败: ${resp.code}")  
                }  
  
                val body = resp.body?.string()  
                if (body.isNullOrEmpty()) {  
                    return emptyResponse("服务器返回空数据")  
                }  
  
                Log.i(TAG, "服务器返回: ${body.take(500)}")  
                return parseServerResponse(body)  
            }  
        } catch (e: Exception) {  
            Log.e(TAG, "queryByImage 失败", e)  
            return emptyResponse("网络错误: ${e.message}")  
        }  
    }  
  
    private fun emptyResponse(message: String): ServerArenaResponse {  
        return ServerArenaResponse(-1, message, 0, emptyList(), emptyList(), emptyList())  
    }  
  
    private fun parseServerResponse(body: String): ServerArenaResponse {  
        val json = JSONObject(body)  
        val code = json.optInt("code", -1)  
        val message = json.optString("message", "")  
        val teamCount = json.optInt("team_count", 0)  
  
        if (code != 0) {  
            return ServerArenaResponse(code, message, teamCount, emptyList(), emptyList(), emptyList())  
        }  
  
        // ===== 解析 defense =====  
        val defenseArray = json.optJSONArray("defense") ?: org.json.JSONArray()  
        val defenseTeams = mutableListOf<List<Int>>()  
        for (i in 0 until defenseArray.length()) {  
            val team = defenseArray.getJSONArray(i)  
            defenseTeams.add((0 until team.length()).map { team.getInt(it) })  
        }  
  
        // ===== 解析 results（逐队独立结果） =====  
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
  
        // ===== 解析 collision_free_sets（PJJC 无冲配队） =====  
        val cfsArray = json.optJSONArray("collision_free_sets") ?: org.json.JSONArray()  
        val collisionFreeSets = mutableListOf<CollisionFreeSet>()  
        for (i in 0 until cfsArray.length()) {  
            val setObj = cfsArray.getJSONObject(i)  
            val teamsArr = setObj.optJSONArray("teams") ?: org.json.JSONArray()  
            val entries = mutableListOf<CollisionFreeEntry>()  
            for (j in 0 until teamsArr.length()) {  
                val t = teamsArr.getJSONObject(j)  
                val atkUnits = t.optJSONArray("atk_units")?.let { arr ->  
                    (0 until arr.length()).map { arr.getInt(it) }  
                } ?: emptyList()  
                entries.add(  
                    CollisionFreeEntry(  
                        defenseIndex = t.optInt("defense_index", 0),  
                        atkUnits = atkUnits,  
                        upVote = t.optInt("up", 0),  
                        downVote = t.optInt("down", 0),  
                        score = t.optDouble("score", 0.0),  
                        teamType = t.optString("team_type", "normal"),  
                        updated = t.optString("updated", "")  
                    )  
                )  
            }  
            collisionFreeSets.add(CollisionFreeSet(entries))  
        }  
  
        return ServerArenaResponse(  
            code = code,  
            message = message,  
            teamCount = teamCount,  
            defenseTeams = defenseTeams,  
            results = results,  
            collisionFreeSets = collisionFreeSets  
        )  
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