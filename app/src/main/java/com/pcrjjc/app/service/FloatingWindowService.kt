package com.pcrjjc.app.service  
  
import android.annotation.SuppressLint  
import android.app.Service  
import android.content.Context  
import android.content.Intent  
import android.graphics.Bitmap  
import android.graphics.BitmapFactory  
import android.graphics.Canvas  
import android.graphics.Color  
import android.graphics.Paint  
import android.graphics.PixelFormat  
import android.graphics.Rect  
import android.graphics.RectF  
import android.os.Handler  
import android.os.IBinder  
import android.os.Looper  
import android.util.Log  
import android.util.TypedValue  
import android.view.Gravity  
import android.view.MotionEvent  
import android.view.View  
import android.view.ViewGroup  
import android.view.WindowManager  
import android.widget.FrameLayout  
import android.widget.HorizontalScrollView  
import android.widget.ImageView  
import android.widget.LinearLayout  
import android.widget.ProgressBar  
import android.widget.ScrollView  
import android.widget.TextView  
import android.widget.Toast  
import com.pcrjjc.app.util.ArenaQueryClient  
import com.pcrjjc.app.util.IconRecognizer  
import com.pcrjjc.app.util.IconStorage  
import kotlinx.coroutines.CoroutineScope  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.Job  
import kotlinx.coroutines.cancel  
import kotlinx.coroutines.launch  
import kotlinx.coroutines.withContext  
  
class FloatingWindowService : Service() {  
  
    companion object {  
        private const val TAG = "FloatingWindow"  
        @Volatile  
        var isRunning = false  
            private set  
    }  
  
    private lateinit var windowManager: WindowManager  
    private var floatButton: View? = null  
    private var resultPanel: View? = null  
    private var cropOverlay: View? = null  
    private val scope = CoroutineScope(Dispatchers.Main + Job())  
    private val handler = Handler(Looper.getMainLooper())  
  
    private lateinit var iconRecognizer: IconRecognizer  
    private lateinit var arenaClient: ArenaQueryClient  
  
    override fun onBind(intent: Intent?): IBinder? = null  
  
    override fun onCreate() {  
        super.onCreate()  
        isRunning = true  
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager  
        iconRecognizer = IconRecognizer(this)  
        arenaClient = ArenaQueryClient()  
        createFloatButton()  
    }  
  
    override fun onDestroy() {  
        super.onDestroy()  
        isRunning = false  
        removeFloatButton()  
        removeResultPanel()  
        removeCropOverlay()  
        scope.cancel()  
    }  
  
    // ======================== 浮动按钮 ========================  
  
    @SuppressLint("ClickableViewAccessibility")  
    private fun createFloatButton() {  
        val button = TextView(this).apply {  
            text = "拆"  
            textSize = 18f  
            setTextColor(Color.WHITE)  
            gravity = Gravity.CENTER  
            setBackgroundColor(0xDD3F51B5.toInt())  
            setPadding(dp(4), dp(4), dp(4), dp(4))  
        }  
  
        val size = dp(48)  
        val params = WindowManager.LayoutParams(  
            size, size,  
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or  
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,  
            PixelFormat.TRANSLUCENT  
        ).apply {  
            gravity = Gravity.TOP or Gravity.START  
            x = 0  
            y = dp(200)  
        }  
  
        var initialX = 0  
        var initialY = 0  
        var initialTouchX = 0f  
        var initialTouchY = 0f  
        var isDragging = false  
        val longPressRunnable = Runnable {  
            Toast.makeText(this, "怎么拆浮窗已关闭", Toast.LENGTH_SHORT).show()  
            stopService(Intent(this, ScreenCaptureService::class.java))  
            stopSelf()  
        }  
  
        button.setOnTouchListener { _, event ->  
            when (event.action) {  
                MotionEvent.ACTION_DOWN -> {  
                    initialX = params.x  
                    initialY = params.y  
                    initialTouchX = event.rawX  
                    initialTouchY = event.rawY  
                    isDragging = false  
                    handler.postDelayed(longPressRunnable, 800)  
                    true  
                }  
                MotionEvent.ACTION_MOVE -> {  
                    val dx = event.rawX - initialTouchX  
                    val dy = event.rawY - initialTouchY  
                    if (dx * dx + dy * dy > 25) {  
                        isDragging = true  
                        handler.removeCallbacks(longPressRunnable)  
                    }  
                    params.x = initialX + dx.toInt()  
                    params.y = initialY + dy.toInt()  
                    windowManager.updateViewLayout(button, params)  
                    true  
                }  
                MotionEvent.ACTION_UP -> {  
                    handler.removeCallbacks(longPressRunnable)  
                    if (!isDragging) onFloatButtonClick()  
                    true  
                }  
                else -> false  
            }  
        }  
  
        windowManager.addView(button, params)  
        floatButton = button  
    }  
  
