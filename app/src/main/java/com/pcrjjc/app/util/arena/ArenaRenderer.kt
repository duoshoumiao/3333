package com.pcrjjc.app.util.arena

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Log
import com.pcrjjc.app.util.ElementUnits
import com.pcrjjc.app.util.IconStorage

/**
 * 端上出图，替代 arena/old_main.py 的 render_atk_def_teams()（原实现用 PIL）。
 */
class ArenaRenderer(private val context: Context) {

    companion object {
        private const val TAG = "ArenaRenderer"
        private const val ICON = 64
        private const val BORDER = 5
        private const val TEXT_W = 200
        private const val ELEMENT_DOT = 18
        private val ELEMENT_COLORS = mapOf(
            "fire" to 0xFFFF6B4A.toInt(),
            "water" to 0xFF4A9EFF.toInt(),
            "wind" to 0xFF4AD991.toInt(),
            "light" to 0xFFFFD54A.toInt(),
            "dark" to 0xFFB06BFF.toInt()
        )
    }

    data class Row(
        val ids: List<Int> = emptyList(),
        val up: Int = 0,
        val down: Int = 0,
        val updated: String = "",
        val tag: String? = null
    ) {
        val isBlank: Boolean get() = ids.isEmpty()
    }

    private val iconCache = HashMap<Int, Bitmap?>()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 20f
        typeface = Typeface.DEFAULT
    }

    private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun renderSolutions(rows: List<Row>): Bitmap? {
        if (rows.isEmpty()) return null
        val w = 5 * ICON + TEXT_W
        val h = rows.size * (ICON + BORDER) - BORDER
        if (h <= 0) return null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        for (i in rows.indices) {
            val row = rows[i]
            if (row.isBlank) continue
            val y = i * (ICON + BORDER)
            for (j in 0 until 5) {
                val x = j * ICON
                val baseId = row.ids.getOrNull(j) ?: 0
                val icon = if (baseId > 0) loadIcon(baseId) else null
                if (icon != null) {
                    canvas.drawBitmap(icon, null, Rect(x, y, x + ICON, y + ICON), null)
                } else {
                    val p = Paint().apply { color = 0xFFDDDDDD.toInt() }
                    canvas.drawRect(x.toFloat(), y.toFloat(), (x + ICON).toFloat(),
                        (y + ICON).toFloat(), p)
                }
                if (baseId > 0) {
                    val key = ElementUnits.elementKeyOf(baseId)
                    val color = ELEMENT_COLORS[key]
                    if (color != null) {
                        dotPaint.color = color
                        val cx = x + ICON - ELEMENT_DOT / 2f - 2f
                        val cy = y + ELEMENT_DOT / 2f + 2f
                        canvas.drawCircle(cx, cy, ELEMENT_DOT / 2f, dotPaint)
                    }
                }
            }
            val tx = 5 * ICON + 10f
            if (row.tag != null) {
                canvas.drawText(row.tag, tx, y + 28f, tagPaint)
            } else {
                canvas.drawText("赞 ${row.up}", tx, y + 20f, textPaint)
                canvas.drawText("踩 ${row.down}", tx, y + 44f, textPaint)
                val time = formatTime(row.updated)
                if (time.isNotEmpty()) {
                    canvas.drawText(time, tx, y + 64f, textPaint)
                }
            }
        }
        return bmp
    }

    /** 识别高亮图：红框=已识别，蓝框=被排除 */
    fun renderHighlight(src: Bitmap, result: ArenaTeamRecognizer.Result): Bitmap? {
        if (result.cells.isEmpty() && result.rejected.isEmpty()) return null
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bmp)
        val red = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val blue = Paint().apply {
            color = Color.BLUE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        for (c in result.rejected) {
            canvas.drawRect(c.x.toFloat(), c.y.toFloat(), (c.x + c.w).toFloat(),
                (c.y + c.h).toFloat(), blue)
        }
        for (c in result.cells) {
            canvas.drawRect(c.x.toFloat(), c.y.toFloat(), (c.x + c.w).toFloat(),
                (c.y + c.h).toFloat(), red)
        }
        return scaleDown(bmp, 500)
    }

    /** 对比图：上排=截图裁切，下排=标准头像 */
    fun renderCompare(src: Bitmap, result: ArenaTeamRecognizer.Result): Bitmap? {
        val cells = result.cells
        if (cells.isEmpty()) return null
        val perRow = 5
        val rows = (cells.size + perRow - 1) / perRow
        val w = ICON * perRow + 16 * 2
        val h = ICON * 2 * rows + 16 * (rows + 1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        for ((idx, cell) in cells.withIndex()) {
            val r = idx / perRow
            val c = idx % perRow
            val px = 16 + ICON * c
            val py = 16 * (r + 1) + ICON * 2 * r
            val sx = cell.x.coerceIn(0, maxOf(0, src.width - 1))
            val sy = cell.y.coerceIn(0, maxOf(0, src.height - 1))
            val sw = cell.w.coerceAtMost(src.width - sx)
            val sh = cell.h.coerceAtMost(src.height - sy)
            if (sw > 0 && sh > 0) {
                val crop = Bitmap.createBitmap(src, sx, sy, sw, sh)
                canvas.drawBitmap(crop, null, Rect(px, py, px + ICON, py + ICON), null)
                crop.recycle()
            }
            val icon = loadIcon(cell.unitId)
            if (icon != null) {
                canvas.drawBitmap(icon, null, Rect(px, py + ICON, px + ICON, py + ICON * 2), null)
            }
        }
        return bmp
    }

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val maxCurrent = maxOf(src.width, src.height)
        if (maxCurrent <= maxSide) return src
        val ratio = maxCurrent.toFloat() / maxSide
        val w = (src.width / ratio).toInt().coerceAtLeast(1)
        val h = (src.height / ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun loadIcon(baseId: Int): Bitmap? {
        iconCache[baseId]?.let { return it }
        if (iconCache.containsKey(baseId)) return null
        var bmp: Bitmap? = null
        try {
            val path = IconStorage.getIconPath(context, baseId, 6)
                ?: IconStorage.getIconPath(context, baseId, 3)
                ?: IconStorage.getIconPath(context, baseId, 1)
            if (path != null) {
                val decoded = BitmapFactory.decodeFile(path)
                if (decoded != null) {
                    bmp = Bitmap.createScaledBitmap(decoded, ICON, ICON, true)
                    if (bmp !== decoded) decoded.recycle()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "load icon $baseId failed", e)
        }
        iconCache[baseId] = bmp
        return bmp
    }

    /** 2024-09-02T22:01:57.852Z -> 09-02 22:01 */
    private fun formatTime(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            val s = raw.replace("Z", "+00:00")
            val cleaned = if (s.contains(".")) {
                val head = s.substringBefore(".")
                val rest = s.substringAfter(".")
                val ms = rest.takeWhile { it.isDigit() }.padEnd(6, '0').take(6)
                val tz = rest.dropWhile { it.isDigit() }
                "$head.$ms$tz"
            } else s
            val odt = java.time.OffsetDateTime.parse(cleaned)
            val local = odt.atZoneSameInstant(java.time.ZoneId.systemDefault())
            String.format("%02d-%02d %02d:%02d",
                local.monthValue, local.dayOfMonth, local.hour, local.minute)
        } catch (e: Exception) {
            ""
        }
    }
}
