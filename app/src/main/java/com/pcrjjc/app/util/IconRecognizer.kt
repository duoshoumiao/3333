package com.pcrjjc.app.util  
  
import android.content.Context  
import android.graphics.Bitmap  
import android.graphics.BitmapFactory  
import android.graphics.Canvas  
import android.graphics.Color  
import android.graphics.Paint  
import android.graphics.Rect  
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
import kotlin.math.max  
import kotlin.math.sqrt  
  
/**  
 * 角色头像识别器。  
 * 使用 OpenCV 轮廓检测从截图中自动定位角色头像（移植自 arena 的 cutting + getPos 算法），  
 * 再与本地头像库做 NCC 模板匹配，返回识别到的角色 baseId 列表。  
 */  
class IconRecognizer(private val context: Context) {  
  
    companion object {  
        private const val TAG = "IconRecognizer"  
        private const val TEMPLATE_SIZE = 64  
        private const val MATCH_THRESHOLD = 0.70  
        private const val ICON_DIR = "icons/unit"  
        private const val GAME_ASPECT_RATIO = 16f / 9f  
  
        // OpenCV 轮廓检测参数（对应 arena 的 cutting mode=2）  
        private const val BINARY_THRESHOLD = 210.0  
        private const val MIN_CONTOUR_AREA = 500.0  
        private const val SQUARE_RATIO_MIN = 0.95  
        private const val SQUARE_RATIO_MAX = 1.05  
        private const val MIN_AREA_PERCENT = 0.5  
        private const val CLUSTER_TOLERANCE = 0.10  
  
        // 迭代检测最大次数（对应 arena getPos 的 while cnt <= 6）  
        private const val MAX_ITERATIONS = 6  
  
        @Volatile  
        private var opencvInitialized = false  
    }  
  
    // ======================== 数据类 ========================  
  
    /** 识别结果，包含调试图片 */  
    data class RecognitionResult(  
        val recognizedIds: List<Int>,  
        val debugBitmap: Bitmap?,     // 标注了检测区域的截图（颜色含义见 highlight）  
        val compareBitmap: Bitmap?    // 上下对比图（裁出的头像 vs 匹配的模板头像）  
    )  
  
    /** 检测到的正方形区域 */  
    private data class SquareRegion(  
        val sideLength: Int,  
        val x: Int, val y: Int, val w: Int, val h: Int  
    )  
  
    /** findSquares 的返回值 */  
    private data class CuttingSquaresResult(  
        val border: List<SquareRegion>,       // 主聚类或全部正方形  
        val otherBorder: List<SquareRegion>   // 被排除的其它聚类  
    )  
  
    // ======================== 模板缓存 ========================  
  
    private var templates: Map<Int, Bitmap>? = null  
  
    fun getTemplateCount(): Int = loadTemplates().size  
  
    private fun ensureOpenCV(): Boolean {  
        if (opencvInitialized) return true  
        opencvInitialized = OpenCVLoader.initLocal()  
        if (!opencvInitialized) Log.e(TAG, "OpenCV 初始化失败")  
        else Log.i(TAG, "OpenCV 初始化成功: ${OpenCVLoader.OPENCV_VERSION}")  
        return opencvInitialized  
    }  
  
