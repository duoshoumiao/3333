package com.pcrjjc.app.util  
  
import android.content.Context  
import android.graphics.Bitmap  
import android.graphics.BitmapFactory  
import android.util.Log  
import java.io.File  
import java.io.FileOutputStream  
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
        private const val MATCH_THRESHOLD = 0.55 // NCC 匹配阈值（从0.75降低到0.55）  
        private const val ICON_DIR = "icons/unit"  
        private const val DEBUG_DIR = "debug_crops" // 调试用：保存裁剪图片  
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
        if (!dir.exists()) {  
            Log.w(TAG, "头像目录不存在: ${dir.absolutePath}")  
            return emptyMap()  
        }  
  
        val result = mutableMapOf<Int, Bitmap>()  
        val files = dir.listFiles() ?: return emptyMap()  
  
        Log.i(TAG, "头像目录文件数: ${files.size}, 路径: ${dir.absolutePath}")  
  
        for (file in files) {  
            // 文件名格式: {baseId}_{star}.png  
            val name = file.nameWithoutExtension  
            val parts = name.split("_")  
            if (parts.size != 2) {  
                Log.d(TAG, "跳过非标准文件名: ${file.name}")  
                continue  
            }  
  
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
     * @param screenshot 全屏截图  
     * @param screenW 屏幕宽度（VirtualDisplay 的宽度）  
     * @param screenH 屏幕高度（VirtualDisplay 的高度）  
     * @return 识别到的角色 baseId 列表（最多5个）  
     */  
    fun recognize(screenshot: Bitmap, screenW: Int, screenH: Int): List<Int> {  
        val tmpl = loadTemplates()  
        if (tmpl.isEmpty()) {  
            Log.w(TAG, "本地头像库为空，请先下载角色头像")  
            return emptyList()  
        }  
  
        Log.i(TAG, "截图尺寸: ${screenshot.width}x${screenshot.height}, 屏幕参数: ${screenW}x${screenH}")  
  
        // 检测横竖屏：公主连结是横屏游戏，如果截图宽度<高度，说明是竖屏截图  
        val actualW: Int  
        val actualH: Int  
        val actualScreenshot: Bitmap  
  
        if (screenshot.width < screenshot.height) {  
            // 竖屏截图，需要旋转90度或者交换宽高来裁剪  
            // 实际上 MediaProjection 截图的内容已经是正确的（游戏横屏内容被旋转到竖屏画布上）  
            // 我们直接用截图的实际尺寸来裁剪  
            Log.i(TAG, "检测到竖屏截图，使用截图实际尺寸")  
            actualW = screenshot.width  
            actualH = screenshot.height  
            actualScreenshot = screenshot  
        } else {  
            actualW = screenshot.width  
            actualH = screenshot.height  
            actualScreenshot = screenshot  
        }  
  
        // 裁剪5个头像区域  
        val crops = cropDefenseIcons(actualScreenshot, actualW, actualH)  
        if (crops.isEmpty()) {  
            Log.w(TAG, "裁剪头像区域失败")  
            return emptyList()  
        }  
  
        // 调试：保存截图和裁剪结果到本地  
        saveDebugImages(actualScreenshot, crops)  
  
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
                Log.w(TAG, "位置$index: 未匹配到角色（最佳分数低于阈值 $MATCH_THRESHOLD）")  
            }  
        }  
  
        return result  
    }  
  
    /**  
     * 保存调试图片，方便排查裁剪位置是否正确  
     */  
    private fun saveDebugImages(screenshot: Bitmap, crops: List<Bitmap>) {  
        try {  
            val debugDir = File(context.filesDir, DEBUG_DIR)  
            if (!debugDir.exists()) debugDir.mkdirs()  
  
            // 保存完整截图（缩小到1/4节省空间）  
            val smallScreenshot = Bitmap.createScaledBitmap(  
                screenshot, screenshot.width / 4, screenshot.height / 4, true  
            )  
            FileOutputStream(File(debugDir, "screenshot.png")).use { out ->  
                smallScreenshot.compress(Bitmap.CompressFormat.PNG, 80, out)  
            }  
            smallScreenshot.recycle()  
            Log.i(TAG, "调试截图已保存到: ${debugDir.absolutePath}/screenshot.png")  
  
            // 保存每个裁剪区域  
            for ((i, crop) in crops.withIndex()) {  
                FileOutputStream(File(debugDir, "crop_$i.png")).use { out ->  
                    crop.compress(Bitmap.CompressFormat.PNG, 100, out)  
                }  
            }  
            Log.i(TAG, "调试裁剪图已保存到: ${debugDir.absolutePath}/crop_0~4.png")  
        } catch (e: Exception) {  
            Log.w(TAG, "保存调试图片失败", e)  
        }  
    }  
  
    /**  
     * 从截图中裁剪出5个防守角色头像区域。  
     *  
     * 公主连结竞技场对战准备界面（横屏）：  
     * - 对手阵容头像在屏幕上半部分  
     * - 5个头像水平排列  
     *  
     * 注意：如果截图是竖屏的（宽<高），说明游戏横屏内容被系统旋转了，  
     * 此时"宽"实际是短边，"高"是长边，裁剪比例需要对应调整。  
     */  
    private fun cropDefenseIcons(screenshot: Bitmap, screenW: Int, screenH: Int): List<Bitmap> {  
        val isLandscape = screenW > screenH  
  
        // 竞技场防守阵容头像位置参数  
        val iconCenterYRatio: Float  
        val iconSizeRatio: Float  
        val iconStartXRatio: Float  
        val iconEndXRatio: Float  
  
        if (isLandscape) {  
            // 横屏模式（正常游戏状态）  
            iconCenterYRatio = 0.24f  
            iconSizeRatio = 0.058f  
            iconStartXRatio = 0.30f  
            iconEndXRatio = 0.70f  
        } else {  
            // 竖屏截图（游戏内容被旋转到竖屏画布）  
            // 横屏的 Y=24% 对应竖屏的 X 方向  
            // 横屏的 X=30%~70% 对应竖屏的 Y 方向  
            // 但实际上 MediaProjection 截图内容方向与屏幕一致  
            // 如果手机竖着但游戏横屏，截图内容仍然是横屏的，只是被放到竖屏画布上  
            // 游戏内容会在竖屏画布的中间区域，上下有黑边  
            // 这种情况下，游戏内容的有效区域大约在 y=25%~75%（中间50%）  
            // 头像在有效区域内的相对位置与横屏相同  
            iconCenterYRatio = 0.37f  // 0.25 + 0.24 * 0.5 ≈ 0.37  
            iconSizeRatio = 0.032f    // 横屏的 0.058 * (screenH/screenW 比例调整)  
            iconStartXRatio = 0.30f  
            iconEndXRatio = 0.70f  
        }  
  
        Log.i(TAG, "裁剪参数: landscape=$isLandscape, centerY=$iconCenterYRatio, size=$iconSizeRatio, xRange=$iconStartXRatio~$iconEndXRatio")  
  
        val iconSize = (screenW * iconSizeRatio).toInt().coerceAtLeast(32)  
        val halfSize = iconSize / 2  
        val centerY = (screenH * iconCenterYRatio).toInt()  
  
        Log.i(TAG, "裁剪计算: iconSize=$iconSize, centerY=$centerY, screenW=$screenW, screenH=$screenH")  
  
        val crops = mutableListOf<Bitmap>()  
        for (i in 0 until 5) {  
            val ratio = iconStartXRatio + (iconEndXRatio - iconStartXRatio) * i / 4f  
            val centerX = (screenW * ratio).toInt()  
  
            val left = (centerX - halfSize).coerceIn(0, screenshot.width - iconSize)  
            val top = (centerY - halfSize).coerceIn(0, screenshot.height - iconSize)  
            val w = iconSize.coerceAtMost(screenshot.width - left)  
            val h = iconSize.coerceAtMost(screenshot.height - top)  
  
            Log.d(TAG, "位置$i: centerX=$centerX, left=$left, top=$top, w=$w, h=$h")  
  
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
  
        if (candStd < 1.0) {  
            Log.d(TAG, "候选区域为纯色，跳过")  
            return null  
        }  
  
        for ((baseId, tmplBmp) in templates) {  
            val tmplPixels = getPixelArray(tmplBmp)  
            val tmplMean = tmplPixels.average()  
            val tmplStd = stdDev(tmplPixels, tmplMean)  
  
            if (tmplStd < 1.0) continue  
  
            // NCC = sum((a-mean_a)*(b-mean_b)) / (n * std_a * std_b)  
            val n = minOf(candPixels.size, tmplPixels.size)  
            var sum = 0.0  
            for (i in 0 until n) {  
                sum += (candPixels[i] - candMean) * (tmplPixels[i] - tmplMean)  
            }  
            val ncc = sum / (n * candStd * tmplStd)  
  
            if (ncc > bestScore) {  
                bestScore = ncc  
                bestId = baseId  
            }  
        }  
  
        Log.d(TAG, "最佳匹配: baseId=$bestId, score=${"%.3f".format(bestScore)}, threshold=$MATCH_THRESHOLD")  
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