package com.pcrjjc.app.util.arena

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 查询结果缓存，对应 arena/buffer/ 的机制：
 * key = 排序后的 5 个角色 ID 拼接 + region，5 天内命中直接复用。
 *
 * 缓存既省配额，也是 601 限流时的降级来源。
 */
class ArenaSolutionCache(context: Context) {

    companion object {
        private const val TAG = "ArenaCache"
        private const val TTL_MS = 5L * 24 * 60 * 60 * 1000
        private const val FILE = "arena_solutions.json"
    }

    private val file = File(context.filesDir, FILE)
    private val lock = Any()
    private var loaded = false
    private val entries = HashMap<String, Entry>()

    private data class Entry(val timestamp: Long, val attacks: List<ArenaPcrdfansClient.Attack>)

    fun key(def4: List<Int>, region: Int): String =
        def4.sorted().joinToString("") + region

    fun get(def4: List<Int>, region: Int): List<ArenaPcrdfansClient.Attack>? {
        ensureLoaded()
        val k = key(def4, region)
        synchronized(lock) {
            val e = entries[k] ?: return null
            if (System.currentTimeMillis() - e.timestamp > TTL_MS) return null
            return e.attacks
        }
    }

    fun put(def4: List<Int>, region: Int, attacks: List<ArenaPcrdfansClient.Attack>) {
        if (attacks.isEmpty()) return
        ensureLoaded()
        val k = key(def4, region)
        synchronized(lock) {
            entries[k] = Entry(System.currentTimeMillis(), attacks)
        }
        persist()
    }

    /** 近似解检索：找出与目标队伍有 >=4 个相同角色的历史缓存 */
    fun approximate(def4: List<Int>, region: Int): List<ArenaPcrdfansClient.Attack> {
        ensureLoaded()
        val target = def4.toSet()
        val out = ArrayList<ArenaPcrdfansClient.Attack>()
        synchronized(lock) {
            for ((k, e) in entries) {
                if (System.currentTimeMillis() - e.timestamp > TTL_MS) continue
                if (!k.endsWith(region.toString())) continue
                val ids = keyToIds(k)
                if (ids.size != 5) continue
                if (ids.toSet().intersect(target).size >= 4) out.addAll(e.attacks)
            }
        }
        return out
    }

    private fun keyToIds(k: String): List<Int> {
        if (k.length < 6) return emptyList()
        val body = k.dropLast(1)
        if (body.length % 4 != 0) return emptyList()
        return (0 until body.length step 4).mapNotNull { body.substring(it, it + 4).toIntOrNull() }
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            try {
                if (file.exists()) {
                    val root = JSONObject(file.readText())
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val o = root.optJSONObject(k) ?: continue
                        val ts = o.optLong("ts", 0L)
                        val arr = o.optJSONArray("list") ?: continue
                        val list = ArrayList<ArenaPcrdfansClient.Attack>(arr.length())
                        for (i in 0 until arr.length()) {
                            val a = arr.optJSONObject(i) ?: continue
                            val idsArr = a.optJSONArray("ids") ?: continue
                            val ids = ArrayList<Int>(idsArr.length())
                            for (j in 0 until idsArr.length()) ids.add(idsArr.optInt(j))
                            list.add(
                                ArenaPcrdfansClient.Attack(
                                    ids = ids,
                                    up = a.optInt("up", 0),
                                    down = a.optInt("down", 0),
                                    updated = a.optString("upd", "")
                                )
                            )
                        }
                        entries[k] = Entry(ts, list)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "load cache failed", e)
            }
            loaded = true
            Log.i(TAG, "cache entries=${entries.size}")
        }
    }

    private fun persist() {
        try {
            val root = JSONObject()
            synchronized(lock) {
                for ((k, e) in entries) {
                    val o = JSONObject()
                    o.put("ts", e.timestamp)
                    val arr = JSONArray()
                    for (a in e.attacks) {
                        val ao = JSONObject()
                        ao.put("ids", JSONArray(a.ids))
                        ao.put("up", a.up)
                        ao.put("down", a.down)
                        ao.put("upd", a.updated)
                        arr.put(ao)
                    }
                    o.put("list", arr)
                    root.put(k, o)
                }
            }
            file.writeText(root.toString())
        } catch (e: Exception) {
            Log.w(TAG, "persist cache failed", e)
        }
    }
}