    private fun loadTemplates(): Map<Int, Bitmap> {  
        templates?.let { return it }  
        val dir = File(context.filesDir, ICON_DIR)  
        if (!dir.exists()) return emptyMap()  
        val result = mutableMapOf<Int, Bitmap>()  
        val files = dir.listFiles() ?: return emptyMap()  
        for (file in files) {  
            val parts = file.nameWithoutExtension.split("_")  
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
  
    // ======================== 主入口 ========================  
  
    /**  
     * 从截图中识别防守阵容角色。  
     * 优先使用 OpenCV 迭代轮廓检测（移植自 arena getPos），失败时回退到固定比例裁剪。  
     * 返回 RecognitionResult，包含识别到的 baseId 列表和调试图片。  
     */  
    fun recognize(screenshot: Bitmap, screenW: Int, screenH: Int): RecognitionResult {  
        val tmpl = loadTemplates()  
        if (tmpl.isEmpty()) {  
            Log.w(TAG, "本地头像库为空")  
            return RecognitionResult(emptyList(), null, null)  
        }  
  
        // 优先尝试 OpenCV 迭代轮廓检测  
        if (ensureOpenCV()) {  
            val result = cropByIterativeDetection(screenshot, tmpl)  
            if (result.recognizedIds.isNotEmpty()) return result  
            // 迭代检测失败，保留调试图，回退到固定比例裁剪  
            val crops = cropByFixedRatio(screenshot, screenW, screenH)  
            if (crops.isNotEmpty()) {  
                val ids = matchCrops(crops, tmpl)  
                crops.forEach { it.recycle() }  
                if (ids.isNotEmpty()) return RecognitionResult(ids, result.debugBitmap, null)  
            }  
            // 全部失败，返回调试图供用户排查  
            return RecognitionResult(emptyList(), result.debugBitmap, null)  
        }  
  
        // OpenCV 不可用，直接固定比例裁剪  
        val crops = cropByFixedRatio(screenshot, screenW, screenH)  
        if (crops.isEmpty()) return RecognitionResult(emptyList(), null, null)  
        val ids = matchCrops(crops, tmpl)  
        crops.forEach { it.recycle() }  
        return RecognitionResult(ids, null, null)  
    }  
  
    /** 对裁剪出的头像列表做模板匹配，返回识别到的 baseId 列表 */  
    private fun matchCrops(crops: List<Bitmap>, tmpl: Map<Int, Bitmap>): List<Int> {  
        val result = mutableListOf<Int>()  
        for ((index, crop) in crops.withIndex()) {  
            val scaled = Bitmap.createScaledBitmap(crop, TEMPLATE_SIZE, TEMPLATE_SIZE, true)  
            val match = findBestMatch(scaled, tmpl)  
            if (crop != scaled) scaled.recycle()  
            if (match != null) {  
                Log.i(TAG, "位置$index: baseId=${match.first}, 相似度=${"%.3f".format(match.second)}")  
                result.add(match.first)  
            } else {  
                Log.w(TAG, "位置$index: 未匹配")  
            }  
        }  
        return result  
    }  
  
    // ======================== cutting mode=2: 找正方形 ========================  
  
    /**  
     * 对应 arena 的 cutting(img, mode=2)：  
     * 灰度图二值化(>210) → findContours → 过滤近似正方形 → 按边长聚类。  
     * 若存在5的倍数聚类则返回该类，否则返回所有正方形（对应 arena old_main.py 第268行）。  
     */  
    private fun findSquares(greyMat: Mat): CuttingSquaresResult {  
        val binary = Mat()  
        Imgproc.threshold(greyMat, binary, BINARY_THRESHOLD, 255.0, Imgproc.THRESH_BINARY)  
        val totalArea = binary.rows() * binary.cols()  
        val contours = mutableListOf<MatOfPoint>()  
        val hierarchy = Mat()  
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE)  
  
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
        binary.release(); hierarchy.release(); contours.forEach { it.release() }  
        Log.d(TAG, "findSquares: ${squares.size} 个正方形")  
  
        if (squares.isEmpty()) return CuttingSquaresResult(emptyList(), emptyList())  
  
        // 按边长聚类（对应 arena 的聚类算法，容差10%）  
        val clusters = mutableMapOf<Int, MutableList<SquareRegion>>()  
        for (sq in squares) {  
            var matched = false  
            for (key in clusters.keys) {  
                val r = sq.sideLength.toFloat() / key  
                if (r > (1 - CLUSTER_TOLERANCE) && r < (1 + CLUSTER_TOLERANCE)) {  
                    clusters[key]!!.add(sq); matched = true; break  
                }  
            }  
            if (!matched) clusters[sq.sideLength] = mutableListOf(sq)  
        }  
        Log.d(TAG, "聚类: ${clusters.map { "${it.key}px->${it.value.size}个" }}")  
  
        // 排序：优先5个 > 5的倍数 > 其他（第二关键字为边长从大到小）  
        val sorted = clusters.entries.sortedByDescending { (sideLen, members) ->  
            val c = members.size  
            when {  
                c == 5 -> 5_000_000 + sideLen  
                c % 5 == 0 -> 1_000_000 + sideLen  
                else -> c * 10_000 + sideLen  
            }  
        }  
        val best = sorted.first()  
        return if (best.value.size % 5 == 0) {  
            // 存在5的倍数聚类，返回该类，其余为 otherBorder  
            val other = sorted.drop(1).flatMap { it.value }  
            CuttingSquaresResult(best.value, other)  
        } else {  
            // 不是5的倍数，返回所有正方形，由后续逻辑判断（对应 arena old_main.py 第268-269行）  
            CuttingSquaresResult(squares, emptyList())  
        }  
    }  
  
    // ======================== cutting mode=1: 找最大矩形 ========================  
  
    /**  
     * 对应 arena 的 cutting(img, mode=1)：  
     * 找到图像中面积最大的轮廓，返回其 boundingRect [x, y, w, h]。  
     */  
    private fun findLargestRect(greyMat: Mat): IntArray? {  
        val binary = Mat()  
        Imgproc.threshold(greyMat, binary, BINARY_THRESHOLD, 255.0, Imgproc.THRESH_BINARY)  
        val contours = mutableListOf<MatOfPoint>()  
        val hierarchy = Mat()  
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE)  
        if (contours.isEmpty()) { binary.release(); hierarchy.release(); return null }  
  
        var maxArea = -1.0; var maxIdx = 0  
        for (i in contours.indices) {  
            val a = Imgproc.contourArea(contours[i])  
            if (a > maxArea) { maxArea = a; maxIdx = i }  
        }  
        val rect = Imgproc.boundingRect(contours[maxIdx])  
        binary.release(); hierarchy.release(); contours.forEach { it.release() }  
        return intArrayOf(rect.x, rect.y, rect.width, rect.height)  
    }  
  
