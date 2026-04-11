package com.pcrjjc.app.util  
  
import android.content.Context  
import android.graphics.Bitmap  
import android.graphics.BitmapFactory  
import android.util.Log  
import org.opencv.android.OpenCVLoader  
import org.opencv.android.Utils  
import org.opencv.core.Core  
import org.opencv.core.CvType  
import org.opencv.core.Mat  
import org.opencv.core.MatOfPoint  
import org.opencv.core.Scalar  
import org.opencv.imgproc.Imgproc  
import java.io.File  
import kotlin.math.abs  
import kotlin.math.sqrt  
  
/**  
 * 角色头像识别器。  
 * 使用 OpenCV 轮廓检测从截图中自动定位角色头像（移植自 arena 的 cutting 算法），  
 * 再与本地头像库做 NCC 模板匹配，返回识别到的角色 baseId 列表。  
 */  
class IconRecognizer(private val context: Context) {  
  
    companion object {  
        private const val TAG = "IconRecognizer"  
        private const val TEMPLATE_SIZE = 64   // 模板统一缩放尺寸  
        private const val MATCH_THRESHOLD = 0.70 // NCC 匹配阈值（OpenCV 裁剪更精准，可适当放宽）  
        private const val ICON_DIR = "icons/unit"  
        private const val GAME_ASPECT_RATIO = 16f / 9f  
  
        // OpenCV 轮廓检测参数（对应 arena 的 cutting mode=2）  
        private const val BINARY_THRESHOLD = 210.0  // 二值化阈值（arena: x > 210 → 255）  
        private const val MIN_CONTOUR_AREA = 500.0   // 最小轮廓面积  
        private const val SQUARE_RATIO_MIN = 0.95     // 近似正方形判定下限 (w/h)  
        private const val SQUARE_RATIO_MAX = 1.05     // 近似正方形判定上限 (w/h)  
        private const val MIN_AREA_PERCENT = 0.5      // 最小面积占比 (%)  
        private const val CLUSTER_TOLERANCE = 0.10    // 边长聚类容差 (10%)  
  
        @Volatile  
        private var opencvInitialized = false  
    }  
  
    /** 本地头像模板缓存: baseId -> Bitmap(64x64) */  
    private var templates: Map<Int, Bitmap>? = null  
  
    /** 获取已加载的模板数量 */  
    fun getTemplateCount(): Int {  
        return loadTemplates().size  
    }  
  
    /** 确保 OpenCV 已初始化 */  
    private fun ensureOpenCV(): Boolean {  
        if (opencvInitialized) return true  
        opencvInitialized = OpenCVLoader.initLocal()  
        if (!opencvInitialized) {  
            Log.e(TAG, "OpenCV 初始化失败")  
        } else {  
            Log.i(TAG, "OpenCV 初始化成功: ${OpenCVLoader.OPENCV_VERSION}")  
        }  
        return opencvInitialized  
    }  
  
