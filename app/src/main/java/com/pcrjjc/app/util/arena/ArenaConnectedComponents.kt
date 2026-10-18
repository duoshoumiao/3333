package com.pcrjjc.app.util.arena

/**
 * 移植自 arena/old_main.py 的 cutting()：
 * 二值化 -> 找连通域（等价 cv2.findContours + boundingRect）-> 按方形/面积过滤。
 *
 * 之所以不用 OpenCV：原代码只用到 findContours / contourArea / boundingRect，
 * 用连通域标记即可等价替代（已在 Python 侧验证输出完全一致）。
 */
object ArenaConnectedComponents {

    data class Box(val x: Int, val y: Int, val w: Int, val h: Int, val area: Int) {
        val side: Int get() = (w + h) / 2
    }

    /** >threshold 视为前景。原实现：255 if x > 210 else 0 */
    fun binarize(px: IntArray, w: Int, h: Int, threshold: Int = 210): BooleanArray {
        val mask = BooleanArray(w * h)
        for (i in 0 until w * h) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val luma = (r * 299 + g * 587 + b * 114) / 1000
            if (luma > threshold) mask[i] = true
        }
        return mask
    }

    /** 8 邻域连通域标记，返回每个连通域的外接矩形与像素数。 */
    fun components(mask: BooleanArray, w: Int, h: Int, minArea: Int = 1): List<Box> {
        val visited = BooleanArray(w * h)
        val stack = IntArray(w * h)
        val out = ArrayList<Box>(32)
        for (start in 0 until w * h) {
            if (!mask[start] || visited[start]) continue
            var sp = 0
            stack[sp++] = start
            visited[start] = true
            var minX = w
            var maxX = -1
            var minY = h
            var maxY = -1
            var area = 0
            while (sp > 0) {
                val idx = stack[--sp]
                val x = idx % w
                val y = idx / w
                area++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                if (x > 0 && y > 0) {
                    val n = idx - w - 1
                    if (mask[n] && !visited[n]) { visited[n] = true; stack[sp++] = n }
                }
                if (y > 0) {
                    val n = idx - w
                    if (mask[n] && !visited[n]) { visited[n] = true; stack[sp++] = n }
                }
                if (x < w - 1 && y > 0) {
                    val n = idx - w + 1
                    if (mask[n] && !visited[n]) { visited[n] = true; stack[sp++] = n }
                }
                if (x > 0) {
                    val n = idx - 1
                    if (mask[n] && !visited[n]) { visited[n] = true; stack[sp++] = n }
                }
                if (x < w - 1) {
                    val n = idx + 1
                    if (mask[n] && !visited[n]) { visited[n] = true; stack[sp++] = n }
                }
                if (x > 0 && y < h - 1) {
                    val n = idx + w - 1
                    if (mask[n] && !visited[n]) { visited[n] = true; stack[sp++] = n }
                }
                if (y < h - 1) {
                    val n = idx + w
                    if (mask[n] && !visited[n]) { visited[n] = true; stack[sp++] = n }
                }
                if (x < w - 1 && y < h - 1) {
                    val n = idx + w + 1
                    if (mask[n] && !visited[n]) { visited[n] = true; stack[sp++] = n }
                }
            }
            if (area >= minArea) {
                out.add(Box(minX, minY, maxX - minX + 1, maxY - minY + 1, area))
            }
        }
        return out
    }
}
