package com.pcrjjc.app.service    
  
import android.annotation.SuppressLint    
import android.app.Service    
import android.content.Intent    
import android.graphics.Color    
import android.graphics.PixelFormat    
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
import android.widget.LinearLayout    
import android.widget.ScrollView    
import android.widget.TextView    
import android.widget.Toast    
import com.pcrjjc.app.data.local.SettingsDataStore    
import com.pcrjjc.app.data.local.entity.ClanBattleState    
import com.pcrjjc.app.data.remote.RoomClient    
import kotlinx.coroutines.CoroutineScope    
import kotlinx.coroutines.Dispatchers    
import kotlinx.coroutines.Job    
import kotlinx.coroutines.cancel    
import kotlinx.coroutines.delay    
import kotlinx.coroutines.isActive    
import kotlinx.coroutines.launch    
import kotlinx.coroutines.withContext    
  
/**    
 * 会战状态浮窗服务    
 *    
 * 显示在所有应用上层，展示 Boss 状态、申请出刀、挂树、预约等信息。    
 * 定时从房间服务器轮询最新状态。    
 *    
 * 参考 FloatingWindowService.kt 的浮窗实现模式。    
 */    
class ClanBattleFloatingService : Service() {    
  
    companion object {    
        private const val TAG = "CBFloating"    
  
        @Volatile    
        var isRunning = false    
            private set    
  
        /** 外部可通过此方法直接更新浮窗文本（同进程内） */    
        @Volatile    
        var instance: ClanBattleFloatingService? = null    
            private set    
    }    
  
    private lateinit var windowManager: WindowManager    
    private var floatButton: View? = null    
    private var statusPanel: View? = null    
    private var statusTextView: TextView? = null    
    private val scope = CoroutineScope(Dispatchers.Main + Job())    
    private val handler = Handler(Looper.getMainLooper())    
  
    // 复用 OkHttpClient，避免每次轮询都新建实例    
    private val httpClient = okhttp3.OkHttpClient()    
  
    private var roomId: String = ""    
    private var currentText: String = ""    
    private var isExpanded = false    
  
    override fun onBind(intent: Intent?): IBinder? = null    
  
    override fun onCreate() {    
        super.onCreate()    
        isRunning = true    
        instance = this    
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager    
    }    
  
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {    
        roomId = intent?.getStringExtra("room_id") ?: ""    
        currentText = intent?.getStringExtra("floating_text") ?: "会战状态加载中..."    
  
        // 移除旧视图    
        removeFloatButton()    
        removeStatusPanel()    
  
        // 创建浮窗按钮    
        createFloatButton()    
  
        // 开始轮询房间状态    
        if (roomId.isNotBlank()) {    
            startPolling()    
        }    
  
        return START_NOT_STICKY    
    }    
  
    override fun onDestroy() {    
        super.onDestroy()    
        isRunning = false    
        instance = null    
        removeFloatButton()    
        removeStatusPanel()    
        scope.cancel()    
    }    
  
    // ======================== 浮动按钮（入口） ========================    
  