    private fun removeFloatButton() {  
        floatButton?.let {  
            try { windowManager.removeView(it) } catch (_: Exception) {}  
        }  
        floatButton = null  
    }  
  
    // ======================== 截图 → 框选 ========================  
  
    private fun onFloatButtonClick() {  
        floatButton?.visibility = View.INVISIBLE  
        removeResultPanel()  
  
        handler.postDelayed({  
            scope.launch {  
                try {  
                    val captureService = ScreenCaptureService.instance  
                    if (captureService == null) {  
                        withContext(Dispatchers.Main) {  
                            Toast.makeText(this@FloatingWindowService, "截图服务未运行", Toast.LENGTH_SHORT).show()  
                            floatButton?.visibility = View.VISIBLE  
                        }  
                        return@launch  
                    }  
  
                    val screenshot = withContext(Dispatchers.IO) { captureService.captureScreen() }  
                    if (screenshot == null) {  
                        withContext(Dispatchers.Main) {  
                            Toast.makeText(this@FloatingWindowService, "截图失败，请重试", Toast.LENGTH_SHORT).show()  
                            floatButton?.visibility = View.VISIBLE  
                        }  
                        return@launch  
                    }  
  
                    val templateCount = iconRecognizer.getTemplateCount()  
                    if (templateCount == 0) {  
                        withContext(Dispatchers.Main) {  
                            Toast.makeText(this@FloatingWindowService, "本地头像库为空，请先下载角色头像", Toast.LENGTH_LONG).show()  
                            floatButton?.visibility = View.VISIBLE  
                        }  
                        screenshot.recycle()  
                        return@launch  
                    }  
  
                    // 显示框选界面  
                    withContext(Dispatchers.Main) {  
                        showCropSelection(screenshot, templateCount)  
                    }  
                } catch (e: Exception) {  
                    Log.e(TAG, "截图流程出错", e)  
                    withContext(Dispatchers.Main) {  
                        Toast.makeText(this@FloatingWindowService, "出错: ${e.message}", Toast.LENGTH_SHORT).show()  
                        floatButton?.visibility = View.VISIBLE  
                    }  
                }  
            }  
        }, 300)  
    }  
  
    // ======================== 框选覆盖层 ========================  
  
