package com.pcrjjc.app.util  
  
import android.content.Context  
import android.graphics.Bitmap  
import android.graphics.BitmapFactory  
import android.graphics.Canvas  
import android.graphics.Color  
import android.graphics.Paint  
import android.util.Log  
import org.opencv.android.OpenCVLoader  
import org.opencv.android.Utils  
import org.opencv.core.Mat  
import org.opencv.core.MatOfPoint  
import org.opencv.imgproc.Imgproc  
import java.io.File  
import kotlin.math.abs  
import kotlin.math.max  
  
/**  
 * 角色头像识别器。  
 * 使用 OpenCV 迭代轮廓检测从截图中自动定位角色头像（移植自 arena 的 getPos 算法），  
 * 再与本地头像库做 dHash 感知哈希匹配（移植自 arena 的 getUnit 算法），  
 * 返回识别到的角色 baseId 列表及调试图片。  
 */  
class IconRecognizer(private val context: Context) {  
  
    companion object {  
        private const val TAG = "IconRecognizer"  
        private const val ICON_DIR = "icons/unit"  
        private const val GAME_ASPECT_RATIO = 16f / 9f  
  
        // dHash 参数（对应 arena 的 cut_image / difference_value / getUnit）  
        private const val HASH_SIZE = 16            // dHash 尺寸 → 16x16 = 256 位  
        private const val DHASH_THRESHOLD = 90      // 汉明距离阈值（arena: similarity > 90 → Unknown）  
  
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
        val debugBitmap: Bitmap?,     // 标注了检测区域的截图  
        val compareBitmap: Bitmap?    // 上下对比图（裁出的头像 vs 匹配的模板头像）  
    )  
  
    /** 检测到的正方形区域 */  
    private data class SquareRegion(  
        val sideLength: Int,  
        val x: Int, val y: Int, val w: Int, val h: Int  
    )  
  
    /** findSquares 的返回值 */  
    private data class CuttingSquaresResult(  
        val border: List<SquareRegion>,  
        val otherBorder: List<SquareRegion>  
    )  
  
    // ======================== 模板哈希缓存 ========================  
  
    /** 本地头像模板 dHash 缓存: baseId -> IntArray(256) */  
    private var templateHashes: Map<Int, IntArray>? = null  
  
    /** 获取已加载的模板数量 */  
    fun getTemplateCount(): Int = loadTemplateHashes().size  
  
    /** 确保 OpenCV 已初始化 */  
    private fun ensureOpenCV(): Boolean {  
        if (opencvInitialized) return true  
        opencvInitialized = OpenCVLoader.initLocal()  
        if (!opencvInitialized) Log.e(TAG, "OpenCV 初始化失败")  
        else Log.i(TAG, "OpenCV 初始化成功: ${OpenCVLoader.OPENCV_VERSION}")  
        return opencvInitialized  
    }  
  
    /**  
     * 加载本地头像库并预计算 dHash。  
     * 对应 arena 的 process_data()：  
     *   data_processed[uid] = get_hash_arr(Image.fromarray(data[uid][25:96, 8:97, :]))  
     */  
    private fun loadTemplateHashes(): Map<Int, IntArray> {  
        templateHashes?.let { return it }  
  
        val dir = File(context.filesDir, ICON_DIR)  
        if (!dir.exists()) return emptyMap()  
  
        val result = mutableMapOf<Int, IntArray>()  
        val files = dir.listFiles() ?: return emptyMap()  
  
        for (file in files) {  
            val parts = file.nameWithoutExtension.split("_")  
            if (parts.size != 2) continue  
            val baseId = parts[0].toIntOrNull() ?: continue  
            val star = parts[1].toIntOrNull() ?: continue  
  
            // 优先保留 star=6 的模板  
            if (result.containsKey(baseId) && star < 6) continue  
  
            try {  
                val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: continue  
                // 对应 arena: data[uid] 是 128x128 的图像数组  
                val resized = Bitmap.createScaledBitmap(bmp, 128, 128, true)  
                if (bmp != resized) bmp.recycle()  
                // 对应 arena: data[uid][25:96, 8:97, :] → 裁剪中心区域去除边框/星级  
                val cropped = Bitmap.createBitmap(resized, 8, 25, 89, 71)  
                resized.recycle()  
                // 计算 dHash  
                val hash = computeDHash(cropped)  
                cropped.recycle()  
                result[baseId] = hash  
            } catch (e: Exception) {  
                Log.w(TAG, "加载模板失败: ${file.name}", e)  
            }  
        }  
  
        Log.i(TAG, "已加载 ${result.size} 个角色模板 dHash")  
        templateHashes = result  
        return result  
    }  
  
