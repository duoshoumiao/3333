package com.pcrjjc.app.util.arena

import android.util.Log
import com.pcrjjc.app.util.PcrdApiSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 直连 pcrdfans，不再经过自建服务器。
 * 签名移植自 pcrdapi 项目（com.pcrjjc.app.util.PcrdApiSigner）。
 */
object ArenaPcrdfansClient {

    private const val TAG = "ArenaPcrdfans"
    private const val URL = "https://api.pcrdfans.com/x/v1/search"

    data class Attack(
        val ids: List<Int>,      // 6 位 unitId
        val up: Int,
        val down: Int,
        val updated: String
    )

    sealed class Result {
        data class Ok(val attacks: List<Attack>) : Result()
        data class Failed(val code: Int, val message: String) : Result()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private const val NONCE_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz"

    private fun nonce(): String {
        val sb = StringBuilder(16)
        repeat(16) { sb.append(NONCE_CHARS[Random.nextInt(NONCE_CHARS.length)]) }
        return sb.toString()
    }

    /**
     * @param def4 4 位角色 ID 列表（5 个，或 4 个用于近似查询）
     */
    suspend fun search(def4: List<Int>, region: Int = 2, sort: Int = 1): Result =
        withContext(Dispatchers.IO) {
            if (def4.isEmpty()) return@withContext Result.Failed(-1, "防守队伍为空")
            val def6 = def4.map { it * 100 + 1 }
            val nonce = nonce()
            val ts = System.currentTimeMillis() / 1000

            // 必须手工拼 JSON：签名覆盖的是这段原样字符串，key 顺序不能变
            val signed = buildString {
                append("{\"def\":[")
                append(def6.joinToString(","))
                append("],\"language\":0,\"nonce\":\"")
                append(nonce)
                append("\",\"page\":1,\"region\":")
                append(region)
                append(",\"sort\":")
                append(sort)
                append(",\"ts\":")
                append(ts)
                append('}')
            }

            val sign = try {
                PcrdApiSigner.sign(signed, nonce)
            } catch (e: Exception) {
                Log.e(TAG, "sign failed", e)
                return@withContext Result.Failed(-1, "签名失败: ${e.message}")
            }
            val body = signed.substring(0, signed.length - 1) +
                ",\"_sign\":\"" + sign + "\"}"

            val request = Request.Builder()
                .url(URL)
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 " +
                    "Edg/126.0.0.0")
                .header("Referer", "https://pcrdfans.com/")
                .header("Origin", "https://pcrdfans.com")
                .header("Accept", "*/*")
                .header("Authorization", "")
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.Failed(resp.code, "HTTP ${resp.code}")
                    }
                    val json = JSONObject(text)
                    val code = json.optInt("code", -1)
                    if (code != 0) {
                        val msg = when (code) {
                            601 -> "查询次数已达上限，请稍后再试"
                            110 -> "队伍参数不合法"
                            else -> "服务器返回 $code"
                        }
                        return@withContext Result.Failed(code, msg)
                    }
                    val result = json.optJSONObject("data")?.optJSONArray("result")
                        ?: return@withContext Result.Ok(emptyList())
                    val out = ArrayList<Attack>(result.length())
                    for (i in 0 until result.length()) {
                        val item = result.optJSONObject(i) ?: continue
                        val atk = item.optJSONArray("atk") ?: continue
                        val ids = ArrayList<Int>(atk.length())
                        for (j in 0 until atk.length()) {
                            val u = atk.optJSONObject(j) ?: continue
                            ids.add(u.optInt("id", 0))
                        }
                        if (ids.size < 5) continue
                        out.add(
                            Attack(
                                ids = ids,
                                up = item.optInt("up", 0),
                                down = item.optInt("down", 0),
                                updated = item.optString("updated", "")
                            )
                        )
                    }
                    Result.Ok(out)
                }
            } catch (e: Exception) {
                Log.e(TAG, "search failed", e)
                Result.Failed(-1, "网络异常: ${e.message}")
            }
        }
}
