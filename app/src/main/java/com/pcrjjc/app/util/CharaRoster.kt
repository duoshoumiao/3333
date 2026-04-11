package com.pcrjjc.app.util  
  
import android.content.Context  
import android.util.Log  
import okhttp3.OkHttpClient  
import okhttp3.Request  
import org.json.JSONArray  
import java.io.File  
  
/**  
 * 花名册工具类  
 * 从 LandosolRoster 获取真实角色 ID 列表，替代硬编码 1001..1899  
 * 缓存在本地 {filesDir}/chara_roster.json  
 */  
object CharaRoster {  
  
    private const val TAG = "CharaRoster"  
    private const val ROSTER_FILE = "chara_roster.json"  
    private const val ROSTER_URL =  
        "https://raw.githubusercontent.com/Ice9Coffee/LandosolRoster/master/_pcr_data.py"  
    private const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L // 24 小时  
  
    /**  
     * 获取花名册角色 ID 列表  
     * 优先使用未过期的本地缓存，否则从网络拉取  
     * @param client OkHttpClient 实例  
     * @param context Android Context  
     * @param forceRefresh 是否强制刷新（忽略缓存）  
     * @return 有效角色 ID 列表，失败时返回 null  
     */  
    fun fetchRoster(  
        client: OkHttpClient,  
        context: Context,  
        forceRefresh: Boolean = false  
    ): List<Int>? {  
        // 尝试读取本地缓存  
        if (!forceRefresh) {  
            val cached = loadCache(context)  
            if (cached != null) {  
                Log.d(TAG, "使用本地缓存花名册，共 ${cached.size} 个角色")  
                return cached  
            }  
        }  
  
        // 从网络拉取  
        return try {  
            val request = Request.Builder().url(ROSTER_URL).build()  
            val response = client.newCall(request).execute()  
            response.use { resp ->  
                if (!resp.isSuccessful) {  
                    Log.w(TAG, "花名册下载失败: HTTP ${resp.code}")  
                    // 网络失败时尝试返回过期缓存  
                    return loadCache(context, ignoreExpiry = true)  
                }  
                val body = resp.body?.string() ?: return loadCache(context, ignoreExpiry = true)  
                val ids = parsePcrData(body)  
                if (ids.isNotEmpty()) {  
                    saveCache(context, ids)  
                    Log.d(TAG, "花名册更新成功，共 ${ids.size} 个角色")  
                    ids  
                } else {  
                    Log.w(TAG, "花名册解析结果为空")  
                    loadCache(context, ignoreExpiry = true)  
                }  
            }  
        } catch (e: Exception) {  
            Log.e(TAG, "花名册获取异常: ${e.message}", e)  
            loadCache(context, ignoreExpiry = true)  
        }  
    }  
  
    /**  
     * 获取本地缓存的角色数量（不触发网络请求）  
     */  
    fun getCachedRosterCount(context: Context): Int {  
        val file = File(context.filesDir, ROSTER_FILE)  
        if (!file.exists()) return 0  
        return try {  
            val json = file.readText()  
            JSONArray(json).length()  
        } catch (e: Exception) {  
            0  
        }  
    }  
  
    /**  
     * 解析 _pcr_data.py 文本，提取有效角色 ID  
     * 有效 ID = CHARA_NAME 的 keys - UnavailableChara  
     */  
    internal fun parsePcrData(text: String): List<Int> {  
        val unavailable = parseUnavailableChara(text)  
        val charaIds = parseCharaNameKeys(text)  
        return charaIds.filter { it !in unavailable }.sorted()  
    }  
  
    /**  
     * 解析 UnavailableChara = { 1000, 1072, ... }  
     */  
    private fun parseUnavailableChara(text: String): Set<Int> {  
        val result = mutableSetOf<Int>()  
        // 匹配 UnavailableChara = { ... } 块（可能跨多行）  
        val blockRegex = Regex("""UnavailableChara\s*=\s*\{([^}]*)\}""", RegexOption.DOT_MATCHES_ALL)  
        val match = blockRegex.find(text) ?: return result  
        val block = match.groupValues[1]  
        // 提取所有数字  
        val numRegex = Regex("""\d+""")  
        numRegex.findAll(block).forEach { m ->  
            m.value.toIntOrNull()?.let { result.add(it) }  
        }  
        return result  
    }  
  
    /**  
     * 解析 CHARA_NAME 字典中的 int key  
     * 匹配形如:  1001: ['xxx', ...] 或 1001:['xxx', ...]  
     */  
    private fun parseCharaNameKeys(text: String): List<Int> {  
        val result = mutableListOf<Int>()  
        // 找到 CHARA_NAME = { 之后的内容  
        val startIdx = text.indexOf("CHARA_NAME")  
        if (startIdx == -1) return result  
        val sub = text.substring(startIdx)  
  
        // 逐行匹配 key  
        val keyRegex = Regex("""^\s*(\d{4,})\s*:\s*\[""")  
        for (line in sub.lines()) {  
            val m = keyRegex.find(line)  
            if (m != null) {  
                m.groupValues[1].toIntOrNull()?.let { result.add(it) }  
            }  
        }  
        return result  
    }  
  
    /**  
     * 从本地缓存加载花名册  
     * @param ignoreExpiry 是否忽略过期时间（fallback 场景）  
     */  
    private fun loadCache(context: Context, ignoreExpiry: Boolean = false): List<Int>? {  
        val file = File(context.filesDir, ROSTER_FILE)  
        if (!file.exists()) return null  
        if (!ignoreExpiry) {  
            val age = System.currentTimeMillis() - file.lastModified()  
            if (age > CACHE_MAX_AGE_MS) return null  
        }  
        return try {  
            val json = file.readText()  
            val arr = JSONArray(json)  
            List(arr.length()) { i -> arr.getInt(i) }  
        } catch (e: Exception) {  
            Log.w(TAG, "缓存读取失败: ${e.message}")  
            null  
        }  
    }  
  
    /**  
     * 保存花名册到本地缓存  
     */  
    private fun saveCache(context: Context, ids: List<Int>) {  
        val file = File(context.filesDir, ROSTER_FILE)  
        try {  
            val arr = JSONArray()  
            ids.forEach { arr.put(it) }  
            file.writeText(arr.toString())  
        } catch (e: Exception) {  
            Log.w(TAG, "缓存写入失败: ${e.message}")  
        }  
    }  
}