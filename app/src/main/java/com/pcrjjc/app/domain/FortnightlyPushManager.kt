package com.pcrjjc.app.domain  
  
import android.Manifest  
import android.app.NotificationChannel  
import android.app.NotificationManager  
import android.app.PendingIntent  
import android.content.Context  
import android.content.Intent  
import android.content.pm.PackageManager  
import android.os.Build  
import android.util.Log  
import androidx.core.app.NotificationCompat  
import androidx.core.content.ContextCompat  
import com.pcrjjc.app.MainActivity  
import com.pcrjjc.app.PcrJjcApp  
import com.pcrjjc.app.R  
import com.pcrjjc.app.data.local.SettingsDataStore  
import com.pcrjjc.app.data.model.ClassifiedActivity  
import com.pcrjjc.app.data.repository.FortnightlyRepository  
import com.pcrjjc.app.util.EmailSender  
import dagger.hilt.android.qualifiers.ApplicationContext  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.withContext  
import java.text.SimpleDateFormat  
import java.util.Date  
import java.util.Locale  
import java.util.TimeZone  
import javax.inject.Inject  
import javax.inject.Singleton  
  
/**  
 * 半月刊「新活动系统性推送」。  
 *  
 * 触发链路：  
 * 1. [com.pcrjjc.app.worker.FortnightlyCheckWorker] 定时（15 分钟）检查一次；  
 * 2. App 启动时补一次；  
 * 3. 用户在半月刊页面手动刷新时，同步跑一次。  
 *  
 * 推送判定（仅针对用户已订阅的类别）：  
 * - **即将开始**：活动将在 [LEAD_SECONDS] 内开始（提前 30 分钟）；  
 * - **已开始**：活动刚开始不久（Worker 未及时运行时的补偿推送，窗口 [STARTED_GRACE_SECONDS]）；  
 * - **新活动预告**：data.json 中新出现、且 3 天内开始的活动。  
 *  
 * 更远的活动不推预告，等临近开始再推，避免半月刊一更新就刷屏。  
 *  
 * 去重：每个活动按 `pushKey#事件` 记录到 DataStore，同一个事件只推一次。  
 */  
