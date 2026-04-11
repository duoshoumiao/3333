package com.pcrjjc.app.util  
  
import android.content.Context  
import android.graphics.Bitmap  
import android.graphics.BitmapFactory  
import android.util.Log  
import java.io.File  
import kotlin.math.sqrt  
  
/**  
 * 角色头像识别器。  
 * 从截图中裁剪出竞技场防守阵容的5个头像位置，  
 * 与本地头像库做模板匹配，返回识别到的角色 baseId 列表。  
 */  
class IconRecognizer(private val context: Context) {  
  
    companion object {  
        private const val TAG = "IconRecognizer"  
        private const val TEMPLATE_SIZE = 64 // 模板统一缩放尺寸  
        private const val MATCH_THRESHOLD = 0.75 // NCC 匹配阈值  
        private const val ICON_DIR = "icons/unit"  
        private const val GAME_ASPECT_RATIO = 16f / 9f // 公主连结固定 16:9 渲染  
    }  
  
    /**  
     * 本地头像模板缓存: baseId -> Bitmap(64x64)  
     */  
    private var templates: Map<Int, Bitmap>? = null  
  
    /**  
     * 获取已加载的模板数量（用于外部检查）  
     */  
    fun getTemplateCount(): Int {  
        return loadTemplates().size  
    }  
  
    /**  
     * 加载本地头像库作为模板  
     */  
    private fun loadTemplates(): Map<Int, Bitmap> {  
        templates?.let { return it }  
  
        val dir = File(context.filesDir, ICON_DIR)  
        if (!dir.exists()) return emptyMap()  
  
        val result = mutableMapOf<Int, Bitmap>()  
        val files = dir.listFiles() ?: return emptyMap()  
  
        for (file in files) {  
            // 文件名格式: {baseId}_{star}.png  
            val name = file.nameWithoutExtension  
            val parts = name.split("_")  
            if (parts.size != 2) continue  
  
            val baseId = parts[0].toIntOrNull() ?: continue  
            val star = parts[1].toIntOrNull() ?: continue  
  
            // 优先使用6星，如果已有6星则跳过3星  
            if (result.containsKey(baseId) && star < 6) continue  
  
            try {  
                val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: continue  
                val scaled = Bitmap.createScaledBitmap(bmp, TEMPLATE_SIZE, TEMPLATE_SIZE, true)  
                if (bmp != scaled) bmp.recycle()  
                result[baseId] = scaled  
            } catch (e: Exception) {  
                Log.w(TAG, "加载模板失败: ${file.name}", e)  
            }  
        }  
  
        Log.i(TAG, "已加载 ${result.size} 个角色模板")  
        templates = result  
        return result  
    }  
  
    /**  
     * 从截图中识别防守阵容角色。  
     *  
     * 公主连结竞技场界面中，防守阵容的5个头像位于屏幕特定区域。  
     * 横屏模式下（游戏默认横屏），头像大致在屏幕中上部，水平均匀分布。  
     *  
     * @param screenshot 全屏截图  
     * @param screenW 屏幕宽度  
     * @param screenH 屏幕高度  
     * @return 识别到的角色 baseId 列表（最多5个）  
     */  
    fun recognize(screenshot: Bitmap, screenW: Int, screenH: Int): List<Int> {  
        val tmpl = loadTemplates()  
        if (tmpl.isEmpty()) {  
            Log.w(TAG, "本地头像库为空，请先下载角色头像")  
            return emptyList()  
        }  
  
        // 裁剪5个头像区域  
        val crops = cropDefenseIcons(screenshot, screenW, screenH)  
        if (crops.isEmpty()) {  
            Log.w(TAG, "裁剪头像区域失败")  
            return emptyList()  
        }  
  
        val result = mutableListOf<Int>()  
        for ((index, crop) in crops.withIndex()) {  
            val scaled = Bitmap.createScaledBitmap(crop, TEMPLATE_SIZE, TEMPLATE_SIZE, true)  
            val match = findBestMatch(scaled, tmpl)  
            if (crop != scaled) crop.recycle()  
            scaled.recycle()  
  
            if (match != null) {  
                Log.i(TAG, "位置$index: 匹配到 baseId=${match.first}, 相似度=${"%.3f".format(match.second)}")  
                result.add(match.first)  
            } else {  
                Log.w(TAG, "位置$index: 未匹配到角色")  
            }  
        }  
  
        return result  
    }  
  