    // ======================== 主入口 ========================  
  
    /**  
     * 从截图中识别防守阵容角色。  
     * 优先使用 OpenCV 迭代轮廓检测，失败时回退到固定比例裁剪。  
     */  
    fun recognize(screenshot: Bitmap, screenW: Int, screenH: Int): RecognitionResult {  
        val tmpl = loadTemplateHashes()  
        if (tmpl.isEmpty()) {  
            Log.w(TAG, "本地头像库为空，请先下载角色头像")  
            return RecognitionResult(emptyList(), null, null)  
        }  
  
        // 优先尝试 OpenCV 迭代轮廓检测  
        if (ensureOpenCV()) {  
            val result = cropByIterativeDetection(screenshot, tmpl)  
            if (result.recognizedIds.isNotEmpty()) return result  
        }  
  
        // 回退到固定比例裁剪  
        Log.i(TAG, "OpenCV 迭代检测未找到头像，回退到固定比例裁剪")  
        val crops = cropByFixedRatio(screenshot, screenW, screenH)  
        if (crops.isEmpty()) {  
            Log.w(TAG, "裁剪头像区域失败")  
            return RecognitionResult(emptyList(), null, null)  
        }  
  
        return matchCrops(crops, tmpl, "回退")  
    }  
  
    /**  
     * 从用户框选的区域中识别角色。  
     * 优先在区域内做 OpenCV 迭代轮廓检测，失败则水平等分为5份。  
     */  
    fun recognizeFromRegion(region: Bitmap): RecognitionResult {  
        val tmpl = loadTemplateHashes()  
        if (tmpl.isEmpty()) {  
            Log.w(TAG, "本地头像库为空，请先下载角色头像")  
            return RecognitionResult(emptyList(), null, null)  
        }  
  
        Log.i(TAG, "框选区域识别: ${region.width}x${region.height}")  
  
        // 优先在框选区域内做迭代轮廓检测  
        if (ensureOpenCV()) {  
            val result = cropByIterativeDetection(region, tmpl)  
            if (result.recognizedIds.isNotEmpty()) return result  
        }  
  
        // 回退到等分  
        Log.i(TAG, "框选区域内迭代检测未找到头像，等分为5份")  
        val crops = cropByEqualSplit(region)  
        if (crops.isEmpty()) {  
            Log.w(TAG, "框选区域裁剪失败")  
            return RecognitionResult(emptyList(), null, null)  
        }  
  
        return matchCrops(crops, tmpl, "框选等分")  
    }  
  
    /**  
     * 对裁剪出的头像列表做 dHash 匹配（用于回退方案，无调试图）。  
     */  
    private fun matchCrops(crops: List<Bitmap>, tmpl: Map<Int, IntArray>, tag: String): RecognitionResult {  
        val result = mutableListOf<Int>()  
        for ((index, crop) in crops.withIndex()) {  
            val match = findBestMatchDHash(crop, tmpl)  
            crop.recycle()  
            if (match != null) {  
                Log.i(TAG, "${tag}位置$index: 匹配到 baseId=${match.first}, 距离=${match.second}")  
                result.add(match.first)  
            } else {  
                Log.w(TAG, "${tag}位置$index: 未匹配到角色")  
            }  
        }  
        return RecognitionResult(result, null, null)  
    }  
  
    /**  
     * 将区域水平等分为5个正方形（取高度为边长）。  
     */  
    private fun cropByEqualSplit(region: Bitmap): List<Bitmap> {  
        val h = region.height  
        val iconSize = h  
        val totalW = region.width  
        if (totalW < iconSize) {  
            Log.w(TAG, "框选区域宽度(${totalW})小于高度(${h})，无法等分")  
            return emptyList()  
        }  
  
        val crops = mutableListOf<Bitmap>()  
        val count = 5  
        val spacing = (totalW - iconSize).toFloat() / (count - 1).coerceAtLeast(1)  
  
        for (i in 0 until count) {  
            val left = (i * spacing).toInt().coerceIn(0, totalW - iconSize)  
            val w = iconSize.coerceAtMost(totalW - left)  
            if (w > 0 && h > 0) {  
                try {  
                    crops.add(Bitmap.createBitmap(region, left, 0, w, h))  
                } catch (e: Exception) {  
                    Log.w(TAG, "等分裁剪$i 失败", e)  
                }  
            }  
        }  
        return crops  
    }  
  
