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
 * 移植自 arena.py + pcrdapi，调用 pcrdfans.com API 查询进攻阵容推荐。    
 */    
class ArenaQueryClient {    
    
    companion object {    
        private const val TAG = "ArenaQueryClient"    
        private const val API_URL = "https://api.pcrdfans.com/x/v1/search"    
        private const val NONCE_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz"    
        private const val NONCE_LENGTH = 16    
    }    
    
    data class ArenaResult(    
        val atkUnits: List<Int>,    
        val upVote: Int,    
        val downVote: Int,    
        val score: Double,    
        val updated: String    
    )    
    
    private val client = OkHttpClient.Builder()    
        .connectTimeout(10, TimeUnit.SECONDS)    
        .readTimeout(10, TimeUnit.SECONDS)    
        .build()    
    
    private fun getNonce(): String {    
        return (1..NONCE_LENGTH).map { NONCE_CHARS[Random.nextInt(NONCE_CHARS.length)] }.joinToString("")    
    }    
    
    private fun getTs(): Long {    
        return System.currentTimeMillis() / 1000    
    }    
    
    private fun buildJsonString(    
        defArray: List<Int>,    
        language: Int,    
        nonce: String,    
        page: Int,    
        region: Int,    
        sort: Int,    
        ts: Long,    
        sign: String? = null    
    ): String {    
        val sb = StringBuilder()    
        sb.append("{")    
        sb.append("\"def\":[")    
        sb.append(defArray.joinToString(","))    
        sb.append("],")    
        sb.append("\"language\":$language,")    
        sb.append("\"nonce\":\"$nonce\",")    
        sb.append("\"page\":$page,")    
        sb.append("\"region\":$region,")    
        sb.append("\"sort\":$sort,")    
        sb.append("\"ts\":$ts")    
        if (sign != null) {    
            sb.append(",\"_sign\":\"$sign\"")    
        }    
        sb.append("}")    
        return sb.toString()    
    }    
    
    fun query(defenseIds: List<Int>, region: Int = 2, sort: Int = 1): List<ArenaResult> {    
        if (defenseIds.size < 4 || defenseIds.size > 5) {    
            Log.w(TAG, "防守阵容数量不对: ${defenseIds.size}，需要4~5个")    
            return emptyList()    
        }    
    
        try {    
            val defArray = defenseIds.map { it * 100 + 1 }    
            val nonce = getNonce()    
            val ts = getTs()    
            val page = 1    
            val language = 0    
    
            val jsonForSign = buildJsonString(defArray, language, nonce, page, region, sort, ts)    
            val sign = PcrdApiSigner.sign(jsonForSign, nonce)    
            val finalJson = buildJsonString(defArray, language, nonce, page, region, sort, ts, sign)    
    
            Log.i(TAG, "查询防守阵容: $defenseIds -> def=$defArray, nonce=$nonce")    
            Log.i(TAG, "签名: $sign")    
            Log.i(TAG, "请求体: $finalJson")    
    
            val body = finalJson.toRequestBody("application/json; charset=utf-8".toMediaType())    
    
            val request = Request.Builder()    
                .url(API_URL)    
                .post(body)    
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0")    
                .header("Referer", "https://pcrdfans.com/")    
                .header("Origin", "https://pcrdfans.com")    
                .header("Accept", "*/*")    
                .header("Content-Type", "application/json; charset=utf-8")    
                .header("Authorization", "")    
                .header("Host", "api.pcrdfans.com")    
                .build()    
    
            val response = client.newCall(request).execute()    
            response.use { resp ->    
                if (!resp.isSuccessful) {    
                    Log.e(TAG, "API 请求失败: ${resp.code}")    
                    return emptyList()    
                }    
    
                val responseBody = resp.body?.string() ?: return emptyList()    
                Log.i(TAG, "API 响应: ${responseBody.take(500)}")    
                return parseResponse(responseBody)    
            }    
        } catch (e: Exception) {    
            Log.e(TAG, "查询失败", e)    
            return emptyList()    
        }    
    }    
    
    private fun parseResponse(responseBody: String): List<ArenaResult> {    
        val json = JSONObject(responseBody)    
        val code = json.optInt("code", -1)    
        if (code != 0) {    
            Log.w(TAG, "API 返回错误: code=$code, message=${json.optString("message")}, 完整响应: $responseBody")    
            return emptyList()    
        }    
    
        val data = json.optJSONObject("data") ?: return emptyList()    
        val resultArray = data.optJSONArray("result") ?: return emptyList()    
    
        val results = mutableListOf<ArenaResult>()    
        for (i in 0 until resultArray.length()) {    
            val entry = resultArray.getJSONObject(i)    
    
            val atkArray = entry.optJSONArray("atk") ?: continue    
            val atkUnits = mutableListOf<Int>()    
            for (j in 0 until atkArray.length()) {    
                val unit = atkArray.getJSONObject(j)    
                val unitId = unit.getInt("id")    
                atkUnits.add(unitId / 100)    
            }    
    
            val up = entry.optInt("up", 0)    
            val down = entry.optInt("down", 0)    
            val updated = entry.optString("updated", "")    
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
    
        return results.sortedByDescending { it.score }.take(10)    
    }    
    
    private fun calculateVal(up: Int, down: Int): Double {    
        val total = up + down    
        val val1 = up.toDouble() / (total + 0.0001) * 2.0 - 1.0    
        val val2 = ln(total + 0.01) / ln(100.0)    
        return val1 + val2 + Random.nextDouble() / 1000.0    
    }    
}