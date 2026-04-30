package com.pcrjjc.app.domain  
  
import android.app.NotificationManager  
import android.content.Context  
import android.util.Log  
import androidx.core.app.NotificationCompat  
import com.pcrjjc.app.PcrJjcApp  
import com.pcrjjc.app.R  
import com.pcrjjc.app.data.local.dao.BindDao  
import com.pcrjjc.app.data.local.dao.HistoryDao  
import com.pcrjjc.app.data.local.dao.RankCacheDao  
import com.pcrjjc.app.data.local.entity.JjcHistory  
import com.pcrjjc.app.data.local.entity.PcrBind  
import com.pcrjjc.app.data.local.entity.RankCache  
import com.pcrjjc.app.util.NoticeType  
import com.pcrjjc.app.data.local.SettingsDataStore  
import com.pcrjjc.app.util.EmailSender  
import kotlinx.coroutines.CoroutineScope  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap  
  
class RankMonitor(  
    private val context: Context,  
    private val historyDao: HistoryDao,  
    private val bindDao: BindDao,  
    private val rankCacheDao: RankCacheDao,  
    private val settingsDataStore: SettingsDataStore  
) { 
    companion object {  
        private const val TAG = "RankMonitor"  
        private var notificationId = 1000  
    }  
  
    private val cache = ConcurrentHashMap<Pair<Long, Int>, IntArray>()
    private val pendingHistories = mutableListOf<JjcHistory>()
    private val pendingNotifications = mutableListOf<Pair<String, NoticeType>>()
  
    /**  
     * 从数据库加载已有的排名缓存到内存，避免 Service 重启后丢失比较基准  
     */  
    suspend fun initCacheFromDb() {  
        val allCaches = rankCacheDao.getAll()  
        for (rc in allCaches) {  
            cache[Pair(rc.pcrid, rc.platform)] = intArrayOf(rc.arenaRank, rc.grandArenaRank, rc.lastLoginTime)  
        }  
        Log.i(TAG, "Initialized cache from DB with ${allCaches.size} entries")  
    }  
  
suspend fun processResult(result: QueryEngine.QueryResult) {
        val bind = result.bind
        val userInfo = result.userInfo

        val arenaRank = (userInfo["arena_rank"] as? Number)?.toInt() ?: return
        val grandArenaRank = (userInfo["grand_arena_rank"] as? Number)?.toInt() ?: return
        val lastLoginTime = (userInfo["last_login_time"] as? Number)?.toInt() ?: 0

        val cacheKey = Pair(bind.pcrid, bind.platform)
        val current = intArrayOf(arenaRank, grandArenaRank, lastLoginTime)

        // Always write to database for UI display
        rankCacheDao.upsert(
            RankCache(
                pcrid = bind.pcrid,
                platform = bind.platform,
                arenaRank = arenaRank,
                grandArenaRank = grandArenaRank,
                lastLoginTime = lastLoginTime
            )
        )

        val previous = cache[cacheKey]
        if (previous == null) {
            cache[cacheKey] = current
            return
        }

        // 检查排名是否有变化，直接发送通知（保持原有行为）
        if (current[0] != previous[0]) {
            handleRankChange(current[0], previous[0], bind, NoticeType.JJC, addToPending = false)
        }
        if (current[1] != previous[1]) {
            handleRankChange(current[1], previous[1], bind, NoticeType.PJJC, addToPending = false)
        }
        if (current[2] != previous[2]) {
            handleRankChange(current[2], previous[2], bind, NoticeType.ONLINE, addToPending = false)
        }

        // 更新缓存
        cache[cacheKey] = current
    }

    /**
     * 批量处理查询结果，全部查询完后再统一推送
     */
    suspend fun processResults(results: List<QueryEngine.QueryResult>) {
        // 先处理所有结果，收集排名变化到待推送列表
        for (result in results) {
            processResultInternal(result)
        }

        // 批量发送通知
        flushNotifications()
    }

    private suspend fun processResultInternal(result: QueryEngine.QueryResult) {
        val bind = result.bind
        val userInfo = result.userInfo

        val arenaRank = (userInfo["arena_rank"] as? Number)?.toInt() ?: return
        val grandArenaRank = (userInfo["grand_arena_rank"] as? Number)?.toInt() ?: return
        val lastLoginTime = (userInfo["last_login_time"] as? Number)?.toInt() ?: 0

        val cacheKey = Pair(bind.pcrid, bind.platform)
        val current = intArrayOf(arenaRank, grandArenaRank, lastLoginTime)

        // Always write to database for UI display
        rankCacheDao.upsert(
            RankCache(
                pcrid = bind.pcrid,
                platform = bind.platform,
                arenaRank = arenaRank,
                grandArenaRank = grandArenaRank,
                lastLoginTime = lastLoginTime
            )
        )

        val previous = cache[cacheKey]
        if (previous == null) {
            cache[cacheKey] = current
            return
        }

        // 检查排名是否有变化，添加到待推送列表
        if (current[0] != previous[0]) {
            handleRankChange(current[0], previous[0], bind, NoticeType.JJC, addToPending = true)
        }
        if (current[1] != previous[1]) {
            handleRankChange(current[1], previous[1], bind, NoticeType.PJJC, addToPending = true)
        }
        if (current[2] != previous[2]) {
            handleRankChange(current[2], previous[2], bind, NoticeType.ONLINE, addToPending = true)
        }

        // 更新缓存
        cache[cacheKey] = current
    }

    private suspend fun handleRankChange(
		new: Int, old: Int, bind: PcrBind, noticeType: NoticeType, addToPending: Boolean = false
	) {
		val timestamp = System.currentTimeMillis() / 1000

		if (noticeType == NoticeType.ONLINE) {
			if (bind.onlineNotice == 0) return
			val msg = "${bind.name ?: bind.pcrid} 上线了！"
			if (addToPending) {
				synchronized(pendingNotifications) { pendingNotifications.add(Pair(msg, noticeType)) }
			} else {
				sendNotification(msg, noticeType)
			}
			Log.i(TAG, "Send Notice: $msg")
			return
		}

		// 无论通知开关如何，始终记录历史
		val isJjc = noticeType == NoticeType.JJC
		val prefix = if (isJjc) "jjc: " else "pjjc: "
		val change = if (new < old) {
			"$prefix$old->$new [▲${old - new}]"
		} else {
			"$prefix$old->$new [▽${new - old}]"
		}

		val history = JjcHistory(
			pcrid = bind.pcrid, name = bind.name ?: "",
			platform = bind.platform, date = timestamp,
			item = if (isJjc) 0 else 1, before = old, after = new
		)
		synchronized(pendingHistories) { pendingHistories.add(history) }

		// 通知开关只控制是否发送通知
		val shouldNotify = if (isJjc) bind.jjcNotice else bind.pjjcNotice
		if (!shouldNotify) return
		if (!bind.upNotice && new < old) return

		val msg = "${bind.name ?: bind.pcrid} $change"
		if (addToPending) {
			synchronized(pendingNotifications) { pendingNotifications.add(Pair(msg, noticeType)) }
		} else {
			sendNotification(msg, noticeType)
		}
		Log.i(TAG, "Send Notice: $msg")
	}  
  
suspend fun flushHistories() {
        val toInsert: List<JjcHistory>
        synchronized(pendingHistories) {
            toInsert = pendingHistories.toList()
            pendingHistories.clear()
        }
        if (toInsert.isNotEmpty()) { historyDao.insertAll(toInsert) }
    }

    /**
     * 批量发送待推送的通知
     */
    private fun flushNotifications() {
        val toSend: List<Pair<String, NoticeType>>
        synchronized(pendingNotifications) {
            toSend = pendingNotifications.toList()
            pendingNotifications.clear()
        }
        for ((message, noticeType) in toSend) {
            sendNotification(message, noticeType)
        }
    }
  
    private fun sendNotification(message: String, noticeType: NoticeType) {  
        val title = when (noticeType) {  
            NoticeType.JJC -> "竞技场排名变动"  
            NoticeType.PJJC -> "公主竞技场排名变动"  
            NoticeType.ONLINE -> "上线提醒"  
        }  
        val notification = NotificationCompat.Builder(context, PcrJjcApp.RANK_CHANNEL_ID)  
            .setSmallIcon(R.drawable.ic_notification)  
            .setContentTitle(title)  
            .setContentText(message)  
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)  
            .setAutoCancel(true)  
            .build()  
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager  
        nm.notify(notificationId++, notification)  
  
        // 邮箱推送（异步，不阻塞）  
        CoroutineScope(Dispatchers.IO).launch {  
            try {  
                if (settingsDataStore.isEmailPushEnabled()) {  
                    val email = settingsDataStore.getEmailAddress()  
                    val authCode = settingsDataStore.getEmailAuthCode()  
                    if (email.isNotBlank() && authCode.isNotBlank()) {  
                        EmailSender.sendEmail(email, authCode, title, message)  
                    }  
                }  
            } catch (e: Exception) {  
                Log.e(TAG, "Email push failed: ${e.message}", e)  
            }  
        }  
    }  
  
    fun getCachedRank(pcrid: Long, platform: Int): IntArray? {  
        return cache[Pair(pcrid, platform)]  
    }  
}