    // ======================== dHash 感知哈希（移植自 arena）========================  
  
    /**  
     * 计算图像的 dHash（差异哈希）。  
     * 对应 arena 的 get_hash_arr(image) = difference_value(cut_image(image))。  
     *  
     * 步骤：  
     * 1. resize 到 (HASH_SIZE+1) x HASH_SIZE = 17x16  
     * 2. 转灰度  
     * 3. 每行17个像素，比较相邻像素：pixel[i] > pixel[i+1] ? 1 : 0 → 16个值  
     * 4. 16行 × 16值 = 256个值  
     *  
     * @return IntArray(256)，每个元素为 0 或 1  
     */  
    private fun computeDHash(bitmap: Bitmap): IntArray {  
        val w = HASH_SIZE + 1  // 17  
        val h = HASH_SIZE      // 16  
        val resized = Bitmap.createScaledBitmap(bitmap, w, h, true)  
  
        val pixels = IntArray(w * h)  
        resized.getPixels(pixels, 0, w, 0, 0, w, h)  
        if (resized != bitmap) resized.recycle()  
  
        // 转灰度（对应 arena 的 .convert('L')）  
        val grey = IntArray(pixels.size) { i ->  
            val p = pixels[i]  
            val r = (p shr 16) and 0xFF  
            val g = (p shr 8) and 0xFF  
            val b = p and 0xFF  
            (0.299 * r + 0.587 * g + 0.114 * b).toInt()  
        }  
  
        // 计算差异哈希（对应 arena 的 trans_hash + difference_value）  
        // 每行17个像素，比较 grey[row*17+i] > grey[row*17+i+1] → 16个值/行  
        val hash = IntArray(HASH_SIZE * HASH_SIZE)  
        for (row in 0 until HASH_SIZE) {  
            for (col in 0 until HASH_SIZE) {  
                val idx = row * w + col  
                hash[row * HASH_SIZE + col] = if (grey[idx] > grey[idx + 1]) 1 else 0  
            }  
        }  
        return hash  
    }  
  
    /**  
     * 计算两个 dHash 之间的汉明距离。  
     * 对应 arena 的 calc_distance_arr(arr1, arr2) = sum(sum(abs(arr1 - arr2)))。  
     */  
    private fun calcHashDistance(hash1: IntArray, hash2: IntArray): Int {  
        var dist = 0  
        for (i in hash1.indices) {  
            dist += abs(hash1[i] - hash2[i])  
        }  
        return dist  
    }  
  
    /**  
     * 在所有模板中找 dHash 距离最小且不超过阈值的匹配。  
     * 对应 arena 的 getUnit()：  
     *   resize 128x128 → crop [25:96, 8:97] → computeDHash → 比较所有模板 → 距离 > 90 为 Unknown  
     *  
     * @return Pair(baseId, distance) 或 null  
     */  
    private fun findBestMatchDHash(candidate: Bitmap, templateHashes: Map<Int, IntArray>): Pair<Int, Int>? {  
        // 对应 arena: img2.resize((128, 128))  
        val resized = Bitmap.createScaledBitmap(candidate, 128, 128, true)  
        // 对应 arena: img3[25:96, 8:97, :] → 裁剪中心区域  
        val cropped = try {  
            Bitmap.createBitmap(resized, 8, 25, 89, 71)  
        } catch (e: Exception) {  
            Log.w(TAG, "裁剪候选头像失败", e)  
            if (resized != candidate) resized.recycle()  
            return null  
        }  
        if (resized != candidate) resized.recycle()  
  
        val candHash = computeDHash(cropped)  
        cropped.recycle()  
  
        var bestId = -1  
        var bestDist = Int.MAX_VALUE  
        for ((baseId, tmplHash) in templateHashes) {  
            val dist = calcHashDistance(candHash, tmplHash)  
            if (dist < bestDist) {  
                bestDist = dist  
                bestId = baseId  
            }  
        }  
  
        // 对应 arena: if similarity > 90: return Unknown  
        return if (bestDist <= DHASH_THRESHOLD) Pair(bestId, bestDist) else null  
    }  
  
