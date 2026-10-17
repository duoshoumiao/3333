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
	private const val ROSTER_NAME_FILE = "chara_roster_names.json"
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
     * 获取 baseId -> 名字列表 的映射（用于搜索）  
     * 优先本地缓存，否则拉网络解析  
     */  
    fun fetchNameMap(  
        client: OkHttpClient,  
        context: Context,  
        forceRefresh: Boolean = false  
    ): Map<Int, List<String>>? {  
        if (!forceRefresh) {  
            loadNameCache(context)?.let { return it }  
        }  
        return try {  
            val request = Request.Builder().url(ROSTER_URL).build()  
            client.newCall(request).execute().use { resp ->  
                if (!resp.isSuccessful) return loadNameCache(context, ignoreExpiry = true)  
                val body = resp.body?.string()  
                    ?: return loadNameCache(context, ignoreExpiry = true)  
                val map = parseCharaNameMap(body)  
                if (map.isNotEmpty()) {  
                    saveNameCache(context, map)  
                    map  
                } else loadNameCache(context, ignoreExpiry = true)  
            }  
        } catch (e: Exception) {  
            Log.e(TAG, "花名册名字获取异常: ${e.message}", e)  
            loadNameCache(context, ignoreExpiry = true)  
        }  
    }  
  
    /**  
     * 同步获取已缓存的名字映射（不触发网络），无缓存返回 null  
     */  
    fun getCachedNameMap(context: Context): Map<Int, List<String>>? {  
        return loadNameCache(context, ignoreExpiry = true)  
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
     * 解析 CHARA_NAME 字典为 baseId -> 名字列表  
     * 形如:  1001: ['日和', 'ヒヨリ', 'hiyori'],  
     */  
    internal fun parseCharaNameMap(text: String): Map<Int, List<String>> {  
        val result = LinkedHashMap<Int, List<String>>()  
        val start = text.indexOf("CHARA_NAME")  
        if (start == -1) return result  
        val braceStart = text.indexOf('{', start)  
        if (braceStart == -1) return result  
        var depth = 0  
        var end = text.length  
        loop@ for (i in braceStart until text.length) {  
            when (text[i]) {  
                '{' -> depth++  
                '}' -> { depth--; if (depth == 0) { end = i; break@loop } }  
            }  
        }  
        val body = text.substring(braceStart + 1, end)  
        val entryRegex = Regex("""(\d{4,})\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)  
        val strRegex = Regex("""'([^']*)'|"([^"]*)"""")  
        for (m in entryRegex.findAll(body)) {  
            val id = m.groupValues[1].toIntOrNull() ?: continue  
            val names = strRegex.findAll(m.groupValues[2])  
                .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }  
                .filter { it.isNotBlank() }  
                .toList()  
            if (names.isNotEmpty()) result[id] = names  
        }  
        Log.d(TAG, "parseCharaNameMap 解析到 ${result.size} 个角色")  
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
  
    private fun loadNameCache(  
        context: Context,  
        ignoreExpiry: Boolean = false  
    ): Map<Int, List<String>>? {  
        val file = File(context.filesDir, ROSTER_NAME_FILE)  
        if (!file.exists()) return null  
        if (!ignoreExpiry) {  
            val age = System.currentTimeMillis() - file.lastModified()  
            if (age > CACHE_MAX_AGE_MS) return null  
        }  
        return try {  
            val obj = org.json.JSONObject(file.readText())  
            val map = mutableMapOf<Int, List<String>>()  
            obj.keys().forEach { k ->  
                val arr = obj.getJSONArray(k)  
                map[k.toInt()] = List(arr.length()) { arr.getString(it) }  
            }  
            map  
        } catch (e: Exception) {  
            null  
        }  
    }  
  
    private fun saveNameCache(context: Context, map: Map<Int, List<String>>) {  
        val file = File(context.filesDir, ROSTER_NAME_FILE)  
        try {  
            val obj = org.json.JSONObject()  
            map.forEach { (k, v) ->  
                val arr = JSONArray()  
                v.forEach { arr.put(it) }  
                obj.put(k.toString(), arr)  
            }  
            file.writeText(obj.toString())  
        } catch (e: Exception) {  
            Log.w(TAG, "名字缓存写入失败: ${e.message}")  
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