    /**  
     * 从截图中裁剪出5个防守角色头像区域。  
     *  
     * 公主连结竞技场对战准备界面（横屏）：  
     * - 对手阵容头像在屏幕上半部分  
     * - 5个头像水平排列，大致在 y=18%~30%, x 从 28%~72% 均匀分布  
     * - 每个头像大约占屏幕宽度的 6%  
     *  
     * 这些比例基于 1920x1080 分辨率的标准布局。  
     * 公主连结固定以 16:9 渲染，非 16:9 屏幕会有黑边，  
     * 需要先计算游戏实际渲染区域再按比例裁剪。  
     */  
    private fun cropDefenseIcons(screenshot: Bitmap, screenW: Int, screenH: Int): List<Bitmap> {  
        // 计算游戏实际渲染区域（公主连结固定 16:9）  
        val screenRatio = screenW.toFloat() / screenH  
        val gameW: Int  
        val gameH: Int  
        val offsetX: Int  
        val offsetY: Int  
  
        if (screenRatio > GAME_ASPECT_RATIO) {  
            // 屏幕比 16:9 更宽（如 20:9 长屏手机），左右有黑边  
            gameH = screenH  
            gameW = (screenH * GAME_ASPECT_RATIO).toInt()  
            offsetX = (screenW - gameW) / 2  
            offsetY = 0  
        } else if (screenRatio < GAME_ASPECT_RATIO) {  
            // 屏幕比 16:9 更窄，上下有黑边  
            gameW = screenW  
            gameH = (screenW / GAME_ASPECT_RATIO).toInt()  
            offsetX = 0  
            offsetY = (screenH - gameH) / 2  
        } else {  
            // 刚好 16:9  
            gameW = screenW  
            gameH = screenH  
            offsetX = 0  
            offsetY = 0  
        }  
  
        Log.d(TAG, "屏幕: ${screenW}x${screenH} (ratio=${"%.2f".format(screenRatio)}), " +  
                "游戏区域: ${gameW}x${gameH}, 偏移: ($offsetX, $offsetY)")  
  
        // 竞技场防守阵容头像位置参数（基于游戏渲染区域的比例）  
        val iconCenterYRatio = 0.24f   // 头像中心 Y 比例  
        val iconSizeRatio = 0.058f     // 头像大小占游戏区域宽度比例  
        val iconStartXRatio = 0.30f    // 第一个头像中心 X 比例  
        val iconEndXRatio = 0.70f      // 最后一个头像中心 X 比例  
  
        val iconSize = (gameW * iconSizeRatio).toInt()  
        val halfSize = iconSize / 2  
        val centerY = offsetY + (gameH * iconCenterYRatio).toInt()  
  
        val crops = mutableListOf<Bitmap>()  
        for (i in 0 until 5) {  
            val ratio = iconStartXRatio + (iconEndXRatio - iconStartXRatio) * i / 4f  
            val centerX = offsetX + (gameW * ratio).toInt()  
  
            val left = (centerX - halfSize).coerceIn(0, screenshot.width - 1)  
            val top = (centerY - halfSize).coerceIn(0, screenshot.height - 1)  
            val w = iconSize.coerceAtMost(screenshot.width - left)  
            val h = iconSize.coerceAtMost(screenshot.height - top)  
  
            if (w > 0 && h > 0) {  
                try {  
                    val crop = Bitmap.createBitmap(screenshot, left, top, w, h)  
                    crops.add(crop)  
                } catch (e: Exception) {  
                    Log.w(TAG, "裁剪位置$i 失败: left=$left top=$top w=$w h=$h", e)  
                }  
            }  
        }  
  
        return crops  
    }  
  
    /**  
     * 在模板库中找到与 candidate 最匹配的角色。  
     * 使用归一化互相关 (NCC) 算法。  
     *  
     * @return Pair(baseId, similarity) 或 null（低于阈值）  
     */  
    private fun findBestMatch(candidate: Bitmap, templates: Map<Int, Bitmap>): Pair<Int, Double>? {  
        var bestId = -1  
        var bestScore = -1.0  
  
        val candPixels = getPixelArray(candidate)  
        val candMean = candPixels.average()  
        val candStd = stdDev(candPixels, candMean)  
  
        if (candStd < 1.0) return null // 纯色区域，跳过  
  
        for ((baseId, tmplBmp) in templates) {  
            val tmplPixels = getPixelArray(tmplBmp)  
            val tmplMean = tmplPixels.average()  
            val tmplStd = stdDev(tmplPixels, tmplMean)  
  
            if (tmplStd < 1.0) continue  
  
            // NCC = sum((a-mean_a)*(b-mean_b)) / (n * std_a * std_b)  
            val n = minOf(candPixels.size, tmplPixels.size)  
            var sum = 0.0  
            for (j in 0 until n) {  
                sum += (candPixels[j] - candMean) * (tmplPixels[j] - tmplMean)  
            }  
            val ncc = sum / (n * candStd * tmplStd)  
  
            if (ncc > bestScore) {  
                bestScore = ncc  
                bestId = baseId  
            }  
        }  
  
        return if (bestScore >= MATCH_THRESHOLD) Pair(bestId, bestScore) else null  
    }  
  
    /**  
     * 将 Bitmap 转为灰度像素数组  
     */  
    private fun getPixelArray(bmp: Bitmap): DoubleArray {  
        val w = bmp.width  
        val h = bmp.height  
        val pixels = IntArray(w * h)  
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)  
  
        return DoubleArray(pixels.size) { i ->  
            val p = pixels[i]  
            val r = (p shr 16) and 0xFF  
            val g = (p shr 8) and 0xFF  
            val b = p and 0xFF  
            // 灰度值  
            (0.299 * r + 0.587 * g + 0.114 * b)  
        }  
    }  
  
    private fun stdDev(arr: DoubleArray, mean: Double): Double {  
        var sum = 0.0  
        for (v in arr) {  
            val d = v - mean  
            sum += d * d  
        }  
        return sqrt(sum / arr.size)  
    }  
}