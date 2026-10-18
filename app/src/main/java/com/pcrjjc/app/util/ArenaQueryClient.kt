package com.pcrjjc.app.util

import android.content.Context
import android.util.Log
import com.pcrjjc.app.util.arena.ArenaLocalEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 「怎么拆」查询入口。
 *
 * 默认走**端侧本地实现**（识别 + 直连 pcrdfans + 出图），不再需要自建的 arena_api 服务。
 * 只有在本地引擎异常、且配置了服务器地址时，才降级到旧的服务端接口。
 */
class ArenaQueryClient(
    private val context: Context,
    private val serverUrl: String? = null,
    private val preferLocal: Boolean = true
) {

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
        val image: String?,
        val highlightImage: String? = null,
        val compareImage: String? = null
    )

    private val local: ArenaLocalEngine by lazy { ArenaLocalEngine(context) }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    // ======================== 截图识别 ========================

    suspend fun queryByImage(imageBytes: ByteArray, region: Int = 2): ServerArenaResponse {
        if (preferLocal) {
            try {
                return local.queryByImage(imageBytes, region)
            } catch (e: Exception) {
                Log.e(TAG, "local queryByImage failed, try server", e)
                if (serverUrl.isNullOrBlank()) {
                    return failure("本地识别失败: ${e.message}")
                }
            }
        }
        return remoteImage(imageBytes, region)
    }

    // ======================== 角色名文本 ========================

    suspend fun queryByText(names: List<String>, region: Int = 2): ServerArenaResponse {
        if (preferLocal) {
            try {
                return local.queryByText(names, region)
            } catch (e: Exception) {
                Log.e(TAG, "local queryByText failed, try server", e)
                if (serverUrl.isNullOrBlank()) {
                    return failure("本地查询失败: ${e.message}")
                }
            }
        }
        return remoteText(names, region)
    }

    // ======================== 点选组队 ========================

    suspend fun queryByIds(ids: List<Int>, region: Int = 2): ServerArenaResponse {
        if (preferLocal) {
            try {
                return local.queryByIds(ids, region)
            } catch (e: Exception) {
                Log.e(TAG, "local queryByIds failed, try server", e)
                if (serverUrl.isNullOrBlank()) {
                    return failure("本地查询失败: ${e.message}")
                }
            }
        }
        return remoteIds(ids, region)
    }

    // ======================== 旧服务端接口（降级用） ========================

    private suspend fun remoteImage(
        imageBytes: ByteArray,
        region: Int
    ): ServerArenaResponse = withContext(Dispatchers.IO) {
        val url = serverUrl
        if (url.isNullOrBlank()) {
            return@withContext failure("未配置服务器地址，且本地识别不可用")
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

            Log.i(TAG, "fallback to server: $url, size=${imageBytes.size}, region=$region")
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext failure("服务器请求失败: ${resp.code}")
                }
                val text = resp.body?.string() ?: return@withContext failure("空响应")
                parseServerResponse(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "remote image failed", e)
            failure("请求异常: ${e.message}")
        }
    }

    private suspend fun remoteText(
        names: List<String>,
        region: Int
    ): ServerArenaResponse = withContext(Dispatchers.IO) {
        val url = serverUrl ?: return@withContext failure("未配置服务器地址")
        try {
            val jsonBody = JSONObject()
                .put("names", org.json.JSONArray(names))
                .put("region", region)
                .toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$url/api/arena/query_text")
                .post(jsonBody)
                .build()

            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string() ?: return@withContext failure("空响应")
                parseServerResponse(text)
            }
        } catch (e: Exception) {
            failure("请求异常: ${e.message}")
        }
    }

    private suspend fun remoteIds(
        ids: List<Int>,
        region: Int
    ): ServerArenaResponse = withContext(Dispatchers.IO) {
        val url = serverUrl ?: return@withContext failure("未配置服务器地址")
        try {
            val jsonBody = JSONObject()
                .put("ids", org.json.JSONArray(ids))
                .put("region", region)
                .toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$url/api/arena/query_text")
                .post(jsonBody)
                .build()

            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string() ?: return@withContext failure("空响应")
                parseServerResponse(text)
            }
        } catch (e: Exception) {
            failure("请求异常: ${e.message}")
        }
    }

    private fun failure(msg: String): ServerArenaResponse =
        ServerArenaResponse(
            code = -1,
            message = msg,
            teamCount = 0,
            defenseTeams = emptyList(),
            results = emptyList(),
            image = null
        )

    private fun parseServerResponse(responseBody: String): ServerArenaResponse {
        val json = JSONObject(responseBody)
        val code = json.optInt("code", -1)
        val message = json.optString("message", "")
        val image = json.optString("image", null)?.takeIf { it.isNotBlank() }
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
            highlightImage = highlightImage,
            compareImage = compareImage
        )
    }
}
