package com.pcrjjc.app.util.arena

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * 读取 assets/arena/ 下的识别资产。
 *  - hashes.json：940 个 6 位 unitId -> 256bit 指纹（64 位 hex）
 *  - best_atk_records.json：高频进攻阵容，用于 PJJC 补队
 */
object ArenaAssets {

    private const val TAG = "ArenaAssets"

    @Volatile
    private var hashes: List<Pair<Int, ByteArray>>? = null

    @Volatile
    private var bestAtk: List<List<Int>>? = null

    fun unitHashes(context: Context): List<Pair<Int, ByteArray>> {
        hashes?.let { return it }
        synchronized(this) {
            hashes?.let { return it }
            val list = ArrayList<Pair<Int, ByteArray>>(1024)
            try {
                val text = context.assets.open("arena/hashes.json")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                val obj = JSONObject(text)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val uid = key.toIntOrNull() ?: continue
                    val bytes = hexToBytes(obj.optString(key)) ?: continue
                    if (bytes.size != 32) continue
                    list.add(uid to bytes)
                }
                Log.i(TAG, "loaded ${list.size} unit fingerprints")
            } catch (e: Exception) {
                Log.e(TAG, "load hashes.json failed", e)
            }
            hashes = list
            return list
        }
    }

    fun bestAtkRecords(context: Context): List<List<Int>> {
        bestAtk?.let { return it }
        synchronized(this) {
            bestAtk?.let { return it }
            val out = ArrayList<List<Int>>()
            try {
                val text = context.assets.open("arena/best_atk_records.json")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                val arr = org.json.JSONArray(text)
                for (i in 0 until arr.length()) {
                    val team = arr.optJSONArray(i) ?: continue
                    val ids = ArrayList<Int>(5)
                    for (j in 0 until team.length()) ids.add(team.optInt(j))
                    if (ids.size == 5) out.add(ids)
                }
                Log.i(TAG, "loaded ${out.size} best atk records")
            } catch (e: Exception) {
                Log.e(TAG, "load best_atk_records.json failed", e)
            }
            bestAtk = out
            return out
        }
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