@Singleton  
class FortnightlyPushManager @Inject constructor(  
    @ApplicationContext private val context: Context,  
    private val repository: FortnightlyRepository,  
    private val settingsDataStore: SettingsDataStore  
) {  
    companion object {  
        private const val TAG = "FortnightlyPush"  
  
        /** 开始前多久提醒 */  
        const val LEAD_SECONDS = 30 * 60L  
  
        /** 已经开始的补偿窗口 */  
        private const val STARTED_GRACE_SECONDS = 6 * 3600L  
  
        /** 「新活动预告」只对 3 天内开始的活动发，避免半月刊一更新就刷屏 */  
        private const val PREVIEW_WINDOW_SECONDS = 3 * 24 * 3600L  
  
        /** 两次网络刷新之间的最小间隔，避免 App 启动 + Worker 重复拉取 */  
        private const val MIN_REFRESH_INTERVAL_MS = 20 * 60 * 1000L  
  
        /** 推送记录上限 */  
        private const val MAX_NOTIFIED_KEYS = 800  
  
        private const val EVENT_NEW = "new"  
        private const val EVENT_SOON = "soon"  
        private const val EVENT_STARTED = "started"  
  
        private const val NOTIFICATION_ID_BASE = 2000  
    }  
  
    /** 一次检查的结果，便于 UI 给出反馈 */  
    data class PushResult(  
        val pushedCount: Int,  
        val categories: List<String>,  
        val message: String  
    ) {  
        val hasPush: Boolean get() = pushedCount > 0  
    }  
  
    /**  
     * 执行一次检查与推送。  
     *  
     * @param refresh 是否先从 GitHub 拉取最新 data.json  
     * @param seedOnly 仅建立去重基线、不发任何通知（首次启用订阅时使用）  
     * @param forceRefresh 忽略刷新限流，强制联网拉取（用户点「立即检查」时使用）  
     */  
    suspend fun checkAndPush(  
        refresh: Boolean = true,  
        seedOnly: Boolean = false,  
        forceRefresh: Boolean = false  
    ): PushResult =  
        withContext(Dispatchers.IO) {  
            try {  
                // 限流：距上次网络刷新不足 20 分钟就跳过，避免启动 + Worker 重复拉取  
                val sinceLastRefresh = System.currentTimeMillis() - settingsDataStore.getFortnightlyLastRefreshAt()  
                if (refresh && (forceRefresh || sinceLastRefresh > MIN_REFRESH_INTERVAL_MS)) {  
                    repository.refreshSilently()  
                    settingsDataStore.setFortnightlyLastRefreshAt(System.currentTimeMillis())  
                }  
  
                if (!settingsDataStore.isFortnightlyPushEnabled()) {  
                    return@withContext PushResult(0, emptyList(), "新活动推送已关闭")  
                }  
  
                val subscribed = settingsDataStore.getFortnightlySubscriptions()  
                if (subscribed.isEmpty()) {  
                    return@withContext PushResult(0, emptyList(), "还没有订阅任何类别")  
                }  
  
                val allActivities = repository.loadClassifiedList()  
                if (allActivities.isEmpty()) {  
                    settingsDataStore.setFortnightlyLastCheckAt(System.currentTimeMillis())  
                    return@withContext PushResult(0, emptyList(), "暂无活动数据")  
                }  
  
                val nowSec = System.currentTimeMillis() / 1000  
                val lastCheckMs = settingsDataStore.getFortnightlyLastCheckAt()  
                val notified = settingsDataStore.getFortnightlyNotifiedKeys()  
  
                // 首次运行（没有任何检查记录）只建立基线，避免一次推送几十条历史活动  
                val isFirstRun = lastCheckMs == 0L  
                if (seedOnly || isFirstRun) {  
                    val baseline = allActivities  
                        .filter { it.category in subscribed }  
                        .flatMap { baselineKeysFor(it, nowSec) }  
                    settingsDataStore.addFortnightlyNotifiedKeys(baseline, MAX_NOTIFIED_KEYS)  
                    settingsDataStore.setFortnightlyLastCheckAt(System.currentTimeMillis())  
                    Log.i(TAG, "首次运行，已建立去重基线 ${baseline.size} 条")  
                    return@withContext PushResult(  
                        0, emptyList(),  
                        if (seedOnly) "已建立推送基线" else "已开始跟踪订阅类别的新活动"  
                    )  
                }  
  
                // 只保留订阅类别、且还没结束的活动  
                val candidates = allActivities.filter {  
                    it.category in subscribed && it.endTimeSec > nowSec  
                }  
  
                val grouped = linkedMapOf<String, MutableList<PushEvent>>()  
  
                for (activity in candidates) {  
                    val keyNew = "${activity.pushKey}#$EVENT_NEW"  
                    val keySoon = "${activity.pushKey}#$EVENT_SOON"  
                    val keyStarted = "${activity.pushKey}#$EVENT_STARTED"  
  
                    val secondsToStart = activity.startTimeSec - nowSec  
  
                    when {  
                        // 活动即将开始 / 已经开始不久  
                        secondsToStart in -STARTED_GRACE_SECONDS..LEAD_SECONDS -> {  
                            if (keySoon !in notified && keyStarted !in notified) {  
                                val isStarted = secondsToStart <= 0  
                                grouped.getOrPut(activity.category) { mutableListOf() }  
                                    .add(  
                                        PushEvent(  
                                            activity = activity,  
                                            eventKey = if (isStarted) keyStarted else keySoon,  
                                            headline = if (isStarted) "已开始" else "即将开始"  
                                        )  
                                    )  
                            }  
                        }  
  
                        // 新出现、且 3 天内开始的活动 → 预告（更远的等临近再提醒）  
                        secondsToStart in (LEAD_SECONDS + 1)..PREVIEW_WINDOW_SECONDS -> {  
                            if (keyNew !in notified) {  
                                grouped.getOrPut(activity.category) { mutableListOf() }  
                                    .add(  
                                        PushEvent(  
                                            activity = activity,  
                                            eventKey = keyNew,  
                                            headline = "新活动预告"  
                                        )  
                                    )  
                            }  
                        }  
                    }  
                }  
  
                if (grouped.isEmpty()) {  
                    settingsDataStore.setFortnightlyLastCheckAt(System.currentTimeMillis())  
                    return@withContext PushResult(0, emptyList(), "订阅类别暂无新活动")  
                }  
  
                val pushedKeys = mutableListOf<String>()  
                var total = 0  
                for ((category, events) in grouped) {  
                    if (events.isEmpty()) continue  
                    sendNotification(category, events)  
                    total += events.size  
                    pushedKeys += events.map { it.eventKey }  
                }  
  
                settingsDataStore.addFortnightlyNotifiedKeys(pushedKeys, MAX_NOTIFIED_KEYS)  
                settingsDataStore.setFortnightlyLastCheckAt(System.currentTimeMillis())  
  
                // 邮件推送（异步失败不影响通知）  
                sendEmailIfEnabled(grouped)  
  
                val categoryText = grouped.keys.joinToString("、")  
                Log.i(TAG, "已推送 $total 条新活动，类别：$categoryText")  
                PushResult(  
                    pushedCount = total,  
                    categories = grouped.keys.toList(),  
                    message = "已推送 $total 条新活动（$categoryText）"  
                )  
            } catch (e: Exception) {  
                Log.e(TAG, "新活动检查失败: ${e.message}", e)  
                PushResult(0, emptyList(), "推送检查失败: ${e.message}")  
            }  
        }  
  
    private data class PushEvent(  
        val activity: ClassifiedActivity,  
        val eventKey: String,  
        val headline: String  
    )  
  
    /**  
     * 建立基线时应该标记为「已处理」的事件 key。  
     *  
     * 关键点：对未来才开始的订阅活动，**不能**把 soon / started 也标记掉，  
     * 否则用户永远收不到真正的「即将开始」提醒；只标记 new（预告已视为看过）。  
     * 对已经开始的活动，三种事件都标记，避免订阅瞬间补推一堆历史。  
     */  
    private fun baselineKeysFor(activity: ClassifiedActivity, nowSec: Long): List<String> {  
        val base = activity.pushKey  
        return if (activity.startTimeSec > nowSec) {  
            listOf("$base#$EVENT_NEW")  
        } else {  
            listOf("$base#$EVENT_NEW", "$base#$EVENT_SOON", "$base#$EVENT_STARTED")  
        }  
    }  
  
    /**  
     * 新订阅某个类别时，把该类别**当前已有**的活动记为已推送，  
     * 这样用户只会收到订阅之后才出现的新活动，不会一订阅就被历史活动刷屏。  
     */  
    suspend fun seedCategoryBaseline(category: String) = withContext(Dispatchers.IO) {  
        try {  
            val nowSec = System.currentTimeMillis() / 1000  
            val activities = repository.loadClassifiedList().filter { it.category == category }  
            if (activities.isEmpty()) return@withContext  
            val keys = activities.flatMap { baselineKeysFor(it, nowSec) }  
            settingsDataStore.addFortnightlyNotifiedKeys(keys, MAX_NOTIFIED_KEYS)  
            Log.i(TAG, "已为类别【$category】建立基线 ${activities.size} 条")  
        } catch (e: Exception) {  
            Log.e(TAG, "建立基线失败: ${e.message}", e)  
        }  
    }  
  
    private fun sendNotification(category: String, events: List<PushEvent>) {  
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager  
  
        // Android 13+ 需要运行时通知权限  
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&  
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=  
            PackageManager.PERMISSION_GRANTED  
        ) {  
            Log.w(TAG, "没有通知权限，跳过系统通知")  
            return  
        }  
  
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  
            val channel = NotificationChannel(  
                PcrJjcApp.FORTNIGHTLY_CHANNEL_ID,  
                "半月刊新活动",  
                NotificationManager.IMPORTANCE_DEFAULT  
            ).apply {  
                description = "订阅的半月刊类别有新活动时提醒"  
            }  
            manager.createNotificationChannel(channel)  
        }  
  
        val notificationId = NOTIFICATION_ID_BASE + kotlin.math.abs(category.hashCode() % 500)  
        val intent = Intent(context, MainActivity::class.java).apply {  
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP  
            putExtra(MainActivity.EXTRA_OPEN_FORTNIGHTLY, true)  
        }  
        val pendingIntent = PendingIntent.getActivity(  
            context,  
            notificationId,  
            intent,  
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE  
        )  
  
        val title = "【$category】${events.size} 条新活动"  
        val summary = events.joinToString("；") { "${it.activity.subName}（${it.headline}）" }  
  
        // InboxStyle 需要显式声明类型，否则 apply{} 返回 Unit 导致链式调用失败  
        val inboxStyle = NotificationCompat.InboxStyle()  
        events.take(6).forEach { event ->  
            val timeText = if (event.activity.startTimeText.isNotBlank()) {  
                "${event.headline} · ${event.activity.startTimeText}"  
            } else {  
                event.headline  
            }  
            inboxStyle.addLine("${event.activity.subName}  $timeText")  
        }  
        if (events.size > 6) {  
            inboxStyle.setSummaryText("等 ${events.size} 条")  
        }  
  
        val builder = NotificationCompat.Builder(context, PcrJjcApp.FORTNIGHTLY_CHANNEL_ID)  
            .setSmallIcon(R.drawable.ic_notification)  
            .setContentTitle(title)  
            .setContentText(summary)  
            .setStyle(inboxStyle)  
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)  
            .setAutoCancel(true)  
            .setContentIntent(pendingIntent)  
            .setGroup(PcrJjcApp.FORTNIGHTLY_CHANNEL_ID)  
  
        try {  
            manager.notify(notificationId, builder.build())  
        } catch (e: SecurityException) {  
            Log.w(TAG, "通知发送被拒绝: ${e.message}")  
        }  
    }  
  
    private suspend fun sendEmailIfEnabled(grouped: Map<String, List<PushEvent>>) {  
        try {  
            if (!settingsDataStore.isEmailPushEnabled()) return  
            val email = settingsDataStore.getEmailAddress()  
            val authCode = settingsDataStore.getEmailAuthCode()  
            if (email.isBlank() || authCode.isBlank()) return  
  
            val formatter = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).apply {  
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")  
            }  
            val nowText = formatter.format(Date())  
  
            val body = buildString {  
                appendLine("公主连结半月刊 · 订阅类别新活动")  
                appendLine("检查时间：$nowText")  
                appendLine()  
                for ((category, events) in grouped) {  
                    appendLine("【$category】")  
                    for (event in events) {  
                        val startText = if (event.activity.startTimeText.isNotBlank())  
                            event.activity.startTimeText else "未知"  
                        appendLine("  · ${event.activity.subName}")  
                        appendLine("    ${event.headline} · 开始时间 $startText")  
                    }  
                    appendLine()  
                }  
                appendLine("—— 由半月刊订阅推送自动发送")  
            }  
  
            EmailSender.sendEmail(email, authCode, "半月刊新活动提醒（${grouped.size} 个类别）", body)  
        } catch (e: Exception) {  
            Log.e(TAG, "邮件推送失败: ${e.message}", e)  
        }  
    }  
}  
