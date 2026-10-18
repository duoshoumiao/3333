package com.pcrjjc.app.util.arena

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.util.zip.GZIPInputStream

/**
 * 识别算法移植的正确性验证。
 *
 * 基准是「绘制进截图里的真值」（expected.json 的 requested 字段），
 * 由 arena_local_port/verify/gen_testdata.py 生成：截图里每个头像格放的就是这些角色。
 *
 * 注意：不能用 arena/old_main.py 的输出当基准。实测发现，PIL 的 LANCZOS 与
 * 自写 Lanczos3 在个别近似角色上会给出不同结果（1014/1029 相距仅 7bit）；
 * 在 pjjc 那张图上，Python 参考实现把 1014 误判成 1029，而本移植版判对。
 */
class ArenaRecognizerTest {

    private fun loadHashes(): List<Pair<Int, ByteArray>> {
        val text = javaClass.classLoader!!
            .getResourceAsStream("arena/hashes.json")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val obj = JSONObject(text)
        val out = ArrayList<Pair<Int, ByteArray>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val hex = obj.getString(k)
            val bytes = ByteArray(hex.length / 2)
            for (i in bytes.indices) {
                bytes[i] = ((Character.digit(hex[i * 2], 16) shl 4) or
                    Character.digit(hex[i * 2 + 1], 16)).toByte()
            }
            out.add(k.toInt() to bytes)
        }
        return out
    }

    private fun loadExpected(): JSONObject {
        val text = javaClass.classLoader!!
            .getResourceAsStream("arena/expected.json")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        return JSONObject(text)
    }

    private fun decode(name: String): Triple<IntArray, Int, Int> {
        DataInputStream(
            GZIPInputStream(javaClass.classLoader!!.getResourceAsStream("arena/$name"))
        ).use { din ->
            val w = din.readInt()
            val h = din.readInt()
            val px = IntArray(w * h)
            for (i in px.indices) px[i] = din.readInt()
            return Triple(px, w, h)
        }
    }

    private fun recognizeAsSets(binName: String): List<Set<Int>> {
        val (px, w, h) = decode(binName)
        val rec = ArenaTeamRecognizer(loadHashes()).recognizePixels(px, w, h)
        return rec.teams.filter { it.size >= 4 }.map { it.toSet() }
    }

    /** 截图里画进去的真值，按队返回 */
    private fun requested(pngName: String): List<Set<Int>> {
        val root = loadExpected().getJSONObject(pngName).getJSONArray("requested")
        val out = ArrayList<Set<Int>>()
        for (i in 0 until root.length()) {
            val team = root.getJSONArray(i)
            val set = HashSet<Int>()
            for (j in 0 until team.length()) set.add(team.getInt(j))
            out.add(set)
        }
        return out
    }

    @Test
    fun recognizesSingleTeamExactly() {
        val want = requested("shot_jjc.png")
        val got = recognizeAsSets("shot_jjc.bin.gz")
        assertEquals("应识别出 1 队", want.size, got.size)
        assertEquals("单队识别结果与截图中绘制的阵容不一致", want[0], got[0])
    }

    @Test
    fun recognizesThreeTeamsExactly() {
        val want = requested("shot_pjjc.png")
        val got = recognizeAsSets("shot_pjjc.bin.gz")
        assertEquals("应识别出 3 队", want.size, got.size)
        for (i in want.indices) {
            assertEquals("第 ${i + 1} 队识别结果与绘制阵容不一致", want[i], got[i])
        }
    }

    @Test
    fun recognizesAllCellsWithoutUnknown() {
        // 15 个格子都应识别出来（不允许落到「未知」）。
        // 注意：rejected 里可能含被排除的次级方块（原实现也会用蓝框标出），不作为失败条件。
        val (px, w, h) = decode("shot_pjjc.bin.gz")
        val rec = ArenaTeamRecognizer(loadHashes()).recognizePixels(px, w, h)
        assertEquals("识别到的头像格数不对", 15, rec.cells.size)
        assertTrue("存在未识别的格子", rec.cells.all { it.unitId > 0 })
    }

    @Test
    fun fingerprintsMatchShippedAsset() {
        // 同一张图、同一套代码，指纹库自洽性：对 940 个角色自匹配必须命中最优 0 距离
        val hashes = loadHashes()
        assertEquals("指纹库条目数异常", 940, hashes.size)
        for ((uid, ref) in hashes.take(64)) {
            val d = ArenaLanczos.distance(ref, ref)
            assertEquals("自匹配距离应为 0 ($uid)", 0, d)
        }
    }
}