    // ======================== 迭代检测主流程（移植自 arena getPos）========================  
  
    /**  
     * 迭代轮廓检测，移植自 arena 的 getPos() 函数 (old_main.py:285-416)。  
     *  
     * 流程（最多循环 MAX_ITERATIONS 次）：  
     * 1. findSquares (cutting mode=2) 找正方形  
     * 2. 找不到 → 标记 needInvert  
     * 3. 找到 >=4 个 → 行列分组 + 角色匹配 → 生成调试图 + 对比图 → 返回  
     * 4. findLargestRect (cutting mode=1) 找最大矩形，裁剪进去  
     * 5. 第一次迭代或 needInvert → 反色灰度图  
     * 6. 重复  
     *  
     * 调试图颜色含义（与 arena highlight 一致）：  
     *   红色 = 识别成功, 蓝色 = 被排除, 绿色 = 超出5列, 黑色 = 匹配失败, 黄色 = baseId=1000  
     */  
    private fun cropByIterativeDetection(  
        screenshot: Bitmap,  
        tmpl: Map<Int, Bitmap>  
    ): RecognitionResult {  
        val origW = screenshot.width  
        val origH = screenshot.height  
  
        // --- 调试图初始化（对应 arena 的 outpImg + outpImgText）---  
        val debugBmp = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)  
        val debugCanvas = Canvas(debugBmp)  
        debugCanvas.drawColor(Color.BLACK)  
  
        val strokePaint = Paint().apply {  
            style = Paint.Style.STROKE  
            strokeWidth = 4f  
            isAntiAlias = false  
        }  
        val fillPaint = Paint().apply { style = Paint.Style.FILL }  
  
        // --- 转灰度 Mat ---  
        val srcMat = Mat()  
        Utils.bitmapToMat(screenshot, srcMat)  
        val fullGrey = Mat()  
        Imgproc.cvtColor(srcMat, fullGrey, Imgproc.COLOR_RGBA2GRAY)  
        srcMat.release()  
  