    @SuppressLint("ClickableViewAccessibility")  
    private fun showCropSelection(screenshot: Bitmap, templateCount: Int) {  
        val root = FrameLayout(this)  
  
        // 自定义绘制 View：显示截图 + 框选矩形  
        val cropView = CropSelectionView(this, screenshot)  
        root.addView(cropView, FrameLayout.LayoutParams(  
            FrameLayout.LayoutParams.MATCH_PARENT,  
            FrameLayout.LayoutParams.MATCH_PARENT  
        ))  
  
        // 底部按钮栏  
        val buttonBar = LinearLayout(this).apply {  
            orientation = LinearLayout.HORIZONTAL  
            gravity = Gravity.CENTER  
            setBackgroundColor(0xCC000000.toInt())  
            setPadding(dp(8), dp(10), dp(8), dp(10))  
        }  
  
        fun makeBtn(label: String, color: Int): TextView = TextView(this).apply {  
            text = label  
            setTextColor(color)  
            textSize = 14f  
            setPadding(dp(16), dp(8), dp(16), dp(8))  
            setBackgroundColor(0xFF333333.toInt())  
        }  
  
        val cancelBtn = makeBtn("取消", Color.LTGRAY)  
        val confirmBtn = makeBtn("确认框选", 0xFF90CAF9.toInt())  
        val autoBtn = makeBtn("自动识别", 0xFF81C784.toInt())  
  
        cancelBtn.setOnClickListener {  
            removeCropOverlay()  
            screenshot.recycle()  
            floatButton?.visibility = View.VISIBLE  
        }  
  
        confirmBtn.setOnClickListener {  
            val sel = cropView.getSelectionInBitmapCoords()  
            if (sel == null || sel.width() < 20 || sel.height() < 20) {  
                Toast.makeText(this, "请先在截图上拖动框选头像区域", Toast.LENGTH_SHORT).show()  
                return@setOnClickListener  
            }  
            removeCropOverlay()  
            onCropConfirmed(screenshot, sel, templateCount)  
        }  
  
        autoBtn.setOnClickListener {  
            removeCropOverlay()  
            onAutoRecognize(screenshot, templateCount)  
        }  
  
        val spacer = { View(this).apply {  
            layoutParams = LinearLayout.LayoutParams(dp(12), 1)  
        }}  
        buttonBar.addView(cancelBtn)  
        buttonBar.addView(spacer())  
        buttonBar.addView(confirmBtn)  
        buttonBar.addView(spacer())  
        buttonBar.addView(autoBtn)  
  
        root.addView(buttonBar, FrameLayout.LayoutParams(  
            FrameLayout.LayoutParams.MATCH_PARENT,  
            FrameLayout.LayoutParams.WRAP_CONTENT  
        ).apply { gravity = Gravity.BOTTOM })  
  
        // 提示文字  
        val hint = TextView(this).apply {  
            text = "拖动框选防守阵容头像区域"  
            setTextColor(Color.WHITE)  
            textSize = 14f  
            setBackgroundColor(0x88000000.toInt())  
            setPadding(dp(12), dp(6), dp(12), dp(6))  
        }  
        root.addView(hint, FrameLayout.LayoutParams(  
            FrameLayout.LayoutParams.WRAP_CONTENT,  
            FrameLayout.LayoutParams.WRAP_CONTENT  
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; topMargin = dp(16) })  
  
        val params = WindowManager.LayoutParams(  
            WindowManager.LayoutParams.MATCH_PARENT,  
            WindowManager.LayoutParams.MATCH_PARENT,  
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or  
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,  
            PixelFormat.TRANSLUCENT  
        ).apply { gravity = Gravity.TOP or Gravity.START }  
  
        windowManager.addView(root, params)  
        cropOverlay = root  
    }  
  
    private fun removeCropOverlay() {  
        cropOverlay?.let {  
            try { windowManager.removeView(it) } catch (_: Exception) {}  
        }  
        cropOverlay = null  
    }
// ======================== CropSelectionView 内部类 ========================  
  
    @SuppressLint("ViewConstructor")  
    private class CropSelectionView(  
        context: Context,  
        private val screenshot: Bitmap  
    ) : View(context) {  
  
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)  
        private val dimPaint = Paint().apply { color = 0x88000000.toInt() }  
        private val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {  
            color = 0xFF00BFFF.toInt()  
            style = Paint.Style.STROKE  
            strokeWidth = 4f  
        }  
  
        // 截图绘制区域（View 坐标系）  
        private var drawRect = RectF()  
        private var scaleX = 1f  
        private var scaleY = 1f  
  
