package com.pcrjjc.app.service  
  
import android.annotation.SuppressLint  
import android.app.Service  
import android.content.Context  
import android.content.Intent  
import android.graphics.Bitmap  
import android.graphics.BitmapFactory  
import android.graphics.Color  
import android.graphics.PixelFormat  
import android.graphics.Rect  
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
import com.pcrjjc.app.util.IconStorage  
import kotlinx.coroutines.CoroutineScope  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.Job  
import kotlinx.coroutines.cancel  
import kotlinx.coroutines.launch  
import kotlinx.coroutines.withContext  
import java.io.ByteArrayOutputStream  
  
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
  
    private lateinit var arenaClient: ArenaQueryClient  
  
    override fun onBind(intent: Intent?): IBinder? = null  
  
    override fun onCreate() {  
        super.onCreate()  
        isRunning = true  
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager  
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
                    if (!isDragging) {  
                        onFloatButtonClick()  
                    }  
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
  
    // ======================== 截图 + 框选 + 发送 ========================  
  
    private fun onFloatButtonClick() {  
        floatButton?.visibility = View.INVISIBLE  
        removeResultPanel()  
  
        handler.postDelayed({  
            scope.launch {  
                try {  
                    doScreenshotAndShowCrop()  
                } catch (e: Exception) {  
                    Log.e(TAG, "怎么拆流程出错", e)  
                    withContext(Dispatchers.Main) {  
                        Toast.makeText(this@FloatingWindowService, "出错: ${e.message}", Toast.LENGTH_SHORT).show()  
                        floatButton?.visibility = View.VISIBLE  
                    }  
                }  
            }  
        }, 300)  
    }  
  
    private suspend fun doScreenshotAndShowCrop() {  
        val captureService = ScreenCaptureService.instance  
        if (captureService == null) {  
            withContext(Dispatchers.Main) {  
                Toast.makeText(this@FloatingWindowService, "截图服务未运行", Toast.LENGTH_SHORT).show()  
                floatButton?.visibility = View.VISIBLE  
            }  
            return  
        }  
  
        val screenshot = withContext(Dispatchers.IO) { captureService.captureScreen() }  
        if (screenshot == null) {  
            withContext(Dispatchers.Main) {  
                Toast.makeText(this@FloatingWindowService, "截图失败，请重试", Toast.LENGTH_SHORT).show()  
                floatButton?.visibility = View.VISIBLE  
            }  
            return  
        }  
  
        withContext(Dispatchers.Main) {  
            showCropSelection(screenshot)  
        }  
    }  
  
    private fun showCropSelection(screenshot: Bitmap) {  
        removeCropOverlay()  
  
        val overlay = CropSelectionOverlay(  
            context = this,  
            screenshot = screenshot,  
            onConfirm = { cropRect ->  
                removeCropOverlay()  
                onCropConfirmed(screenshot, cropRect)  
            },  
            onCancel = {  
                removeCropOverlay()  
                screenshot.recycle()  
                floatButton?.visibility = View.VISIBLE  
            }  
        )  
  
        val params = WindowManager.LayoutParams(  
            WindowManager.LayoutParams.MATCH_PARENT,  
            WindowManager.LayoutParams.MATCH_PARENT,  
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or  
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,  
            PixelFormat.TRANSLUCENT  
        ).apply {  
            gravity = Gravity.TOP or Gravity.START  
        }  
  
        windowManager.addView(overlay, params)  
        cropOverlay = overlay  
    }  
  
    private fun removeCropOverlay() {  
        cropOverlay?.let {  
            try { windowManager.removeView(it) } catch (_: Exception) {}  
        }  
        cropOverlay = null  
    }  
  
    /**  
     * 用户确认框选后，裁剪区域并发送到服务器。  
     */  
    private fun onCropConfirmed(screenshot: Bitmap, cropRect: Rect) {  
        scope.launch {  
            try {  
                withContext(Dispatchers.Main) { showLoadingPanel() }  
  
                val w = cropRect.width().coerceAtMost(screenshot.width - cropRect.left)  
                val h = cropRect.height().coerceAtMost(screenshot.height - cropRect.top)  
                if (w <= 0 || h <= 0) {  
                    withContext(Dispatchers.Main) {  
                        removeResultPanel()  
                        Toast.makeText(this@FloatingWindowService, "框选区域无效", Toast.LENGTH_SHORT).show()  
                        floatButton?.visibility = View.VISIBLE  
                    }  
                    screenshot.recycle()  
                    return@launch  
                }  
  
                val croppedBitmap = Bitmap.createBitmap(screenshot, cropRect.left, cropRect.top, w, h)  
                Log.i(TAG, "框选区域: $cropRect, 裁剪: ${w}x${h}")  
  
                // 将裁剪区域转为字节数组发送到服务器  
                val baos = ByteArrayOutputStream()  
                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)  
                val imageBytes = baos.toByteArray()  
                croppedBitmap.recycle()  
                screenshot.recycle()  
  
                Log.i(TAG, "发送图片到服务器, 大小=${imageBytes.size}")  
  
                val serverResponse = withContext(Dispatchers.IO) {  
                    arenaClient.queryByImage(imageBytes, region = 2)  
                }  
  
                withContext(Dispatchers.Main) {  
                    removeResultPanel()  
  
                    if (serverResponse.code != 0) {  
                        Toast.makeText(  
                            this@FloatingWindowService,  
                            serverResponse.message.ifEmpty { "查询失败" },  
                            Toast.LENGTH_SHORT  
                        ).show()  
                        return@withContext  
                    }  
  
                    val teamCount = serverResponse.teamCount  
  
                    if (teamCount <= 1) {  
                        // JJC 单队：展示第一个有结果的队伍  
                        val firstResult = serverResponse.results.firstOrNull { it.attacks.isNotEmpty() }  
                        if (firstResult != null) {  
                            showResultPanel(firstResult.defenseIds, firstResult.attacks)  
                        } else {  
                            Toast.makeText(this@FloatingWindowService, "未找到进攻阵容推荐", Toast.LENGTH_SHORT).show()  
                        }  
                    } else {  
                        // PJJC 多队：展示所有队伍结果 + 无冲配队  
                        showMultiTeamResultPanel(serverResponse)  
                    }  
                }  
            } catch (e: Exception) {  
                Log.e(TAG, "框选识别流程出错", e)  
                withContext(Dispatchers.Main) {  
                    removeResultPanel()  
                    Toast.makeText(this@FloatingWindowService, "出错: ${e.message}", Toast.LENGTH_SHORT).show()  
                }  
                screenshot.recycle()  
            } finally {  
                withContext(Dispatchers.Main) {  
                    floatButton?.visibility = View.VISIBLE  
                }  
            }  
        }  
    }  
  
    // ======================== 加载面板 ========================  
  
    private fun showLoadingPanel() {  
        removeResultPanel()  
        val panel = LinearLayout(this).apply {  
            orientation = LinearLayout.VERTICAL  
            setBackgroundColor(0xEE222222.toInt())  
            setPadding(dp(16), dp(12), dp(16), dp(12))  
            gravity = Gravity.CENTER  
        }  
  
        val progress = ProgressBar(this)  
        val text = TextView(this).apply {  
            this.text = "正在识别中，请稍候...\n(PJJC多队可能需要1-2分钟)"  
            setTextColor(Color.WHITE)  
            textSize = 13f  
            gravity = Gravity.CENTER  
            setPadding(0, dp(8), 0, 0)  
        }  
        panel.addView(progress)  
        panel.addView(text)  
  
        val params = WindowManager.LayoutParams(  
            dp(220), WindowManager.LayoutParams.WRAP_CONTENT,  
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,  
            PixelFormat.TRANSLUCENT  
        ).apply {  
            gravity = Gravity.CENTER  
        }  
  
        windowManager.addView(panel, params)  
        resultPanel = panel  
    }  
  
    // ======================== 单队结果面板（JJC） ========================  
  
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
			text = "⠿ 防守阵容 → 推荐进攻"  // 前面加个拖动图标  
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
  
        root.addView(createIconRow(ctx, defenseIds))  
        root.addView(createDivider(ctx))  
  
        val scrollView = ScrollView(ctx).apply {  
            layoutParams = LinearLayout.LayoutParams(  
                ViewGroup.LayoutParams.MATCH_PARENT, dp(280)  
            )  
        }  
        val listLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }  
  
        val maxResults = minOf(results.size, 10)  
        for (i in 0 until maxResults) {  
            val r = results[i]  
            listLayout.addView(createAttackItem(ctx, r, i < maxResults - 1))  
        }  
  
        scrollView.addView(listLayout)  
        root.addView(scrollView)  
        root.addView(createBottomRow(ctx))  
  
        val params = WindowManager.LayoutParams(  
		dp(320), WindowManager.LayoutParams.WRAP_CONTENT,  
		WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  
		WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,  
		PixelFormat.TRANSLUCENT  
	).apply {  
		gravity = Gravity.TOP or Gravity.START  
		x = dp(20)  
		y = dp(100)  
	}  
	  
	windowManager.addView(root, params)  
	resultPanel = root  
	  
	// 用标题栏作为拖动手柄  
	makePanelDraggable(titleRow, root, params) 
    }  
  
    // ======================== 多队结果面板（PJJC） ========================  
  
    private fun showMultiTeamResultPanel(response: ArenaQueryClient.ServerArenaResponse) {  
        val ctx: Context = this  
  
        val root = LinearLayout(ctx).apply {  
            orientation = LinearLayout.VERTICAL  
            setBackgroundColor(0xF0222222.toInt())  
            setPadding(dp(12), dp(8), dp(12), dp(8))  
        }  
  
        // 标题栏  
        val titleRow = LinearLayout(ctx).apply {  
            orientation = LinearLayout.HORIZONTAL  
            gravity = Gravity.CENTER_VERTICAL  
        }  
        val titleText = TextView(ctx).apply {  
            text = "PJJC ${response.teamCount}队查询结果"  
            setTextColor(0xFFFFD54F.toInt())  
            textSize = 14f  
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
  
        // 滚动区域  
        val scrollView = ScrollView(ctx).apply {  
            layoutParams = LinearLayout.LayoutParams(  
                ViewGroup.LayoutParams.MATCH_PARENT, dp(400)  
            )  
        }  
        val contentLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }  
  
        // ===== 无冲配队方案 =====  
        if (response.collisionFreeSets.isNotEmpty()) {  
            val cfTitle = TextView(ctx).apply {  
                text = "── 无冲配队方案 (${response.collisionFreeSets.size}组) ──"  
                setTextColor(0xFF81C784.toInt())  
                textSize = 13f  
                gravity = Gravity.CENTER  
                setPadding(0, dp(6), 0, dp(4))  
            }  
            contentLayout.addView(cfTitle)  
  
            for ((setIndex, cfSet) in response.collisionFreeSets.withIndex()) {  
                // 方案标题  
                val setTitle = TextView(ctx).apply {  
                    text = "方案 ${setIndex + 1}"  
                    setTextColor(0xFFFFB74D.toInt())  
                    textSize = 12f  
                    setPadding(0, dp(6), 0, dp(2))  
                }  
                contentLayout.addView(setTitle)  
  
                for (entry in cfSet.teams) {  
                    // 防守队伍编号  
                    val defLabel = TextView(ctx).apply {  
                        val defIndex = entry.defenseIndex + 1  
                        text = "第${defIndex}队防守 → 进攻:"  
                        setTextColor(Color.LTGRAY)  
                        textSize = 11f  
                        setPadding(0, dp(2), 0, 0)  
                    }  
                    contentLayout.addView(defLabel)  
  
                    // 进攻阵容图标  
                    contentLayout.addView(createIconRow(ctx, entry.atkUnits))  
  
                    // 赞踩信息  
                    val typeLabel = when {  
                        entry.teamType.startsWith("approximation") -> " [近似]"  
                        entry.teamType == "frequency" -> " [频率推荐]"  
                        else -> ""  
                    }  
                    val infoText = TextView(ctx).apply {  
                        text = "\uD83D\uDC4D${entry.upVote}  \uD83D\uDC4E${entry.downVote}  评分:${"%.1f".format(entry.score)}$typeLabel"  
                        setTextColor(Color.LTGRAY)  
                        textSize = 10f  
                        setPadding(0, dp(1), 0, dp(2))  
                    }  
                    contentLayout.addView(infoText)  
                }  
  
                // 方案之间的分隔线  
                if (setIndex < response.collisionFreeSets.size - 1) {  
                    contentLayout.addView(createDivider(ctx))  
                }  
            }  
  
            // 无冲配队和逐队结果之间的粗分隔  
            val thickDivider = View(ctx).apply {  
                setBackgroundColor(0xFFFFD54F.toInt())  
                layoutParams = LinearLayout.LayoutParams(  
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(2)  
                ).apply { setMargins(0, dp(8), 0, dp(4)) }  
            }  
            contentLayout.addView(thickDivider)  
        }  
  
        // ===== 逐队独立结果 =====  
        val indTitle = TextView(ctx).apply {  
            text = "── 逐队独立结果 ──"  
            setTextColor(0xFF90CAF9.toInt())  
            textSize = 13f  
            gravity = Gravity.CENTER  
            setPadding(0, dp(4), 0, dp(4))  
        }  
        contentLayout.addView(indTitle)  
  
        for ((teamIndex, teamResult) in response.results.withIndex()) {  
            // 队伍标题  
            val teamTitle = TextView(ctx).apply {  
                text = "第${teamIndex + 1}队防守"  
                setTextColor(0xFFFFB74D.toInt())  
                textSize = 12f  
                setPadding(0, dp(6), 0, dp(2))  
            }  
            contentLayout.addView(teamTitle)  
  
            // 防守阵容图标  
            contentLayout.addView(createIconRow(ctx, teamResult.defenseIds))  
  
            if (teamResult.attacks.isEmpty()) {  
                val noResult = TextView(ctx).apply {  
                    text = "未查询到解法"  
                    setTextColor(0xFFEF5350.toInt())  
                    textSize = 11f  
                    setPadding(0, dp(4), 0, dp(4))  
                }  
                contentLayout.addView(noResult)  
            } else {  
                // 显示前5条进攻推荐  
                val maxShow = minOf(teamResult.attacks.size, 5)  
                for (i in 0 until maxShow) {  
                    contentLayout.addView(createAttackItem(ctx, teamResult.attacks[i], i < maxShow - 1))  
                }  
            }  
  
            // 队伍之间的分隔线  
            if (teamIndex < response.results.size - 1) {  
                contentLayout.addView(createDivider(ctx))  
            }  
        }  
  
        scrollView.addView(contentLayout)  
        root.addView(scrollView)  
        root.addView(createBottomRow(ctx))  
  
        val params = WindowManager.LayoutParams(  
            dp(340), WindowManager.LayoutParams.WRAP_CONTENT,  
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,  
            PixelFormat.TRANSLUCENT  
        ).apply {  
            gravity = Gravity.CENTER  
        }  
  
        windowManager.addView(root, params)  
        resultPanel = root  
    }  
  
    // ======================== 通用 UI 组件 ========================  
  
    private fun createAttackItem(ctx: Context, r: ArenaQueryClient.ArenaResult, showDivider: Boolean): LinearLayout {  
        val itemLayout = LinearLayout(ctx).apply {  
            orientation = LinearLayout.VERTICAL  
            setPadding(0, dp(4), 0, dp(4))  
        }  
  
        itemLayout.addView(createIconRow(ctx, r.atkUnits))  
  
        val typeLabel = when {  
            r.teamType.startsWith("approximation") -> " [近似]"  
            r.teamType == "frequency" -> " [频率推荐]"  
            else -> ""  
        }  
        val infoText = TextView(ctx).apply {  
            text = "\uD83D\uDC4D${r.upVote}  \uD83D\uDC4E${r.downVote}  评分:${"%.1f".format(r.score)}$typeLabel"  
            setTextColor(Color.LTGRAY)  
            textSize = 11f  
            setPadding(0, dp(2), 0, 0)  
        }  
        itemLayout.addView(infoText)  
  
        if (showDivider) {  
            val itemDivider = View(ctx).apply {  
                setBackgroundColor(0xFF444444.toInt())  
                layoutParams = LinearLayout.LayoutParams(  
                    ViewGroup.LayoutParams.MATCH_PARENT, 1  
                ).apply { setMargins(0, dp(4), 0, 0) }  
            }  
            itemLayout.addView(itemDivider)  
        }  
  
        return itemLayout  
    }  
  
    private fun createDivider(ctx: Context): View {  
        return View(ctx).apply {  
            setBackgroundColor(Color.GRAY)  
            layoutParams = LinearLayout.LayoutParams(  
                ViewGroup.LayoutParams.MATCH_PARENT, 1  
            ).apply { setMargins(0, dp(6), 0, dp(6)) }  
        }  
    }  
  
    private fun createBottomRow(ctx: Context): LinearLayout {  
        return LinearLayout(ctx).apply {  
            orientation = LinearLayout.HORIZONTAL  
            gravity = Gravity.CENTER  
            setPadding(0, dp(6), 0, 0)  
  
            addView(TextView(ctx).apply {  
                text = "重新截图"  
                setTextColor(0xFF90CAF9.toInt())  
                textSize = 13f  
                setPadding(dp(16), dp(6), dp(16), dp(6))  
                setOnClickListener {  
                    removeResultPanel()  
                    onFloatButtonClick()  
                }  
            })  
            addView(TextView(ctx).apply {  
                text = "关闭"  
                setTextColor(Color.LTGRAY)  
                textSize = 13f  
                setPadding(dp(16), dp(6), dp(16), dp(6))  
                setOnClickListener { removeResultPanel() }  
            })  
        }  
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
  
    @SuppressLint("ClickableViewAccessibility")  
	private fun makePanelDraggable(dragHandle: View, panel: View, params: WindowManager.LayoutParams) {  
		var initialX = 0  
		var initialY = 0  
		var initialTouchX = 0f  
		var initialTouchY = 0f  
	  
		dragHandle.setOnTouchListener { _, event ->  
			when (event.action) {  
				MotionEvent.ACTION_DOWN -> {  
					initialX = params.x  
					initialY = params.y  
					initialTouchX = event.rawX  
					initialTouchY = event.rawY  
					true  
				}  
				MotionEvent.ACTION_MOVE -> {  
					params.x = initialX + (event.rawX - initialTouchX).toInt()  
					params.y = initialY + (event.rawY - initialTouchY).toInt()  
					try {  
						windowManager.updateViewLayout(panel, params)  
					} catch (_: Exception) {}  
					true  
				}  
				else -> false  
			}  
		}  
	}
	
	private fun dp(value: Int): Int {  
        return TypedValue.applyDimension(  
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),  
            resources.displayMetrics  
        ).toInt()  
    }  
}