        // --- 工作变量 ---  
        var currentGrey = fullGrey       // 当前灰度 Mat（会被裁剪/反色）  
        var currentBmp = screenshot      // 当前 RGBA Bitmap（同步裁剪）  
        var actualX = 0                  // 累计偏移（映射回原图坐标）  
        var actualY = 0  
        var nowColor = 0  
  
        var cnt = 0  
        while (cnt <= MAX_ITERATIONS) {  
            var needInvert = false  
            cnt++  
  
            // Step 1: 找正方形 (cutting mode=2)  
            val sqResult = findSquares(currentGrey)  
            val border = sqResult.border  
            val otherBorder = sqResult.otherBorder  
  
            if (border.isEmpty()) {  
                needInvert = true  
                Log.d(TAG, "迭代$cnt: 无正方形，标记反色")  
            } else {  
                // 标注被排除的聚类（蓝色）  
                strokePaint.color = Color.BLUE  
                for (sq in otherBorder) {  
                    debugCanvas.drawRect(  
                        (actualX + sq.x).toFloat(),  
                        (actualY + sq.y).toFloat(),  
                        (actualX + sq.x + sq.w).toFloat(),  
                        (actualY + sq.y + sq.h).toFloat(),  
                        strokePaint  
                    )  
                }  
  
                if (border.size < 4) {  
                    // 不足4个，标蓝色，继续迭代  
                    strokePaint.color = Color.BLUE  
                    for (sq in border) {  
                        debugCanvas.drawRect(  
                            (actualX + sq.x).toFloat(),  
                            (actualY + sq.y).toFloat(),  
                            (actualX + sq.x + sq.w).toFloat(),  
                            (actualY + sq.y + sq.h).toFloat(),  
                            strokePaint  
                        )  
                    }  
                    Log.d(TAG, "迭代$cnt: 正方形仅${border.size}个(<4)，继续")  
                } else {  
                    // >=4 个正方形 → 行列分组 + 角色匹配  
                    Log.i(TAG, "迭代$cnt: 找到 ${border.size} 个正方形，开始匹配")  
  
                    val result = processSquares(  
                        border, currentBmp, tmpl,  
                        actualX, actualY, debugCanvas, strokePaint  
                    )  
                    if (result != null) {  
                        // 混合调试图（对应 arena: Image.blend(outpImg, actual_img, 0.2)）  
                        val blendPaint = Paint().apply { alpha = 50 }  
                        debugCanvas.drawBitmap(screenshot, 0f, 0f, blendPaint)  
  
                        // 缩放调试图到最大500px  
                        val ratio = max(1f, max(origW, origH).toFloat() / 500f)  
                        val finalDebug = Bitmap.createScaledBitmap(  
                            debugBmp,  
                            (origW / ratio).toInt(),  
                            (origH / ratio).toInt(),  
                            true  
                        )  
  
                        // 释放 Mat  
                        if (currentGrey !== fullGrey) currentGrey.release()  
                        fullGrey.release()  
  
                        return RecognitionResult(  
                            result.first,  
                            finalDebug,  
                            result.second  
                        )  
                    }  
                    // processSquares 返回 null 说明有效行太少，继续迭代  
                }  
            }  
  
            // Step 4: 找最大矩形，裁剪进去 (cutting mode=1)  
            val rect = try {  
                findLargestRect(currentGrey)  
            } catch (_: Exception) {  
                null  
            }  
            if (rect == null) {  
                Log.w(TAG, "迭代$cnt: 找不到最大矩形，终止")  
                break  
            }  
            val rx = rect[0]; val ry = rect[1]; val rw = rect[2]; val rh = rect[3]  
  
            // 裁剪灰度图（内缩2px，对应 arena: img2[y+2:y+h-2, x+2:x+w-2]）  
            val cx = (rx + 2).coerceAtLeast(0)  
            val cy = (ry + 2).coerceAtLeast(0)  
            val cw = (rw - 4).coerceAtMost(currentGrey.cols() - cx).coerceAtLeast(1)  
            val ch = (rh - 4).coerceAtMost(currentGrey.rows() - cy).coerceAtLeast(1)  
            val croppedGrey = currentGrey.submat(cy, cy + ch, cx, cx + cw).clone()  
            if (currentGrey !== fullGrey) currentGrey.release()  
  
            // Step 5: 反色（对应 arena: if cnt == 1 or bo: im_grey.point(lambda x: 0 if x > 128 else 255)）  
            if (cnt == 1 || needInvert) {  
                Imgproc.threshold(croppedGrey, croppedGrey, 128.0, 255.0, Imgproc.THRESH_BINARY_INV)  
                Log.d(TAG, "迭代$cnt: 已反色")  
            }  
            currentGrey = croppedGrey  
  
            // 同步裁剪 RGBA Bitmap（对应 arena: img = await cut(img, border)）  
            val bx = (rx + 2).coerceAtLeast(0)  
            val by = (ry + 2).coerceAtLeast(0)  
            val bw = (rw - 4).coerceAtMost(currentBmp.width - bx).coerceAtLeast(1)  
            val bh = (rh - 4).coerceAtMost(currentBmp.height - by).coerceAtLeast(1)  
            try {  
                currentBmp = Bitmap.createBitmap(currentBmp, bx, by, bw, bh)  
            } catch (e: Exception) {  
                Log.w(TAG, "迭代$cnt: Bitmap裁剪失败", e)  
                break  
            }  
  
            actualX += rx  
            actualY += ry  
  
            // 在调试图上标记本次裁剪区域（对应 arena: outpImg.paste(rgb(nowcolor,...))）  
            nowColor = (nowColor + 60) % 300  
            fillPaint.color = Color.rgb(nowColor, nowColor, nowColor)  
            debugCanvas.drawRect(  
                actualX.toFloat(), actualY.toFloat(),  
                (actualX + rw).toFloat(), (actualY + rh).toFloat(),  
                fillPaint  
            )  
        }  
  
