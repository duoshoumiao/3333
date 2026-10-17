package com.pcrjjc.app.util

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

/**
 * 角色档案（CHARA_PROFILE）与别称（CHARA_NAME）的本地数据源。
 *
 * 数据来自 assets/chara_data.json（由 priconne/_pcr_data.py 生成），
 * 包含全部角色别称和已有角色的档案字段。
 */
data class CharaProfile(
    val id: Int,
    val names: List<String>,
    val fields: List<Pair<String, String>>
) {
    /**
     * 按 Hoshino whois 插件的顺序输出档案：
     * 先固定展示基础字段，再按原顺序展示额外字段（排除“名字”）。
     */
    val displayFields: List<Pair<String, String>>
        get() {
            if (fields.isEmpty()) return emptyList()
            val map = LinkedHashMap<String, String>(fields.size)
            fields.forEach { (key, value) -> map[key] = value }
            val base = BASE_FIELDS.mapNotNull { key ->
                map[key]?.let { value -> key to value }
            }
            val extra = fields.filter { (key, _) ->
                key != "名字" && key !in BASE_FIELDS
            }
            return base + extra
        }

    companion object {
        val BASE_FIELDS = listOf(
            "公会", "生日", "年龄", "身高", "体重",
            "血型", "种族", "喜好", "声优"
        )
    }
}

data class CharaSearchResult(
    val matches: List<CharaProfile>,
    val exactMatch: Boolean
)

object CharaProfileStore {

    private const val TAG = "CharaProfileStore"
    private const val ASSET_FILE = "chara_data.json"
    private const val MAX_RESULTS = 8
    private const val FUZZY_THRESHOLD = 0.5f

    @Volatile
    private var loaded = false

    private var all: List<CharaProfile> = emptyList()
    private var byId: Map<Int, CharaProfile> = emptyMap()

    /**
     * 从 assets 加载一次。重复调用是安全的。
     */
    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        try {
            val json = context.assets
                .open(ASSET_FILE)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val root = JSONObject(json)
            val namesObj = root.optJSONObject("names") ?: JSONObject()
            val profilesObj = root.optJSONObject("profiles") ?: JSONObject()

            val ids = sortedSetOf<Int>()
            namesObj.keys().forEach { key -> key.toIntOrNull()?.let { ids.add(it) } }
            profilesObj.keys().forEach { key -> key.toIntOrNull()?.let { ids.add(it) } }

            val list = ArrayList<CharaProfile>(ids.size)
            ids.forEach { id ->
                val nameArray = namesObj.optJSONArray(id.toString())
                val names = ArrayList<String>(nameArray?.length() ?: 0)
                if (nameArray != null) {
                    for (i in 0 until nameArray.length()) {
                        val name = nameArray.optString(i)
                        if (name.isNotBlank()) names.add(name)
                    }
                }

                val fieldArray = profilesObj.optJSONArray(id.toString())
                val fields = ArrayList<Pair<String, String>>(fieldArray?.length() ?: 0)
                if (fieldArray != null) {
                    for (i in 0 until fieldArray.length()) {
                        val pair = fieldArray.optJSONArray(i) ?: continue
                        if (pair.length() >= 2) {
                            val key = pair.optString(0)
                            val value = pair.optString(1)
                            if (key.isNotBlank()) fields.add(key to value)
                        }
                    }
                }

                if (names.isNotEmpty()) {
                    list.add(CharaProfile(id, names, fields))
                }
            }

            all = list
            byId = list.associateBy { it.id }
            loaded = true
            Log.i(
                TAG,
                "loaded ${all.size} characters, " +
                    "${all.count { it.fields.isNotEmpty() }} with profiles"
            )
        } catch (e: Exception) {
            Log.e(TAG, "load $ASSET_FILE failed", e)
        }
    }

    fun get(id: Int): CharaProfile? = byId[id]

    /**
     * 查询顺序与 whois 插件一致：
     * 1. 纯数字按 ID 精确查询；
     * 2. 别称精确匹配；
     * 3. 前缀匹配；
     * 4. 包含匹配；
     * 5. 相似度匹配（容忍少量错字）。
     */
    fun search(query: String, limit: Int = MAX_RESULTS): CharaSearchResult {
        val raw = query.trim()
        if (raw.isEmpty()) return CharaSearchResult(emptyList(), false)

        val q = normalize(raw)
        if (q.isEmpty()) return CharaSearchResult(emptyList(), false)

        // 兼容全角数字 / 前后空格
        q.toIntOrNull()?.let { id ->
            byId[id]?.let { return CharaSearchResult(listOf(it), true) }
        }

        val exact = all.filter { profile ->
            profile.names.any { normalize(it) == q }
        }
        if (exact.isNotEmpty()) {
            return CharaSearchResult(exact.take(limit), true)
        }

        val prefix = all.filter { profile ->
            profile.names.any { normalize(it).startsWith(q) }
        }
        if (prefix.isNotEmpty()) {
            return CharaSearchResult(prefix.take(limit), false)
        }

        val contains = all.filter { profile ->
            profile.names.any { normalize(it).contains(q) }
        }
        if (contains.isNotEmpty()) {
            return CharaSearchResult(contains.take(limit), false)
        }

        val fuzzy = all.mapNotNull { profile ->
            var best = 0f
            profile.names.forEach { name ->
                best = max(best, similarity(q, normalize(name)))
            }
            if (best >= FUZZY_THRESHOLD) profile to best else null
        }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)

        return CharaSearchResult(fuzzy, false)
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(" ", "")
    }

    private fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.isEmpty() || b.isEmpty()) return 0f
        val distance = levenshtein(a, b)
        return 1f - distance.toFloat() / max(a.length, b.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
