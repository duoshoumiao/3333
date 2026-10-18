package com.pcrjjc.app.util.arena

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs

/**
 * 移植自 arena/old_main.py 的 getPos() / cutting() / cut() / getUnit()。
 *
 * 流程：反复「找最大的矩形区域 -> 裁掉」逐层缩小，直到找到 4 个以上的方形头像；
 * 找到后按行列聚类，逐格做 256bit 指纹比对，得到每队的 4 位角色 ID。
 */
class ArenaTeamRecognizer(
    private val hashes: List<Pair<Int, ByteArray>>
) {

    constructor(context: Context) : this(ArenaAssets.unitHashes(context))

    companion object {
        private const val TAG = "ArenaRecognizer"
        private const val UNKNOWN_DISTANCE = 90
    }

    data class Cell(val x: Int, val y: Int, val w: Int, val h: Int, val unitId: Int)

    data class Result(
        val teams: List<List<Int>>,
        val cells: List<Cell>,
        val rejected: List<Cell>
    )

    private class Canvas(val px: IntArray, val w: Int, val h: Int) {
        fun crop(x: Int, y: Int, cw: Int, ch: Int): Canvas {
            val cx = x.coerceIn(0, maxOf(0, w - 1))
            val cy = y.coerceIn(0, maxOf(0, h - 1))
            val ww = cw.coerceIn(1, w - cx)
            val hh = ch.coerceIn(1, h - cy)
            val out = IntArray(ww * hh)
            for (r in 0 until hh) {
                System.arraycopy(px, (cy + r) * w + cx, out, r * ww, ww)
            }
            return Canvas(out, ww, hh)
        }

        /** im_grey.point(lambda x: 0 if x > 128 else 255) —— 逐通道二值反转 */
        fun invertedPerChannel(): Canvas {
            val out = IntArray(px.size)
            for (i in px.indices) {
                val p = px[i]
                var a = (p ushr 24) and 0xFF
                var r = (p shr 16) and 0xFF
                var g = (p shr 8) and 0xFF
                var b = p and 0xFF
                a = if (a > 128) 0 else 255
                r = if (r > 128) 0 else 255
                g = if (g > 128) 0 else 255
                b = if (b > 128) 0 else 255
                out[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            return Canvas(out, w, h)
        }

        fun toBinaryCanvas(): Canvas {
            val mask = ArenaConnectedComponents.binarize(px, w, h)
            val out = IntArray(px.size)
            for (i in mask.indices) {
                out[i] = if (mask[i]) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            }
            return Canvas(out, w, h)
        }
    }

    private data class Cut2(val border: List<ArenaConnectedComponents.Box>, val other: List<ArenaConnectedComponents.Box>)

    fun recognize(src: Bitmap): Result {
        val w = src.width
        val h = src.height
        if (w < 32 || h < 32) return Result(emptyList(), emptyList(), emptyList())
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        return recognizePixels(px, w, h)
    }

    /** 纯像素入口：便于在 JVM 上做单元测试（不依赖 Bitmap） */
    fun recognizePixels(px: IntArray, w: Int, h: Int): Result {
        if (w < 32 || h < 32) return Result(emptyList(), emptyList(), emptyList())

        // 不复制像素数组：crop / invert 都会产生新数组，px 本身只读
        var work = Canvas(px, w, h)
        var color = Canvas(px, w, h)
        var offX = 0
        var offY = 0
        val cells = ArrayList<Cell>()
        val rejected = ArrayList<Cell>()

        var cnt = 0
        while (cnt <= 6) {
            cnt++
            var invertNext = false
            val cut = cutting2(work)
            if (cut.border.isEmpty()) {
                invertNext = true
            } else {
                val teams = assemble(color, cut.border, cut.other, offX, offY, cells, rejected)
                if (teams != null) {
                    Log.i(TAG, "recognized ${teams.size} team(s), cells=${cells.size}")
                    return Result(teams, cells, rejected)
                }
                invertNext = false
            }

            val biggest = cuttingMax(work)
            if (biggest == null) {
                Log.i(TAG, "no more region to crop, stop")
                break
            }
            // 与原实现一致：先在原图上找最大矩形并裁剪，再对裁剪结果反色
            work = biggest.first
            if (cnt == 1 || invertNext) {
                work = work.invertedPerChannel()
            }
            color = color.crop(biggest.second.x + 2, biggest.second.y + 2,
                biggest.second.w - 4, biggest.second.h - 4)
            offX += biggest.second.x + 2
            offY += biggest.second.y + 2
        }
        return Result(emptyList(), cells, rejected)
    }

    // ======================== cutting(mode=2) ========================

    private fun cutting2(c: Canvas): Cut2 {
        val comps = ArenaConnectedComponents.components(
            ArenaConnectedComponents.binarize(c.px, c.w, c.h), c.w, c.h, 1
        )
        val totArea = c.w.toDouble() * c.h
        val icons = ArrayList<Pair<Int, ArenaConnectedComponents.Box>>()
        for (b in comps) {
            if (b.area <= 500) continue
            val ratio = b.w.toDouble() / b.h.toDouble()
            if (!(ratio > 0.95 && ratio < 1.05)) continue
            val are = (b.w + b.h) / 2.0
            if (are * are / totArea * 100 < 0.5) continue
            icons.add(are.toInt() to b)
        }
        if (icons.isEmpty()) return Cut2(emptyList(), emptyList())

        val kinds = LinkedHashMap<Int, MutableList<ArenaConnectedComponents.Box>>()
        for ((side, box) in icons) {
            var placed = false
            for (k in kinds.keys.toList()) {
                val r = side.toDouble() / k.toDouble()
                if (r > 0.9 && r < 1.1) {
                    kinds[k]!!.add(box)
                    placed = true
                    break
                }
            }
            if (!placed) {
                kinds[side] = mutableListOf(box)
            }
        }

        fun weight(e: Map.Entry<Int, MutableList<ArenaConnectedComponents.Box>>): Int {
            val n = e.value.size
            val side = e.key
            return when {
                n == 5 -> 5_000_000 + side
                n % 5 == 0 -> 1_000_000 + side
                else -> n * 10_000 + side
            }
        }

        val sorted = kinds.entries.sortedByDescending { weight(it) }
        val best = sorted[0]
        return if (best.value.size % 5 == 0) {
            val others = ArrayList<ArenaConnectedComponents.Box>()
            for (i in 1 until sorted.size) others.addAll(sorted[i].value)
            Cut2(best.value.toList(), others)
        } else {
            Cut2(icons.map { it.second }, emptyList())
        }
    }

    // ======================== cutting(mode=1) ========================

    private fun cuttingMax(c: Canvas): Pair<Canvas, ArenaConnectedComponents.Box>? {
        val comps = ArenaConnectedComponents.components(
            ArenaConnectedComponents.binarize(c.px, c.w, c.h), c.w, c.h, 1
        )
        if (comps.isEmpty()) return null
        val biggest = comps.maxByOrNull { it.area } ?: return null
        val cw = biggest.w - 4
        val ch = biggest.h - 4
        if (cw <= 0 || ch <= 0) return null
        return c.toBinaryCanvas().crop(biggest.x + 2, biggest.y + 2, cw, ch) to biggest
    }

    // ======================== 行列聚类 ========================

    private fun splitLast(
        recs: List<ArenaConnectedComponents.Box>
    ): Pair<List<ArenaConnectedComponents.Box>, List<ArenaConnectedComponents.Box>> {
        if (recs.isEmpty()) return emptyList<ArenaConnectedComponents.Box>() to emptyList()
        val sorted = recs.sortedByDescending { it.x }
        val first = sorted[0]
        val last = sorted.filter { abs(it.x - first.x) < first.w / 2.0 }.sortedBy { it.y }
        val rest = sorted.filter { it !in last }
        return rest to last
    }

    private fun assemble(
        color: Canvas,
        border: List<ArenaConnectedComponents.Box>,
        other: List<ArenaConnectedComponents.Box>,
        offX: Int,
        offY: Int,
        cells: MutableList<Cell>,
        rejected: MutableList<Cell>
    ): List<List<Int>>? {
        if (border.size < 4) {
            for (b in border) rejected.add(Cell(b.x + offX, b.y + offY, b.w, b.h, 0))
            return null
        }
        for (b in other) rejected.add(Cell(b.x + offX, b.y + offY, b.w, b.h, 0))

        // 注意：原实现的第一次 split 只用于「数出行数」，不消耗列。
        // 循环里的 split 才是真正从右往左逐列弹出。
        var recs = border.toMutableList()
        val lastCol = splitLast(recs).second
        val rowCnt = lastCol.size
        if (rowCnt == 0) return null

        val arr = Array(rowCnt) { arrayOfNulls<ArenaConnectedComponents.Box>(5) }
        val arrId = Array(rowCnt) { ArrayList<Int>() }
        val arrId6 = Array(rowCnt) { IntArray(5) }
        val rowY = IntArray(rowCnt) { lastCol[it].y }

        for (col in 0 until 5) {
            val split = splitLast(recs)
            recs = split.first.toMutableList()
            val colRecs = split.second
            if (colRecs.isEmpty()) break
            for (rec in colRecs) {
                val crop = color.crop(rec.x + 2, rec.y + 2, rec.w - 4, rec.h - 4)
                val uid6 = identify(crop)
                val unitId = uid6 / 100
                if (unitId == 0) {
                    rejected.add(Cell(rec.x + offX, rec.y + offY, rec.w, rec.h, 0))
                    continue
                }
                cells.add(Cell(rec.x + offX, rec.y + offY, rec.w, rec.h, unitId))
                var bestRow = 0
                for (r in 1 until rowCnt) {
                    if (abs(rowY[r] - rec.y) < abs(rowY[bestRow] - rec.y)) bestRow = r
                }
                val current = arr[bestRow][col]
                if (current == null || abs(rowY[bestRow] - current.y) > abs(rowY[bestRow] - rec.y)) {
                    arr[bestRow][col] = rec
                    arrId[bestRow].add(unitId)
                    arrId6[bestRow][col] = uid6
                }
            }
        }

        val out = ArrayList<List<Int>>(rowCnt)
        for (r in 0 until rowCnt) {
            val missing = arr[r].count { it == null }
            out.add(if (missing >= 2) emptyList() else arrId[r].toList())
        }
        return out
    }

    // ======================== getUnit ========================

    private fun identify(crop: Canvas): Int {
        if (crop.w < 8 || crop.h < 8) return 0
        val gray = ArenaLanczos.toGray(crop.px)
        val r128 = ArenaLanczos.resizeGray(gray, crop.w, crop.h, 128, 128)
        // 对应 PIL 的 img3[25:96, 8:97]：71 行 × 89 列
        val region = FloatArray(71 * 89)
        for (r in 0 until 71) {
            System.arraycopy(r128, (r + 25) * 128 + 8, region, r * 89, 89)
        }
        val fp = ArenaLanczos.fingerprint(region, 89, 71)

        var bestUid = 0
        var bestDist = Int.MAX_VALUE
        for ((uid, ref) in hashes) {
            val d = ArenaLanczos.distance(fp, ref)
            if (d < bestDist) {
                bestDist = d
                bestUid = uid
            }
        }
        return if (bestDist > UNKNOWN_DISTANCE) 0 else bestUid
    }
}