    @SuppressLint("ClickableViewAccessibility")    
    private fun createFloatButton() {    
        val button = TextView(this).apply {    
            text = "战"    
            textSize = 16f    
            setTextColor(Color.WHITE)    
            gravity = Gravity.CENTER    
            setBackgroundColor(0xDDFF5722.toInt()) // 橙红色    
            setPadding(dp(4), dp(4), dp(4), dp(4))    
        }    
  
        val size = dp(44)    
        val params = WindowManager.LayoutParams(    
            size, size,    
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,    
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or    
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,    
            PixelFormat.TRANSLUCENT    
        ).apply {    
            gravity = Gravity.TOP or Gravity.START    
            x = 0    
            y = dp(260) // 放在竞技场浮窗下方    
        }    
  
        var initialX = 0    
        var initialY = 0    
        var initialTouchX = 0f    
        var initialTouchY = 0f    
        var isDragging = false    
        val longPressRunnable = Runnable {    
            Toast.makeText(this, "会战浮窗已关闭", Toast.LENGTH_SHORT).show()    
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
                    try {    
                        windowManager.updateViewLayout(button, params)    
                    } catch (_: Exception) {}    
                    true    
                }    
                MotionEvent.ACTION_UP -> {    
                    handler.removeCallbacks(longPressRunnable)    
                    if (!isDragging) {    
                        toggleStatusPanel()    
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
  
    // ======================== 状态面板（展开/收起） ========================    
  
    private fun toggleStatusPanel() {    
        if (isExpanded) {    
            removeStatusPanel()    
            isExpanded = false    
        } else {    
            showStatusPanel()    
            isExpanded = true    
        }    
    }    
  
    @SuppressLint("ClickableViewAccessibility")    
    private fun showStatusPanel() {    
        removeStatusPanel()    
  
        val ctx = this    
        val root = LinearLayout(ctx).apply {    
            orientation = LinearLayout.VERTICAL    
            setBackgroundColor(0xF0222222.toInt())    
            setPadding(dp(10), dp(8), dp(10), dp(8))    
        }    
  
        // 标题栏（可拖动）    
        val titleRow = LinearLayout(ctx).apply {    
            orientation = LinearLayout.HORIZONTAL    
            gravity = Gravity.CENTER_VERTICAL    
            setPadding(dp(2), dp(2), dp(2), dp(4))    
        }    
        val titleText = TextView(ctx).apply {    
            text = "⠿ 会战状态"    
            setTextColor(Color.WHITE)    
            textSize = 13f    
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)    
        }    
        val closeBtn = TextView(ctx).apply {    
            text = "✕"    
            setTextColor(Color.LTGRAY)    
            textSize = 16f    
            setPadding(dp(8), 0, dp(4), 0)    
            setOnClickListener {    
                removeStatusPanel()    
                isExpanded = false    
            }    
        }    
        titleRow.addView(titleText)    
        titleRow.addView(closeBtn)    
        root.addView(titleRow)    
  
        // 分隔线    
        val divider = View(ctx).apply {    
            setBackgroundColor(Color.GRAY)    
            layoutParams = LinearLayout.LayoutParams(    
                ViewGroup.LayoutParams.MATCH_PARENT, 1    
            ).apply { setMargins(0, dp(2), 0, dp(4)) }    
        }    
        root.addView(divider)    
  
        // 可滚动的状态内容    
        val screenHeight = resources.displayMetrics.heightPixels    
        val maxScrollHeight = (screenHeight * 0.5).toInt()    
  
        val scrollView = ScrollView(ctx).apply {    
            layoutParams = LinearLayout.LayoutParams(    
                ViewGroup.LayoutParams.MATCH_PARENT,    
                maxScrollHeight    
            )    
        }    
  
        val contentText = TextView(ctx).apply {    
            text = currentText    
            setTextColor(Color.WHITE)    
            textSize = 11f    
            setLineSpacing(dp(2).toFloat(), 1f)    
            setPadding(dp(2), 0, dp(2), 0)    
        }    
        statusTextView = contentText    
  
        scrollView.addView(contentText)    
        root.addView(scrollView)    
  
        // 底部提示    
        val hintText = TextView(ctx).apply {    
            text = "长按「战」按钮关闭浮窗"    
            setTextColor(0xFF888888.toInt())    
            textSize = 9f    
            gravity = Gravity.CENTER    
            setPadding(0, dp(4), 0, 0)    
        }    
        root.addView(hintText)    
  
        val panelWidth = dp(240)    
        val params = WindowManager.LayoutParams(    
            panelWidth, WindowManager.LayoutParams.WRAP_CONTENT,    
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,    
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,    
            PixelFormat.TRANSLUCENT    
        ).apply {    
            gravity = Gravity.TOP or Gravity.START    
            x = dp(10)    
            y = dp(60)    
        }    
  
        windowManager.addView(root, params)    
        statusPanel = root    
  
        // 标题栏可拖动    
        makePanelDraggable(titleRow, root, params)    
    }    
  
    private fun removeStatusPanel() {    
        statusPanel?.let {    
            try { windowManager.removeView(it) } catch (_: Exception) {}    
        }    
        statusPanel = null    
        statusTextView = null    
    }    
  
    // ======================== 外部更新接口 ========================    
  
    /**    
     * 外部（如 ClanBattleViewModel）可直接调用此方法更新浮窗文本    
     */    
    fun updateText(text: String) {    
        currentText = text    
        handler.post {    
            statusTextView?.text = text    
        }    
    }    
  
    // ======================== 轮询房间状态 ========================    
  
    private fun startPolling() {    
        scope.launch {    
            val settingsDataStore = SettingsDataStore(this@ClanBattleFloatingService)    
            var lastTimestamp = 0L    
  
            while (isActive) {    
                delay(10000) // 每10秒轮询一次（兜底刷新，主要靠 ViewModel 直接推送）    
                try {    
                    val baseUrl = settingsDataStore.getRoomServerUrl() ?: continue    
                    val messages = withContext(Dispatchers.IO) {    
                        // 简单 HTTP 请求获取消息（不依赖 DI）    
                        val url = if (lastTimestamp > 0) {    
                            "$baseUrl/rooms/$roomId/messages?since=$lastTimestamp"    
                        } else {    
                            "$baseUrl/rooms/$roomId/messages"    
                        }    
                        val request = okhttp3.Request.Builder().url(url).get().build()    
                        val response = httpClient.newCall(request).execute()    
                        if (!response.isSuccessful) return@withContext emptyList()    
                        val body = response.body?.string() ?: return@withContext emptyList()    
                        val arr = org.json.JSONArray(body)    
                        val list = mutableListOf<Pair<Long, String>>()    
                        for (i in 0 until arr.length()) {    
                            val obj = arr.getJSONObject(i)    
                            list.add(obj.getLong("timestamp") to obj.getString("content"))    
                        }    
                        list    
                    }    
  
                    if (messages.isEmpty()) continue    
                    lastTimestamp = messages.maxOf { it.first }    
  
                    // 查找最新的 CB_STATE 消息    
                    for ((_, content) in messages.reversed()) {    
                        val cbState = ClanBattleState.fromMessage(content)    
                        if (cbState != null) {    
                            val newText = buildFloatingText(cbState)    
                            updateText(newText)    
                            break    
                        }    
                    }    
                } catch (e: Exception) {    
                    Log.w(TAG, "Polling failed", e)    
                }    
            }    
        }    
    }    
  
    /**    
     * 从 ClanBattleState 构建浮窗显示文本    
     */    
    private fun buildFloatingText(state: ClanBattleState): String {    
        val sb = StringBuilder()    
        sb.appendLine("当前排名：${if (state.rank > 0) state.rank.toString() else "--"}")    
        sb.appendLine("监控状态：${if (state.isMonitoring) "开启" else "关闭"}")    
        if (state.monitorPlayerName.isNotBlank()) {    
            sb.appendLine("监控人为：${state.monitorPlayerName}")    
        }    
        if (state.periodName.isNotBlank()) {    
            sb.appendLine("当前进度：${state.periodName}")    
        }    
  
        for (boss in state.bosses) {    
            if (boss.maxHp <= 0) continue    
            sb.append("${boss.lapNum}周目${boss.order}王: ")    
            sb.appendLine("HP: ${com.pcrjjc.app.util.formatBigNum(boss.currentHp)}/${com.pcrjjc.app.util.formatBigNum(boss.maxHp)} ${com.pcrjjc.app.util.formatPercent(boss.hpPercent)}")    
  
            val applies = state.getAppliesForBoss(boss.order)    
            if (applies.isNotEmpty()) {    
                sb.appendLine("${applies.joinToString("·") { it.playerName }} ${applies.size}人申请出刀")    
            }    
  
            val trees = state.getTreesForBoss(boss.order)    
            if (trees.isNotEmpty()) {    
                sb.appendLine("${trees.joinToString("·") { it.playerName }} ${trees.size}人挂树中")    
            }    
  
            val subs = state.getSubscribesForBoss(boss.order)    
            if (subs.isNotEmpty()) {    
                for (sub in subs) {    
                    sb.appendLine("${sub.playerName} 预约了下一周")    
                }    
            }    
        }    
        return sb.toString().trimEnd()    
    }    
  
    // ======================== 工具 ========================    
  
    @SuppressLint("ClickableViewAccessibility")    
    private fun makePanelDraggable(    
        dragHandle: View,    
        panel: View,    
        params: WindowManager.LayoutParams    
    ) {    
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