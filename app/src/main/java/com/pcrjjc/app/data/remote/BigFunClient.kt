package com.pcrjjc.app.data.remote  
  
import okhttp3.OkHttpClient  
import okhttp3.Request  
import okhttp3.HttpUrl.Companion.toHttpUrl  
import org.json.JSONObject  
import java.security.MessageDigest  
import java.util.UUID  
import java.util.concurrent.TimeUnit  
  
object BigFunClient {  
  
    private const val APPKEY = "f07288b7ef7645c7a3997baf3d208b62"  
    private const val APPSECRET = "mNnGiylYAFXbY0gPy4Zw2nG+dz1t6TYHENz61fxR3Ic="  
  
    private const val BOSS_API = "https://api.game.bilibili.com/game/player/tools/pcr/boss_daily_report"  
    private const val OVERVIEW_API = "https://api.game.bilibili.com/game/player/tools/pcr/clan_daily_report"  
    private const val MEMBER_API = "https://api.game.bilibili.com/game/player/tools/pcr/clan_daily_report_by_time"  
  
    private val httpClient = OkHttpClient.Builder()  
        .connectTimeout(20, TimeUnit.SECONDS)  
        .readTimeout(20, TimeUnit.SECONDS)  
        .build()  
  
    private fun makeSign(params: Map<String, String>): String {  
        val sortedStr = params.keys.sorted().joinToString("&") { "$it=${params[it]}" }  
        val raw = "$sortedStr&secret=$APPSECRET"  
        val md5 = MessageDigest.getInstance("MD5")  
        return md5.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }  
    }  
  
    private fun buildParams(extra: Map<String, String> = emptyMap()): Map<String, String> {  
        val params = extra.toMutableMap()  
        params["ts"] = System.currentTimeMillis().toString()  
        params["nonce"] = UUID.randomUUID().toString()  
        params["appkey"] = APPKEY  
        params["sign"] = makeSign(params)  
        return params  
    }  
  
    private fun request(url: String, cookie: Map<String, String>, extra: Map<String, String> = emptyMap()): JSONObject {  
        val params = buildParams(extra)  
        val urlBuilder = url.toHttpUrl().newBuilder()  
        params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }  
  
        val cookieStr = cookie.entries.joinToString("; ") { "${it.key}=${it.value}" }  
        val request = Request.Builder()  
            .url(urlBuilder.build())  
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")  
            .header("Referer", "https://game.bilibili.com/tool/pcr/")  
            .header("Origin", "https://game.bilibili.com")  
            .header("Cookie", cookieStr)  
            .get()  
            .build()  
  
        val response = httpClient.newCall(request).execute()  
        if (response.code != 200) {  
            throw Exception("HTTP ${response.code}，响应: ${response.body?.string()?.take(200)}")  
        }  
        val body = response.body?.string() ?: throw Exception("响应体为空")  
        val json = JSONObject(body)  
        if (json.optInt("code", -1) != 0) {  
            throw Exception("API错误 code=${json.optInt("code")}: ${json.optString("message", "未知")}")  
        }  
        return json  
    }  
  
    fun getRecord(cookie: Map<String, String>): List<JSONObject> {  
        val overview = request(OVERVIEW_API, cookie)  
        val dayList = overview.optJSONObject("data")?.optJSONArray("day_list")  
            ?: throw Exception("日期列表为空，请检查当前是否在会战期间")  
        if (dayList.length() == 0) throw Exception("日期列表为空，请检查当前是否在会战期间")  
  
        val allRecords = mutableListOf<JSONObject>()  
        for (i in 0 until dayList.length()) {  
            val date = dayList.getString(i)  
            try {  
                val data = request(MEMBER_API, cookie, mapOf("date" to date, "page" to "1", "size" to "30"))  
                val list = data.optJSONObject("data")?.optJSONArray("list")  
                if (list != null) {  
                    for (j in 0 until list.length()) {  
                        allRecords.add(list.getJSONObject(j))  
                    }  
                }  
            } catch (_: Exception) { }  
        }  
        return allRecords  
    }  
  
    fun getBossInfo(cookie: Map<String, String>): Map<String, Int> {  
        val data = request(BOSS_API, cookie)  
        val bossList = data.optJSONObject("data")?.optJSONArray("boss_list")  
            ?: throw Exception("BOSS列表为空，请检查团队战工具是否有数据")  
        if (bossList.length() == 0) throw Exception("BOSS列表为空，请检查团队战工具是否有数据")  
  
        val result = mutableMapOf<String, Int>()  
        for (i in 0 until minOf(5, bossList.length())) {  
            val boss = bossList.getJSONObject(i)  
            result[boss.getString("boss_name")] = i + 1  
        }  
        return result  
    }  
}