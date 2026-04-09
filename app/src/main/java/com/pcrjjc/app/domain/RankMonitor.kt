package com.pcrjjc.app.domain

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pcrjjc.app.PcrJjcApp
import com.pcrjjc.app.R
import com.pcrjjc.app.data.local.dao.BindDao
import com.pcrjjc.app.data.local.dao.HistoryDao
import com.pcrjjc.app.data.local.entity.JjcHistory
import com.pcrjjc.app.data.local.entity.PcrBind
import com.pcrjjc.app.util.NoticeType
import java.util.concurrent.ConcurrentHashMap

/**
 * Rank monitor corresponding to the rank monitoring logic in pcrjjc2/utils.py
 * Caches ranks, compares changes, writes history, sends Android notifications
 */
class RankMonitor(
    private val context: Context,
    private val historyDao: HistoryDao,
    private val bindDao: BindDao
) {
    companion object {
        private const val TAG = "RankMonitor"
        private var notificationId = 1000
    }

    // Cache: (pcrid, platform) -> [arenaRank, grandArenaRank, lastLoginTime]
    private val cache = ConcurrentHashMap<Pair<Long, Int>, IntArray>()
    private val pendingHistories = mutableListOf<JjcHistory>()

    /**
     * Process a query result and check for rank changes
     * Corresponds to query_rank() in utils.py
     */
    suspend fun processResult(result: QueryEngine.QueryResult) {
        val bind = result.bind
        val userInfo = result.userInfo

        val arenaRank = (userInfo["arena_rank"] as? Number)?.toInt() ?: return
        val grandArenaRank = (userInfo["grand_arena_rank"] as? Number)?.toInt() ?: return
        val lastLoginTime = (userInfo["last_login_time"] as? Number)?.toInt() ?: 0

        val cacheKey = Pair(bind.pcrid, bind.platform)
        val current = intArrayOf(arenaRank, grandArenaRank, lastLoginTime)

        val previous = cache[cacheKey]
        if (previous == null) {
            cache[cacheKey] = current
            return
        }

        // Update cache
        cache[cacheKey] = current

        // Check JJC rank change
        if (current[0] != previous[0]) {
            handleRankChange(current[0], previous[0], bind, NoticeType.JJC)
        }

        // Check PJJC rank change
        if (current[1] != previous[1]) {
            handleRankChange(current[1], previous[1], bind, NoticeType.PJJC)
        }

        // Check online status change
        if (current[2] != previous[2]) {
            handleRankChange(current[2], previous[2], bind, NoticeType.ONLINE)
        }
    }

    /**
     * Handle rank change notification
     * Corresponds to sendNotice() in utils.py
     */
    private suspend fun handleRankChange(
        new: Int,
        old: Int,
        bind: PcrBind,
        noticeType: NoticeType
    ) {
        val timestamp = System.currentTimeMillis() / 1000

        // Build notification message
        val change: String
        if (noticeType == NoticeType.ONLINE) {
            // Online notice logic
            if (bind.onlineNotice == 0) return
            val timeDiff = new - old
            if (timeDiff < (if (bind.onlineNotice == 3) 60 else 600)) {
                // Interval too short, skip
                cache[Pair(bind.pcrid, bind.platform)]?.let { it[2] = old }
                return
            }
            change = "上线了！"
        } else {
            // JJC/PJJC rank change
            val isJjc = noticeType == NoticeType.JJC
            val shouldNotify = if (isJjc) bind.jjcNotice else bind.pjjcNotice
            if (!shouldNotify) return
            if (!bind.upNotice && new > old) return // Only notify on rank up if upNotice is off

            val prefix = if (isJjc) "jjc: " else "pjjc: "
            change = if (new < old) {
                "$prefix$old->$new [▲${old - new}]"
            } else {
                "$prefix$old->$new [▽${new - old}]"
            }

            // Save history
            val history = JjcHistory(
                pcrid = bind.pcrid,
                name = bind.name ?: "",
                platform = bind.platform,
                date = timestamp,
                item = if (isJjc) 0 else 1,
                before = old,
                after = new
            )
            synchronized(pendingHistories) {
                pendingHistories.add(history)
            }
        }

        // Send Android notification
        val msg = "${bind.name ?: bind.pcrid} $change"
        sendNotification(msg, noticeType)
        Log.i(TAG, "Send Notice: $msg")
    }

    /**
     * Flush pending histories to database
     */
    suspend fun flushHistories() {
        val toInsert: List<JjcHistory>
        synchronized(pendingHistories) {
            toInsert = pendingHistories.toList()
            pendingHistories.clear()
        }
        if (toInsert.isNotEmpty()) {
            historyDao.insertAll(toInsert)
        }
    }

    /**
     * Send Android local notification
     */
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

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId++, notification)
    }

    fun getCachedRank(pcrid: Long, platform: Int): IntArray? {
        return cache[Pair(pcrid, platform)]
    }
}