    // ======================== OpenCV 轮廓检测 ========================  
  
    /**  
     * cutting mode=2：在灰度图上找正方形区域并聚类。  
     * 对应 arena 的 cutting(img, mode=2)。  
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
            val other = sorted.drop(1).flatMap { it.value }  
            CuttingSquaresResult(best.value, other)  
        } else {  
            // 不是5的倍数，返回所有正方形（对应 arena old_main.py 第268-269行）  
            CuttingSquaresResult(squares, emptyList())  
        }  
    }  
  
    /**  
     * cutting mode=1：找到图像中面积最大的轮廓，返回其 boundingRect。  
     * 对应 arena 的 cutting(img, mode=1)。  
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
     * 在调试图上标注一个正方形区域。  
     * 对应 arena 的 highlight 函数 (old_main.py:312-316)。  
     *  
     * @param textCanvas  outpImgText 的 Canvas  
     * @param textBmp     outpImgText 的 Bitmap（用于 paste 裁出的头像）  
     * @param srcBmp      当前工作 Bitmap（用于裁出头像内容）  
     * @param sq          正方形区域（坐标相对于 srcBmp）  
     * @param actualX     累计 X 偏移（映射回原图坐标）  
     * @param actualY     累计 Y 偏移  
     * @param paint       画笔（已设好 color）  
     * @param color       颜色值  
     */  
    private fun highlightSquare(  
        textCanvas: Canvas,  
        textBmp: Bitmap,  
        srcBmp: Bitmap,  
        sq: SquareRegion,  
        actualX: Int,  
        actualY: Int,  
        paint: Paint,  
        color: Int  
    ) {  
        // 将裁出的头像贴到 outpImgText 上（对应 arena: outpImgText.paste(cropped, ...)）  
        val cx = (sq.x + 2).coerceAtLeast(0)  
        val cy = (sq.y + 2).coerceAtLeast(0)  
        val cw = (sq.w - 4).coerceAtMost(srcBmp.width - cx).coerceAtLeast(1)  
        val ch = (sq.h - 4).coerceAtMost(srcBmp.height - cy).coerceAtLeast(1)  
        try {  
            val cropped = Bitmap.createBitmap(srcBmp, cx, cy, cw, ch)  
            val destX = actualX + sq.x + 2  
            val destY = actualY + sq.y + 2  
            if (destX >= 0 && destY >= 0 &&  
                destX + cw <= textBmp.width && destY + ch <= textBmp.height  
            ) {  
                textCanvas.drawBitmap(cropped, destX.toFloat(), destY.toFloat(), null)  
            }  
            cropped.recycle()  
        } catch (_: Exception) {}  
  
        // 画矩形框（对应 arena: outpImgTextDraw.rectangle(..., outline=color, width=4)）  
        paint.color = color  
        textCanvas.drawRect(  
            (actualX + sq.x).toFloat(),  
            (actualY + sq.y).toFloat(),  
            (actualX + sq.x + sq.w).toFloat(),  
            (actualY + sq.y + sq.h).toFloat(),  
            paint  
        )  
    }  
  
    /**  
     * 从右往左拆出最右一列。  
     * 对应 arena 的 split_last_col_recs (old_main.py:327-331)。  
     *  
     * @param recs 当前剩余的正方形集合（以 IntArray [x,y,w,h] 表示）  
     * @return Pair(剩余, 最右一列)，最右一列按 y 排序  
     */  
    private fun splitLastColRecs(  
        recs: List<IntArray>  
    ): Pair<List<IntArray>, List<IntArray>> {  
        if (recs.isEmpty()) return Pair(emptyList(), emptyList())  
        val sorted = recs.sortedByDescending { it[0] } // 按 x 降序  
        val rightmostX = sorted[0][0]  
        val halfW = sorted[0][2] / 2  
        val lastCol = sorted.filter { abs(it[0] - rightmostX) < halfW }.sortedBy { it[1] }  
        val remaining = sorted.filter { abs(it[0] - rightmostX) >= halfW }  
        return Pair(remaining, lastCol)  
    }  
  