        // 用户框选（View 坐标系）  
        private var selStart: PointF? = null  
        private var selEnd: PointF? = null  
        private var selRect: RectF? = null  
  
        private class PointF(val x: Float, val y: Float)  
  
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {  
            super.onSizeChanged(w, h, oldw, oldh)  
            // 计算截图缩放到 View 的映射  
            val viewRatio = w.toFloat() / h  
            val bmpRatio = screenshot.width.toFloat() / screenshot.height  
            if (bmpRatio > viewRatio) {  
                val drawW = w.toFloat()  
                val drawH = w / bmpRatio  
                val top = (h - drawH) / 2  
                drawRect = RectF(0f, top, drawW, top + drawH)  
            } else {  
                val drawH = h.toFloat()  
                val drawW = h * bmpRatio  
                val left = (w - drawW) / 2  
                drawRect = RectF(left, 0f, left + drawW, drawH)  
            }  
            scaleX = screenshot.width / drawRect.width()  
            scaleY = screenshot.height / drawRect.height()  
        }  
  
        override fun onDraw(canvas: Canvas) {  
            super.onDraw(canvas)  
            // 绘制截图  
            canvas.drawBitmap(screenshot, null, drawRect, paint)  
            // 半透明遮罩  
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)  
            // 框选区域（清除遮罩，显示原图）  
            selRect?.let { r ->  
                canvas.save()  
                canvas.clipRect(r)  
                canvas.drawBitmap(screenshot, null, drawRect, paint)  
                canvas.restore()  
                canvas.drawRect(r, rectPaint)  
            }  
        }  
  
        @SuppressLint("ClickableViewAccessibility")  
        override fun onTouchEvent(event: MotionEvent): Boolean {  
            when (event.action) {  
                MotionEvent.ACTION_DOWN -> {  
                    selStart = PointF(event.x, event.y)  
                    selEnd = null  
                    selRect = null  
                    invalidate()  
                }  
                MotionEvent.ACTION_MOVE -> {  
                    selEnd = PointF(event.x, event.y)  
                    val s = selStart ?: return true  
                    val e = selEnd ?: return true  
                    selRect = RectF(  
                        minOf(s.x, e.x), minOf(s.y, e.y),  
                        maxOf(s.x, e.x), maxOf(s.y, e.y)  
                    )  
                    invalidate()  
                }  
                MotionEvent.ACTION_UP -> {  
                    selEnd = PointF(event.x, event.y)  
                    val s = selStart ?: return true  
                    val e = selEnd ?: return true  
                    selRect = RectF(  
                        minOf(s.x, e.x), minOf(s.y, e.y),  
                        maxOf(s.x, e.x), maxOf(s.y, e.y)  
                    )  
                    invalidate()  
                }  
            }  
            return true  
        }  
  
        /** 将 View 坐标系的框选转换为 Bitmap 坐标系 */  
        fun getSelectionInBitmapCoords(): Rect? {  
            val r = selRect ?: return null  
            val left = ((r.left - drawRect.left) * scaleX).toInt().coerceIn(0, screenshot.width)  
            val top = ((r.top - drawRect.top) * scaleY).toInt().coerceIn(0, screenshot.height)  
            val right = ((r.right - drawRect.left) * scaleX).toInt().coerceIn(0, screenshot.width)  
            val bottom = ((r.bottom - drawRect.top) * scaleY).toInt().coerceIn(0, screenshot.height)  
            if (right <= left || bottom <= top) return null  
            return Rect(left, top, right, bottom)  
        }  
    }  
  
    // ======================== 框选确认后识别 ========================  
  
    private fun onCropConfirmed(screenshot: Bitmap, rect: Rect, templateCount: Int) {  
        scope.launch {  
            try {  
                withContext(Dispatchers.Main) { showLoadingPanel() }  
  
                val w = rect.width().coerceAtMost(screenshot.width - rect.left)  
                val h = rect.height().coerceAtMost(screenshot.height - rect.top)  
                val region = Bitmap.createBitmap(screenshot, rect.left, rect.top, w, h)  
                Log.i(TAG, "框选区域: ${rect.left},${rect.top} ${w}x${h}")  
  
                val recognized = withContext(Dispatchers.IO) {  
                    iconRecognizer.recognizeFromRegion(region)  
                }  
                region.recycle()  
                screenshot.recycle()  
  
                if (recognized.isEmpty()) {  
                    withContext(Dispatchers.Main) {  
                        removeResultPanel()  
                        Toast.makeText(this@FloatingWindowService,  
                            "未识别到角色（已加载${templateCount}个模板）\n请尝试更精确地框选头像区域",  
                            Toast.LENGTH_LONG).show()  
                    }  
                    return@launch  
                }  
  
                Log.i(TAG, "框选识别到角色: $recognized")  
                val results = withContext(Dispatchers.IO) {  
                    arenaClient.query(recognized, region = 2)  
                }  
  
                withContext(Dispatchers.Main) {  
                    removeResultPanel()  
                    if (results.isEmpty()) {  
                        Toast.makeText(this@FloatingWindowService, "未找到进攻阵容推荐", Toast.LENGTH_SHORT).show()  
                    } else {  
                        showResultPanel(recognized, results)  
                    }  
                }  
            } catch (e: Exception) {  
                Log.e(TAG, "框选识别出错", e)  
                withContext(Dispatchers.Main) {  
                    removeResultPanel()  
                    Toast.makeText(this@FloatingWindowService, "出错: ${e.message}", Toast.LENGTH_SHORT).show()  
                }  
                screenshot.recycle()  
            } finally {  
                withContext(Dispatchers.Main) { floatButton?.visibility = View.VISIBLE }  
            }  
        }  
    }  
  
    // ======================== 自动识别（不框选） ========================  
  
    private fun onAutoRecognize(screenshot: Bitmap, templateCount: Int) {  
        scope.launch {  
            try {  
                withContext(Dispatchers.Main) { showLoadingPanel() }  
  
                val screenW = ScreenCaptureService.instance?.getScreenWidth() ?: screenshot.width  
                val screenH = ScreenCaptureService.instance?.getScreenHeight() ?: screenshot.height  
                Log.i(TAG, "自动识别, 模板数=$templateCount, 截图=${screenshot.width}x${screenshot.height}")  
  
                val recognized = withContext(Dispatchers.IO) {  
                    iconRecognizer.recognize(screenshot, screenW, screenH)  
                }  
                screenshot.recycle()  
  
                if (recognized.isEmpty()) {  
                    withContext(Dispatchers.Main) {  
                        removeResultPanel()  
                        Toast.makeText(this@FloatingWindowService,  
                            "未识别到角色（已加载${templateCount}个模板）\n请在竞技场对战界面使用，或尝试手动框选",  
                            Toast.LENGTH_LONG).show()  
                    }  
                    return@launch  
                }  
  
                Log.i(TAG, "自动识别到角色: $recognized")  
                val results = withContext(Dispatchers.IO) {  
                    arenaClient.query(recognized, region = 2)  
                }  
  
                withContext(Dispatchers.Main) {  
                    removeResultPanel()  
                    if (results.isEmpty()) {  
                        Toast.makeText(this@FloatingWindowService, "未找到进攻阵容推荐", Toast.LENGTH_SHORT).show()  
                    } else {  
                        showResultPanel(recognized, results)  
                    }  
                }  
            } catch (e: Exception) {  
                Log.e(TAG, "自动识别出错", e)  
                withContext(Dispatchers.Main) {  
                    removeResultPanel()  
                    Toast.makeText(this@FloatingWindowService, "出错: ${e.message}", Toast.LENGTH_SHORT).show()  
                }  
                screenshot.recycle()  
            } finally {  
                withContext(Dispatchers.Main) { floatButton?.visibility = View.VISIBLE }  
            }  
        }  
    }  
  
    // ======================== 加载面板 ========================  
  
    private fun showLoadingPanel() {  
        val panel = LinearLayout(this).apply {  
            orientation = LinearLayout.VERTICAL  
            setBackgroundColor(0xEE222222.toInt())  
            setPadding(dp(16), dp(12), dp(16), dp(12))  
            gravity = Gravity.CENTER  
        }  
  
        val progress = ProgressBar(this)  
        val text = TextView(this).apply {  
            this.text = "正在识别角色..."  
            setTextColor(Color.WHITE)  
            textSize = 14f  
            gravity = Gravity.CENTER  
            setPadding(0, dp(8), 0, 0)  
        }  
        panel.addView(progress)  
        panel.addView(text)  
  
        val params = WindowManager.LayoutParams(  
            dp(200), WindowManager.LayoutParams.WRAP_CONTENT,  
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,  
            PixelFormat.TRANSLUCENT  
        ).apply { gravity = Gravity.CENTER }  
  
        windowManager.addView(panel, params)  
        resultPanel = panel  
    }  
  
    // ======================== 结果面板 ========================  
  
    private fun showResultPanel(defenseIds: List<Int>, results: List<ArenaQueryClient.ArenaResult>) {  
        val ctx: Context = this  
  
        val root = LinearLayout(ctx).apply {  
            orientation = LinearLayout.VERTICAL  
            setBackgroundColor(0xF0222222.toInt())  
            setPadding(dp(12), dp(8), dp(12), dp(8))  
        }  
  
        val titleRow = LinearLayout(ctx).apply {  
            orientation = LinearLayout.HORIZONTAL  
            gravity = Gravity.CENTER_VERTICAL  
        }  
        val titleText = TextView(ctx).apply {  
            text = "防守阵容 → 推荐进攻"  
            setTextColor(Color.WHITE)  
            textSize = 13f  
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)  
        }  
        val closeBtn = TextView(ctx).apply {  
            text = "✕"  
            setTextColor(Color.LTGRAY)  
            textSize = 18f  
            setPadding(dp(8), 0, dp(4), 0)  
            setOnClickListener { removeResultPanel() }  
        }  
        titleRow.addView(titleText)  
        titleRow.addView(closeBtn)  
        root.addView(titleRow)  
  
        val defRow = createIconRow(ctx, defenseIds)  
        root.addView(defRow)  
  
        val divider = View(ctx).apply {  
            setBackgroundColor(Color.GRAY)  
            layoutParams = LinearLayout.LayoutParams(  
                ViewGroup.LayoutParams.MATCH_PARENT, 1  
            ).apply { setMargins(0, dp(6), 0, dp(6)) }  
        }  
        root.addView(divider)  
  
        val scrollView = ScrollView(ctx).apply {  
            layoutParams = LinearLayout.LayoutParams(  
                ViewGroup.LayoutParams.MATCH_PARENT, dp(280))  
        }  
        val listLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }  
  
        val maxResults = minOf(results.size, 10)  
        for (i in 0 until maxResults) {  
            val r = results[i]  
            val itemLayout = LinearLayout(ctx).apply {  
                orientation = LinearLayout.VERTICAL  
                setPadding(0, dp(4), 0, dp(4))  
            }  
  
            val atkRow = createIconRow(ctx, r.atkUnits)  
            itemLayout.addView(atkRow)  
  
            val infoText = TextView(ctx).apply {  
                text = "\uD83D\uDC4D${r.upVote}  \uD83D\uDC4E${r.downVote}  评分:${"%.1f".format(r.score)}"  
                setTextColor(Color.LTGRAY)  
                textSize = 11f  
                setPadding(0, dp(2), 0, 0)  
            }  
            itemLayout.addView(infoText)  
  
            if (i < maxResults - 1) {  
                val itemDivider = View(ctx).apply {  
                    setBackgroundColor(0xFF444444.toInt())  
                    layoutParams = LinearLayout.LayoutParams(  
                        ViewGroup.LayoutParams.MATCH_PARENT, 1  
                    ).apply { setMargins(0, dp(4), 0, 0) }  
                }  
                itemLayout.addView(itemDivider)  
            }  
  
            listLayout.addView(itemLayout)  
        }  
  
        scrollView.addView(listLayout)  
        root.addView(scrollView)  
  
        val bottomRow = LinearLayout(ctx).apply {  
            orientation = LinearLayout.HORIZONTAL  
            gravity = Gravity.CENTER  
            setPadding(0, dp(6), 0, 0)  
        }  
        val retryBtn = TextView(ctx).apply {  
            text = "重新截图"  
            setTextColor(0xFF90CAF9.toInt())  
            textSize = 13f  
            setPadding(dp(16), dp(6), dp(16), dp(6))  
            setOnClickListener {  
                removeResultPanel()  
                onFloatButtonClick()  
            }  
        }  
        val closeBtn2 = TextView(ctx).apply {  
            text = "关闭"  
            setTextColor(Color.LTGRAY)  
            textSize = 13f  
            setPadding(dp(16), dp(6), dp(16), dp(6))  
            setOnClickListener { removeResultPanel() }  
        }  
        bottomRow.addView(retryBtn)  
        bottomRow.addView(closeBtn2)  
        root.addView(bottomRow)  
  
        val params = WindowManager.LayoutParams(  
            dp(320), WindowManager.LayoutParams.WRAP_CONTENT,  
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,  
            PixelFormat.TRANSLUCENT  
        ).apply { gravity = Gravity.CENTER }  
  
        windowManager.addView(root, params)  
        resultPanel = root  
    }  
  
    private fun createIconRow(ctx: Context, baseIds: List<Int>): HorizontalScrollView {  
        val hsv = HorizontalScrollView(ctx)  
        val row = LinearLayout(ctx).apply {  
            orientation = LinearLayout.HORIZONTAL  
            gravity = Gravity.CENTER_VERTICAL  
            setPadding(0, dp(4), 0, dp(4))  
        }  
  
        for (id in baseIds) {  
            val iv = ImageView(ctx).apply {  
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {  
                    setMargins(dp(2), 0, dp(2), 0)  
                }  
                scaleType = ImageView.ScaleType.CENTER_CROP  
                setBackgroundColor(0xFF333333.toInt())  
            }  
  
            val iconPath = IconStorage.getIconPath(ctx, id, 6)  
                ?: IconStorage.getIconPath(ctx, id, 3)  
            if (iconPath != null) {  
                val bmp = BitmapFactory.decodeFile(iconPath)  
                if (bmp != null) {  
                    iv.setImageBitmap(Bitmap.createScaledBitmap(bmp, dp(48), dp(48), true))  
                } else {  
                    setPlaceholderText(iv, id)  
                }  
            } else {  
                setPlaceholderText(iv, id)  
            }  
  
            row.addView(iv)  
        }  
  
        hsv.addView(row)  
        return hsv  
    }  
  
    private fun setPlaceholderText(iv: ImageView, baseId: Int) {  
        iv.setImageDrawable(null)  
        iv.setBackgroundColor(0xFF555555.toInt())  
        iv.contentDescription = baseId.toString()  
    }  
  
    private fun removeResultPanel() {  
        resultPanel?.let {  
            try { windowManager.removeView(it) } catch (_: Exception) {}  
        }  
        resultPanel = null  
    }  
  
    // ======================== 工具 ========================  
  
    private fun dp(value: Int): Int {  
        return TypedValue.applyDimension(  
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),  
            resources.displayMetrics  
        ).toInt()  
    }  
}	