        // 循环结束仍未识别 → 返回空结果 + 调试图  
        if (currentGrey !== fullGrey) currentGrey.release()  
        fullGrey.release()  
  
        // 将原始截图混合到调试图上，让用户能看到实际截图内容  
        val blendPaint = Paint().apply { alpha = 50 }  
        debugCanvas.drawBitmap(screenshot, 0f, 0f, blendPaint)  
  
        val ratio = max(1f, max(origW, origH).toFloat() / 500f)  
        val finalDebug = Bitmap.createScaledBitmap(  
            debugBmp, (origW / ratio).toInt(), (origH / ratio).toInt(), true  
        )  
        return RecognitionResult(emptyList(), finalDebug, null)  
    }  
  
    // ======================== 行列分组 + 角色匹配 ========================  
  
    /**  
     * 对检测到的正方形做行列分组和角色匹配。  
     * 移植自 arena getPos 中 split_last_col_recs + 匹配循环 + compare_img 生成。  
     *  
     * @return Pair(识别到的baseId列表, 对比图) 或 null（有效行太少）  
     */  
    private fun processSquares(  
        border: List<SquareRegion>,  
        currentBmp: Bitmap,  
        tmpl: Map<Int, Bitmap>,  
        actualX: Int,  
        actualY: Int,  
        debugCanvas: Canvas,  
        strokePaint: Paint  
    ): Pair<List<Int>, Bitmap>? {  
  
        // --- split_last_col_recs: 从右往左一列一列掰 ---  
        fun splitLastCol(  
            input: List<SquareRegion>  
        ): Pair<List<SquareRegion>, List<SquareRegion>> {  
            if (input.isEmpty()) return Pair(emptyList(), emptyList())  
            val sorted = input.sortedByDescending { it.x }  
            val refW = sorted[0].w  
            val lastCol = sorted  
                .filter { abs(it.x - sorted[0].x) < refW / 2 }  
                .sortedBy { it.y }  
            val remaining = input.filter { sq -> lastCol.none { it === sq } }  
            return Pair(remaining, lastCol)  
        }  
  
        // 先找最右一列，确定行数  
        val (_, initLastCol) = splitLastCol(border)  
        val rowCnt = initLastCol.size  
        if (rowCnt == 0) return null  
  
        // arr[row][col] 存放该位置的正方形, arrId[row] 存放该行识别到的 baseId  
        val arr = Array(rowCnt) { arrayOfNulls<SquareRegion>(5) }  
        val arrId = Array(rowCnt) { mutableListOf<Int>() }  
        val lastColYPos = initLastCol.map { it.y }  // 以最右一列的 y 坐标为基准  
  
        var remaining: List<SquareRegion> = border.toList()  
  
        for (colIdx in 0 until 5) {  
            val (newRem, colRecs) = splitLastCol(remaining)  
            remaining = newRem  
            if (colRecs.isEmpty()) break  
  
            for (sq in colRecs) {  
                // 内缩2px裁剪头像  
                val cx = (sq.x + 2).coerceAtLeast(0)  
                val cy = (sq.y + 2).coerceAtLeast(0)  
                val cw = (sq.w - 4).coerceAtMost(currentBmp.width - cx).coerceAtLeast(1)  
                val ch = (sq.h - 4).coerceAtMost(currentBmp.height - cy).coerceAtLeast(1)  
  
                val cropped: Bitmap  
                try {  
                    cropped = Bitmap.createBitmap(currentBmp, cx, cy, cw, ch)  
                } catch (e: Exception) {  
                    Log.w(TAG, "裁剪失败: x=$cx y=$cy w=$cw h=$ch", e)  
                    strokePaint.color = Color.BLACK  
                    debugCanvas.drawRect(  
                        (actualX + sq.x).toFloat(), (actualY + sq.y).toFloat(),  
                        (actualX + sq.x + sq.w).toFloat(), (actualY + sq.y + sq.h).toFloat(),  
                        strokePaint  
                    )  
                    continue  
                }  
  
                // NCC 模板匹配  
                val scaled = Bitmap.createScaledBitmap(cropped, TEMPLATE_SIZE, TEMPLATE_SIZE, true)  
                val match = findBestMatch(scaled, tmpl)  
                if (cropped != scaled) scaled.recycle()  
  
                // 贴裁出的头像到调试图（对应 arena highlight 中的 outpImgText.paste）  
                debugCanvas.drawBitmap(  
                    cropped, null,  
                    Rect(  
                        actualX + cx, actualY + cy,  
                        actualX + cx + cw, actualY + cy + ch  
                    ),  
                    null  
                )  
  
                if (match == null) {  
                    // 识别不出角色 → 黑色框  
                    strokePaint.color = Color.BLACK  
                    debugCanvas.drawRect(  
                        (actualX + sq.x).toFloat(), (actualY + sq.y).toFloat(),  
                        (actualX + sq.x + sq.w).toFloat(), (actualY + sq.y + sq.h).toFloat(),  
                        strokePaint  
                    )  
                    cropped.recycle()  
                } else {  
                    val baseId = match.first  
                    // 识别成功 → 红色框（baseId=1000 时黄色）  
                    strokePaint.color = if (baseId != 1000) Color.RED else Color.YELLOW  
                    debugCanvas.drawRect(  
                        (actualX + sq.x).toFloat(), (actualY + sq.y).toFloat(),  
                        (actualX + sq.x + sq.w).toFloat(), (actualY + sq.y + sq.h).toFloat(),  
                        strokePaint  
                    )  
                    cropped.recycle()  
  
                    // 找最近的行（对应 arena: most_near_row）  
                    var nearRow = 0  
                    for (ri in 1 until rowCnt) {  
                        if (abs(lastColYPos[ri] - sq.y) < abs(lastColYPos[nearRow] - sq.y)) {  
                            nearRow = ri  
                        }  
                    }  
                    val existing = arr[nearRow][colIdx]  
                    if (existing == null ||  
                        abs(lastColYPos[nearRow] - existing.y) > abs(lastColYPos[nearRow] - sq.y)  
                    ) {  
                        arr[nearRow][colIdx] = sq  
                        arrId[nearRow].add(baseId)  
                    }  
                }  
            }  
        }  
  
        // 超出5列的标绿色  
        strokePaint.color = Color.GREEN  
        for (sq in remaining) {  
            debugCanvas.drawRect(  
                (actualX + sq.x).toFloat(), (actualY + sq.y).toFloat(),  
                (actualX + sq.x + sq.w).toFloat(), (actualY + sq.y + sq.h).toFloat(),  
                strokePaint  
            )  
        }  
  
        // --- 生成对比图 compare_img ---  
        val iconSz = 64  
        val cmpW = iconSz * 5 + 16 * 2  
        val cmpH = iconSz * 2 * rowCnt + 16 * (rowCnt + 1)  
        val cmpBmp = Bitmap.createBitmap(cmpW, cmpH, Bitmap.Config.ARGB_8888)  
        val cmpCanvas = Canvas(cmpBmp)  
        cmpCanvas.drawColor(Color.WHITE)  
  
        val allIds = mutableListOf<Int>()  
  
        for (ri in 0 until rowCnt) {  
            val noneCnt = arr[ri].count { it == null }  
            if (noneCnt >= 2) {  
                // 不允许1-3个角色查询，不渲染（对应 arena: none_cnt >= 2）  
                arrId[ri].clear()  
                continue  
            }  
            for (ci in 0 until 5) {  
                // 从右往左存的，渲染时反转（对应 arena: arr[row_index][4 - col_index]）  
                val sq = arr[ri][4 - ci] ?: continue  
                val px = 16 + iconSz * ci  
                val py = 16 * (ri + 1) + iconSz * 2 * ri  
  
                // 上面：裁出的头像（64x64）  
                val cx2 = (sq.x + 2).coerceAtLeast(0)  
                val cy2 = (sq.y + 2).coerceAtLeast(0)  
                val cw2 = (sq.w - 4).coerceAtMost(currentBmp.width - cx2).coerceAtLeast(1)  
                val ch2 = (sq.h - 4).coerceAtMost(currentBmp.height - cy2).coerceAtLeast(1)  
                try {  
                    val crop = Bitmap.createBitmap(currentBmp, cx2, cy2, cw2, ch2)  
                    val resized = Bitmap.createScaledBitmap(crop, iconSz, iconSz, true)  
                    cmpCanvas.drawBitmap(resized, px.toFloat(), py.toFloat(), null)  
                    if (crop != resized) resized.recycle()  
                    crop.recycle()  
                } catch (_: Exception) {  
                }  
  
                // 下面：通过 baseId 从本地加载的标准头像（64x64）  
                if (ci < arrId[ri].size) {  
                    val bid = arrId[ri][ci]  
                    val path = IconStorage.getIconPath(context, bid, 6)  
                        ?: IconStorage.getIconPath(context, bid, 3)  
                    if (path != null) {  
                        try {  
                            val icon = BitmapFactory.decodeFile(path)  
                            if (icon != null) {  
                                val ri2 = Bitmap.createScaledBitmap(icon, iconSz, iconSz, true)  
                                cmpCanvas.drawBitmap(ri2, px.toFloat(), (py + iconSz).toFloat(), null)  
                                if (icon != ri2) ri2.recycle()  
                                icon.recycle()  
                            }  
                        } catch (_: Exception) {  
                        }  
                    }  
                }  
            }  
            allIds.addAll(arrId[ri])  
        }  
  
        if (allIds.isEmpty()) return null  
        return Pair(allIds, cmpBmp)  
    }  
  
    // ======================== 方案B: 固定比例裁剪（回退方案，不变） ========================  
  
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
  
    /**  
     * 在所有模板中找 NCC 最高且超过阈值的匹配。  
     * @return Pair(baseId, nccScore) 或 null  
     */  
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
  
    /** 将 Bitmap 转为灰度亮度数组 */  
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
  
    /** 标准差 */  
    private fun stdDev(arr: DoubleArray, mean: Double): Double {  
        var sum = 0.0  
        for (v in arr) {  
            val d = v - mean  
            sum += d * d  
        }  
        return sqrt(sum / arr.size)  
    }  
}