    /**  
     * 迭代轮廓检测，移植自 arena 的 getPos() 函数 (old_main.py:285-416)。  
     *  
     * 流程（最多循环 MAX_ITERATIONS 次）：  
     * 1. findSquares (cutting mode=2) 找正方形  
     * 2. 找不到 → 标记 needInvert  
     * 3. 找到 >=4 个 → 行列分组 + dHash 匹配 → 生成调试图 + 对比图 → 返回  
     * 4. findLargestRect (cutting mode=1) 找最大矩形，裁剪进去  
     * 5. 第一次迭代或 needInvert → 反色灰度图  
     * 6. 重复  
     *  
     * 调试图颜色含义（与 arena highlight 一致）：  
     *   红色=识别成功, 蓝色=被排除, 绿色=超出5列, 黑色=匹配失败, 黄色=baseId=1000  
     */  
    private fun cropByIterativeDetection(  
        screenshot: Bitmap,  
        tmplHashes: Map<Int, IntArray>  
    ): RecognitionResult {  
        val origW = screenshot.width  
        val origH = screenshot.height  
  
        // --- 调试图初始化 ---  
        // outpImg: 黑色背景，后续会混合原图（对应 arena outpImg）  
        val outpImg = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)  
        val outpImgCanvas = Canvas(outpImg)  
        outpImgCanvas.drawColor(Color.BLACK)  
  