    /** 加载本地头像库作为模板 */  
    private fun loadTemplates(): Map<Int, Bitmap> {  
        templates?.let { return it }  
  
        val dir = File(context.filesDir, ICON_DIR)  
        if (!dir.exists()) return emptyMap()  
  
        val result = mutableMapOf<Int, Bitmap>()  
        val files = dir.listFiles() ?: return emptyMap()  
  
        for (file in files) {  
            val name = file.nameWithoutExtension  
            val parts = name.split("_")  
            if (parts.size != 2) continue  
  
            val baseId = parts[0].toIntOrNull() ?: continue  
            val star = parts[1].toIntOrNull() ?: continue  
  
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
     * 优先使用 OpenCV 轮廓检测自动定位头像，失败时回退到固定比例裁剪。  
     */  
    fun recognize(screenshot: Bitmap, screenW: Int, screenH: Int): List<Int> {  
        val tmpl = loadTemplates()  
        if (tmpl.isEmpty()) {  
            Log.w(TAG, "本地头像库为空，请先下载角色头像")  
            return emptyList()  
        }  
  
        // 优先尝试 OpenCV 轮廓检测  
        var crops = if (ensureOpenCV()) {  
            cropByContourDetection(screenshot)  
        } else {  
            emptyList()  
        }  
  
        // 回退到固定比例裁剪  
        if (crops.isEmpty()) {  
            Log.i(TAG, "OpenCV 轮廓检测未找到头像，回退到固定比例裁剪")  
            crops = cropByFixedRatio(screenshot, screenW, screenH)  
        }  
  
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
  
    // ======================== 方案A: OpenCV 轮廓检测 ========================  
  
    /**  
     * 数据类：检测到的正方形区域  
     */  
    private data class SquareRegion(  
        val sideLength: Int,  
        val x: Int,  
        val y: Int,  
        val w: Int,  
        val h: Int  
    )  
  
    /**  
     * 使用 OpenCV 轮廓检测自动定位头像。  
     * 移植自 arena 的 cutting(img, mode=2) 算法：  
     * 1. 灰度 + 二值化（阈值 210）  
     * 2. findContours 提取外轮廓  
     * 3. 过滤近似正方形、面积占比 >= 0.5%  
     * 4. 按边长聚类（容差 10%）  
     * 5. 优先返回恰好 5 个或 5 的倍数个的聚类  
     *  
     * @return 裁剪出的头像 Bitmap 列表（最多5个），按 x 坐标从左到右排序  
     */  
    private fun cropByContourDetection(screenshot: Bitmap): List<Bitmap> {  
        val mat = Mat()  
        Utils.bitmapToMat(screenshot, mat)  
  
        val grey = Mat()  
        Imgproc.cvtColor(mat, grey, Imgproc.COLOR_RGBA2GRAY)  
  
        // 二值化：> 210 → 255, 否则 → 0（与 arena 一致）  
        val binary = Mat()  
        Imgproc.threshold(grey, binary, BINARY_THRESHOLD, 255.0, Imgproc.THRESH_BINARY)  
  
        val totalArea = binary.rows() * binary.cols()  
  
        // 查找外轮廓  
        val contours = mutableListOf<MatOfPoint>()  
        val hierarchy = Mat()  
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE)  
  
        Log.d(TAG, "OpenCV 检测到 ${contours.size} 个轮廓")  
  
        // 过滤近似正方形  
        val squares = mutableListOf<SquareRegion>()  
        for (contour in contours) {  
            val area = Imgproc.contourArea(contour)  
            if (area <= MIN_CONTOUR_AREA) continue  
  
            val rect = Imgproc.boundingRect(contour)  
            val ratio = rect.width.toFloat() / rect.height  
            if (ratio < SQUARE_RATIO_MIN || ratio > SQUARE_RATIO_MAX) continue  
  
            val sideLen = (rect.width + rect.height) / 2  
            val areaPercent = sideLen.toFloat() * sideLen / totalArea * 100  
            if (areaPercent < MIN_AREA_PERCENT) continue  
  
            squares.add(SquareRegion(sideLen, rect.x, rect.y, rect.width, rect.height))  
        }  
  
        Log.d(TAG, "过滤后近似正方形: ${squares.size} 个")  
  
        // 释放 Mat  
        mat.release()  
        grey.release()  
        binary.release()  
        hierarchy.release()  
        contours.forEach { it.release() }  
  
        if (squares.isEmpty()) return emptyList()  
  
        // 按边长聚类（arena 的聚类算法）  
        val clusters = mutableMapOf<Int, MutableList<SquareRegion>>()  
        for (sq in squares) {  
            var matched = false  
            for (key in clusters.keys) {  
                val ratio = sq.sideLength.toFloat() / key  
                if (ratio > (1 - CLUSTER_TOLERANCE) && ratio < (1 + CLUSTER_TOLERANCE)) {  
                    clusters[key]!!.add(sq)  
                    matched = true  
                    break  
                }  
            }  
            if (!matched) {  
                clusters[sq.sideLength] = mutableListOf(sq)  
            }  
        }  
  
        Log.d(TAG, "聚类结果: ${clusters.map { "${it.key}px -> ${it.value.size}个" }}")  
  
        // 排序：优先 5 个 > 5 的倍数 > 其他（第二关键字为边长从大到小）  
        val sorted = clusters.entries.sortedByDescending { (sideLen, members) ->  
            val count = members.size  
            when {  
                count == 5 -> 5_000_000 + sideLen  
                count % 5 == 0 -> 1_000_000 + sideLen  
                else -> count * 10_000 + sideLen  
            }  
        }  
  
        val bestCluster = sorted.first().value  
  
        // 如果最佳聚类不是 5 的倍数，可能不是头像  
        if (bestCluster.size < 4) {  
            Log.w(TAG, "最佳聚类只有 ${bestCluster.size} 个，可能不是头像区域")  
            return emptyList()  
        }  
  
        // 取前5个（按 x 坐标排序，从左到右）  
        val selected = bestCluster.sortedBy { it.x }.take(5)  
        Log.i(TAG, "选取 ${selected.size} 个头像区域: ${selected.map { "(${it.x},${it.y},${it.w}x${it.h})" }}")  
  
        // 裁剪  
        val crops = mutableListOf<Bitmap>()  
        for (sq in selected) {  
            // 内缩 2px 去掉白边（与 arena 的 img[y+2:y+h-2, x+2:x+w-2] 一致）  
            val x = (sq.x + 2).coerceAtLeast(0)  
            val y = (sq.y + 2).coerceAtLeast(0)  
            val w = (sq.w - 4).coerceAtMost(screenshot.width - x).coerceAtLeast(1)  
            val h = (sq.h - 4).coerceAtMost(screenshot.height - y).coerceAtLeast(1)  
  
            try {  
                val crop = Bitmap.createBitmap(screenshot, x, y, w, h)  
                crops.add(crop)  
            } catch (e: Exception) {  
                Log.w(TAG, "裁剪失败: x=$x y=$y w=$w h=$h", e)  
            }  
        }  
  
        return crops  
    }  
  
    // ======================== 方案B: 固定比例裁剪（回退方案） ========================  
  
    /**  
     * 使用固定比例裁剪（带 16:9 游戏区域适配）。  
     * 当 OpenCV 轮廓检测失败时作为回退。  
     */  
    private fun cropByFixedRatio(screenshot: Bitmap, screenW: Int, screenH: Int): List<Bitmap> {  
        val screenRatio = screenW.toFloat() / screenH  
        val gameW: Int  
        val gameH: Int  
        val offsetX: Int  
        val offsetY: Int  
  
        if (screenRatio > GAME_ASPECT_RATIO) {  
            gameH = screenH  
            gameW = (screenH * GAME_ASPECT_RATIO).toInt()  
            offsetX = (screenW - gameW) / 2  
            offsetY = 0  
        } else if (screenRatio < GAME_ASPECT_RATIO) {  
            gameW = screenW  
            gameH = (screenW / GAME_ASPECT_RATIO).toInt()  
            offsetX = 0  
            offsetY = (screenH - gameH) / 2  
        } else {  
            gameW = screenW  
            gameH = screenH  
            offsetX = 0  
            offsetY = 0  
        }  
  
        Log.d(TAG, "回退裁剪 - 屏幕: ${screenW}x${screenH}, 游戏区域: ${gameW}x${gameH}, 偏移: ($offsetX, $offsetY)")  
  
        val iconCenterYRatio = 0.24f  
        val iconSizeRatio = 0.058f  
        val iconStartXRatio = 0.30f  
        val iconEndXRatio = 0.70f  
  
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
                    Log.w(TAG, "回退裁剪位置$i 失败: left=$left top=$top w=$w h=$h", e)  
                }  
            }  
        }  
  
        return crops  
    }  
  
    // ======================== NCC 模板匹配（不变） ========================  
  
    private fun findBestMatch(candidate: Bitmap, templates: Map<Int, Bitmap>): Pair<Int, Double>? {  
        var bestId = -1  
        var bestScore = -1.0  
  
        val candPixels = getPixelArray(candidate)  
        val candMean = candPixels.average()  
        val candStd = stdDev(candPixels, candMean)  
  
        if (candStd < 1.0) return null  
  
        for ((baseId, tmplBmp) in templates) {  
            val tmplPixels = getPixelArray(tmplBmp)  
            val tmplMean = tmplPixels.average()  
            val tmplStd = stdDev(tmplPixels, tmplMean)  
  
            if (tmplStd < 1.0) continue  
  
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
  
        return if (bestScore >= MATCH_THRESHOLD) Pair(bestId, bestScore) else null  
    }  
  
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