        // outpImgText: 透明背景，用于绘制头像和矩形框（对应 arena outpImgText）  
        val outpImgText = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)  
        val outpImgTextCanvas = Canvas(outpImgText)  
        // 默认透明  
  
        val strokePaint = Paint().apply {  
            style = Paint.Style.STROKE  
            strokeWidth = 4f  
            isAntiAlias = false  
        }  
  
        // --- 转灰度 Mat ---  
        val srcMat = Mat()  
        Utils.bitmapToMat(screenshot, srcMat)  
        val fullGrey = Mat()  
        Imgproc.cvtColor(srcMat, fullGrey, Imgproc.COLOR_RGBA2GRAY)  
        srcMat.release()  
  
        // --- 工作变量 ---  
        var currentGrey = fullGrey       // 当前灰度 Mat（会被裁剪/反色）  
        var currentBmp = screenshot      // 当前 RGBA Bitmap（同步裁剪）  
        var actualX = 0                  // 累计偏移  
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
                for (sq in otherBorder) {  
                    highlightSquare(outpImgTextCanvas, outpImgText, currentBmp,  
                        sq, actualX, actualY, strokePaint, Color.BLUE)  
                }  
  
                if (border.size < 4) {  
                    // 不够4个，全部标蓝，继续迭代  
                    for (sq in border) {  
                        highlightSquare(outpImgTextCanvas, outpImgText, currentBmp,  
                            sq, actualX, actualY, strokePaint, Color.BLUE)  
                    }  
                    Log.d(TAG, "迭代$cnt: 正方形只有${border.size}个(<4)，继续")  
                } else {  
                    // >=4 个正方形，进入行列分组 + 匹配  
                    Log.i(TAG, "迭代$cnt: 找到${border.size}个正方形，开始匹配")  
  
                    // 转为 IntArray 列表 [x, y, w, h]  
                    var recs = border.map { intArrayOf(it.x, it.y, it.w, it.h) }  
                    val recsSet = recs.toMutableList()  
  
                    // 找出最右侧一列有几行 → 确定行数  
                    val (_, initLastCol) = splitLastColRecs(recsSet)  
                    val rowCnt = initLastCol.size  
                    if (rowCnt == 0) {  
                        Log.w(TAG, "迭代$cnt: splitLastCol 行数为0")  
                        // 继续下一轮迭代  
                    } else {  
                        // 以最右一列的 Y 坐标为基准（对应 arena: last_col_recs_xpos = [rec[1] for rec in last_col_recs]）  
                        val lastColYPos = initLastCol.map { it[1] }  
  
                        val arr = Array(rowCnt) { arrayOfNulls<IntArray>(5) }  
                        val arrId = Array(rowCnt) { mutableListOf<Int>() }  
  
                        // 重置 recs，从右往左一列一列掰  
                        var remaining = recsSet.toList()  
                        for (colIndex in 0 until 5) {  
                            val (rest, lastCol) = splitLastColRecs(remaining)  
                            remaining = rest  
                            if (lastCol.isEmpty()) break  
  
                            for (rec in lastCol) {  
                                val x = rec[0]; val y = rec[1]; val w = rec[2]; val h = rec[3]  
  
                                // 裁剪头像并匹配  
                                val cx = (x + 2).coerceAtLeast(0)  
                                val cy = (y + 2).coerceAtLeast(0)  
                                val cw = (w - 4).coerceAtMost(currentBmp.width - cx).coerceAtLeast(1)  
                                val ch = (h - 4).coerceAtMost(currentBmp.height - cy).coerceAtLeast(1)  
  
                                var baseId = 0  
                                try {  
                                    val cropped = Bitmap.createBitmap(currentBmp, cx, cy, cw, ch)  
                                    val match = findBestMatchDHash(cropped, tmplHashes)  
                                    cropped.recycle()  
                                    if (match != null) {  
                                        baseId = match.first  
                                        Log.d(TAG, "  col=$colIndex rec=($x,$y) → baseId=$baseId dist=${match.second}")  
                                    }  
                                } catch (e: Exception) {  
                                    Log.w(TAG, "裁剪/匹配失败", e)  
                                }  
  
                                val sq = SquareRegion((w + h) / 2, x, y, w, h)  
                                if (baseId == 0) {  
                                    // 匹配失败 → 黑色  
                                    highlightSquare(outpImgTextCanvas, outpImgText, currentBmp,  
                                        sq, actualX, actualY, strokePaint, Color.BLACK)  
                                } else {  
                                    // 匹配成功 → 红色（baseId=1000 时黄色）  
                                    val c = if (baseId != 1000) Color.RED else Color.YELLOW  
                                    highlightSquare(outpImgTextCanvas, outpImgText, currentBmp,  
                                        sq, actualX, actualY, strokePaint, c)  
  
                                    // 找最近的行  
                                    var mostNearRow = 0  
                                    for (rowIndex in 1 until rowCnt) {  
                                        if (abs(lastColYPos[rowIndex] - y) < abs(lastColYPos[mostNearRow] - y)) {  
                                            mostNearRow = rowIndex  
                                        }  
                                    }  
                                    // 赋值（如果该位置为空，或新的更近）  
                                    val existing = arr[mostNearRow][colIndex]  
                                    if (existing == null ||  
                                        abs(lastColYPos[mostNearRow] - existing[1]) > abs(lastColYPos[mostNearRow] - y)  
                                    ) {  
                                        arr[mostNearRow][colIndex] = rec  
                                        arrId[mostNearRow].add(baseId)  
                                    }  
                                }  
                            }  
                        }  
  
                        // 超出5列的标绿色  
                        for (rec in remaining) {  
                            val sq = SquareRegion((rec[2] + rec[3]) / 2, rec[0], rec[1], rec[2], rec[3])  
                            highlightSquare(outpImgTextCanvas, outpImgText, currentBmp,  
                                sq, actualX, actualY, strokePaint, Color.GREEN)  
                        }  
  
                        // --- 生成对比图 (compare_img) ---  
                        val iconSize = 64  
                        val compareBmp = Bitmap.createBitmap(  
                            iconSize * 5 + 16 * 2,  
                            iconSize * 2 * rowCnt + 16 * (rowCnt + 1),  
                            Bitmap.Config.ARGB_8888  
                        )  
                        val compareCanvas = Canvas(compareBmp)  
                        compareCanvas.drawColor(Color.WHITE)  
  
                        val allIds = mutableListOf<Int>()  
                        for (rowIndex in 0 until rowCnt) {  
                            val noneCnt = arr[rowIndex].count { it == null }  
                            if (noneCnt >= 2) {  
                                // 不允许1-3个角色查询，清空该行  
                                arrId[rowIndex].clear()  
                                continue  
                            }  
                            allIds.addAll(arrId[rowIndex])  
  
                            for (colIndex in 0 until 5) {  
                                val rec = arr[rowIndex][4 - colIndex] ?: continue  
                                val posX = 16 + iconSize * colIndex  
                                val posY = 16 * (rowIndex + 1) + iconSize * 2 * rowIndex  
  
                                // 上半：裁出的头像  
                                val rx = rec[0]; val ry = rec[1]; val rw = rec[2]; val rh = rec[3]  
                                val cx2 = (rx + 2).coerceAtLeast(0)  
                                val cy2 = (ry + 2).coerceAtLeast(0)  
                                val cw2 = (rw - 4).coerceAtMost(currentBmp.width - cx2).coerceAtLeast(1)  
                                val ch2 = (rh - 4).coerceAtMost(currentBmp.height - cy2).coerceAtLeast(1)  
                                try {  
                                    val cropped = Bitmap.createBitmap(currentBmp, cx2, cy2, cw2, ch2)  
                                    val resized = Bitmap.createScaledBitmap(cropped, iconSize, iconSize, true)  
                                    compareCanvas.drawBitmap(resized, posX.toFloat(), posY.toFloat(), null)  
                                    if (resized !== cropped) resized.recycle()  
                                    cropped.recycle()  
                                } catch (_: Exception) {}  
  
                                // 下半：通过 baseId 从本地加载的标准头像  
                                if (arrId[rowIndex].size > colIndex) {  
                                    val bid = arrId[rowIndex][colIndex]  
                                    val iconPath = IconStorage.getIconPath(context, bid, 6)  
                                        ?: IconStorage.getIconPath(context, bid, 3)  
                                    if (iconPath != null) {  
                                        try {  
                                            val iconBmp = BitmapFactory.decodeFile(iconPath)  
                                            if (iconBmp != null) {  
                                                val resizedIcon = Bitmap.createScaledBitmap(  
                                                    iconBmp, iconSize, iconSize, true  
                                                )  
                                                compareCanvas.drawBitmap(  
                                                    resizedIcon,  
                                                    posX.toFloat(),  
                                                    (posY + iconSize).toFloat(),  
                                                    null  
                                                )  
                                                if (resizedIcon !== iconBmp) resizedIcon.recycle()  
                                                iconBmp.recycle()  
                                            }  
                                        } catch (_: Exception) {}  
                                    }  
                                }  
                            }  
                        }  
  
                        // --- 合成调试图 ---  
                        // outpImg = Image.blend(outpImg, actual_img, 0.2)  
                        val blendPaint = Paint().apply { alpha = 50 } // 0.2 * 255 ≈ 50  
                        outpImgCanvas.drawBitmap(screenshot, 0f, 0f, blendPaint)  
                        // outpImg.alpha_composite(outpImgText)  
                        outpImgCanvas.drawBitmap(outpImgText, 0f, 0f, null)  
  
                        // 缩放到最大500px  
                        val ratio = max(1f, max(origW, origH).toFloat() / 500f)  
                        val finalDebug = Bitmap.createScaledBitmap(  
                            outpImg,  
                            (origW / ratio).toInt(),  
                            (origH / ratio).toInt(),  
                            true  
                        )  
  
                        // 释放资源  
                        if (currentGrey !== fullGrey) currentGrey.release()  
                        fullGrey.release()  
                        outpImgText.recycle()  
  
                        Log.i(TAG, "识别完成: ${allIds.size} 个角色 $allIds")  
                        return RecognitionResult(allIds, finalDebug, compareBmp)  
                    }  
                }  
            }  
  
            // --- 未成功识别，准备下一轮迭代 ---  
  
            // cutting mode=1: 找最大矩形，裁剪进去  
            val largestRect = findLargestRect(currentGrey)  
            if (largestRect == null) {  
                Log.w(TAG, "迭代$cnt: findLargestRect 失败")  
                break  
            }  
            val rx = largestRect[0]  
            val ry = largestRect[1]  
            val rw = largestRect[2]  
            val rh = largestRect[3]  
  
            // 裁剪灰度图（内缩2px，对应 arena: img2[y+2:y+h-2, x+2:x+w-2]）  
            val cx = (rx + 2).coerceAtLeast(0)  
            val cy = (ry + 2).coerceAtLeast(0)  
            val cw = (rw - 4).coerceAtMost(currentGrey.cols() - cx).coerceAtLeast(1)  
            val ch = (rh - 4).coerceAtMost(currentGrey.rows() - cy).coerceAtLeast(1)  
            val croppedGrey = currentGrey.submat(cy, cy + ch, cx, cx + cw).clone()  
            if (currentGrey !== fullGrey) currentGrey.release()  
  
            // 反色（对应 arena: if cnt == 1 or bo: im_grey.point(lambda x: 0 if x > 128 else 255)）  
            if (cnt == 1 || needInvert) {  
                Imgproc.threshold(croppedGrey, croppedGrey, 128.0, 255.0, Imgproc.THRESH_BINARY_INV)  
                Log.d(TAG, "迭代$cnt: 已反色")  
            }  
            currentGrey = croppedGrey  
  
            // 同步裁剪 RGBA bitmap（对应 arena: img = await cut(img, border)）  
            val bx = rx.coerceAtLeast(0)  
            val by = ry.coerceAtLeast(0)  
            val bw = rw.coerceAtMost(currentBmp.width - bx).coerceAtLeast(1)  
            val bh = rh.coerceAtMost(currentBmp.height - by).coerceAtLeast(1)  
            try {  
                currentBmp = Bitmap.createBitmap(currentBmp, bx, by, bw, bh)  
            } catch (e: Exception) {  
                Log.w(TAG, "迭代$cnt: 裁剪 bitmap 失败", e)  
                break  
            }  
  
            // 更新累计偏移（对应 arena: actual_x += border[0]; actual_y += border[1]）  
            actualX += rx  
            actualY += ry  
  
            // 在调试图上标记本次裁剪区域（对应 arena: outpImg.paste(rgb(nowcolor,...), ...)）  
            nowColor = (nowColor + 60) % 300  
            val fillPaint = Paint().apply {  
                color = Color.rgb(nowColor, nowColor, nowColor)  
                style = Paint.Style.FILL  
            }  
            outpImgCanvas.drawRect(  
                actualX.toFloat(), actualY.toFloat(),  
                (actualX + rw).toFloat(), (actualY + rh).toFloat(),  
                fillPaint  
            )  
        }  
  
        // while 循环结束仍未识别 → 返回空结果 + 调试图  
        if (currentGrey !== fullGrey) currentGrey.release()  
        fullGrey.release()  
  
        // 将原始截图混合到调试图上，让用户能看到实际截图内容  
        val blendPaint = Paint().apply { alpha = 50 }  
        outpImgCanvas.drawBitmap(screenshot, 0f, 0f, blendPaint)  
        outpImgCanvas.drawBitmap(outpImgText, 0f, 0f, null)  
        outpImgText.recycle()  
  
        val ratio = max(1f, max(origW, origH).toFloat() / 500f)  
        val finalDebug = Bitmap.createScaledBitmap(  
            outpImg, (origW / ratio).toInt(), (origH / ratio).toInt(), true  
        )  
  
        Log.w(TAG, "迭代检测失败，未识别到角色")  
        return RecognitionResult(emptyList(), finalDebug, null)  
    }  
  
    // ======================== 固定比例裁剪（回退方案，不变）========================  
  
    private fun cropByFixedRatio(screenshot: Bitmap, screenW: Int, screenH: Int): List<Bitmap> {  
        val screenRatio = screenW.toFloat() / screenH  
        val gameW: Int; val gameH: Int; val offsetX: Int; val offsetY: Int  
        if (screenRatio > GAME_ASPECT_RATIO) {  
            gameH = screenH; gameW = (screenH * GAME_ASPECT_RATIO).toInt()  
            offsetX = (screenW - gameW) / 2; offsetY = 0  
        } else if (screenRatio < GAME_ASPECT_RATIO) {  
            gameW = screenW; gameH = (screenW / GAME_ASPECT_RATIO).toInt()  
            offsetX = 0; offsetY = (screenH - gameH) / 2  
        } else {  
            gameW = screenW; gameH = screenH; offsetX = 0; offsetY = 0  
        }  
        val iconSize = (gameW * 0.058f).toInt()  
        val halfSize = iconSize / 2  
        val centerY = offsetY + (gameH * 0.24f).toInt()  
        val crops = mutableListOf<Bitmap>()  
        for (i in 0 until 5) {  
            val r = 0.30f + (0.70f - 0.30f) * i / 4f  
            val centerX = offsetX + (gameW * r).toInt()  
            val left = (centerX - halfSize).coerceIn(0, screenshot.width - 1)  
            val top = (centerY - halfSize).coerceIn(0, screenshot.height - 1)  
            val w = iconSize.coerceAtMost(screenshot.width - left)  
            val h = iconSize.coerceAtMost(screenshot.height - top)  
            if (w > 0 && h > 0) {  
                try { crops.add(Bitmap.createBitmap(screenshot, left, top, w, h)) }  
                catch (e: Exception) { Log.w(TAG, "回退裁剪$i 失败", e) }  
            }  
        }  
        return crops  
    }  
}