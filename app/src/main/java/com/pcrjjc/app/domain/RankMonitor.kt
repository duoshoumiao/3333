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
import java.util.concurrent.ConcurrentHashMap  
  
class RankMonitor(  
    private val context: Context,  
    private val historyDao: HistoryDao,  
    private val bindDao: BindDao,  
    private val rankCacheDao: RankCacheDao  
) {  
    companion object {  
        private const val TAG = "RankMonitor"  
        private var notificationId = 1000  
    }  
  
    private val cache = ConcurrentHashMap<Pair<Long, Int>, IntArray>()  
    private val pendingHistories = mutableListOf<JjcHistory>()  
    private var dbCacheLoaded = false  
  
    /** Load persisted rank cache from database into memory. */  
    suspend fun loadCacheFromDb() {  
        if (dbCacheLoaded) return  
        try {  
            val all = rankCacheDao.getAll()  
            for (rc in all) {  
                cache[Pair(rc.pcrid, rc.platform)] =  
                    intArrayOf(rc.arenaRank, rc.grandArenaRank, rc.lastLoginTime)  
            }  
            Log.i(TAG, "Loaded ${all.size} rank cache entries from database")  
        } catch (e: Exception) {  
            Log.e(TAG, "Failed to load rank cache from db: ${e.message}", e)  
        }  
        dbCacheLoaded = true  
    }  
  
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
            persistCache(bind.pcrid, bind.platform, current)  
            return  
        }  
  
        cache[cacheKey] = current  
        persistCache(bind.pcrid, bind.platform, current)  
  
        if (current[0] != previous[0]) {  
            handleRankChange(current[0], previous[0], bind, NoticeType.JJC)  
        }  
        if (current[1] != previous[1]) {  
            handleRankChange(current[1], previous[1], bind, NoticeType.PJJC)  
        }  
        if (current[2] != previous[2]) {  
            handleRankChange(current[2], previous[2], bind, NoticeType.ONLINE)  
        }  
    }  
  
    private suspend fun persistCache(pcrid: Long, platform: Int, values: IntArray) {  
        try {  
            rankCacheDao.upsert(  
                RankCache(  
                    pcrid = pcrid,  
                    platform = platform,  
                    arenaRank = values[0],  
                    grandArenaRank = values[1],  
                    lastLoginTime = values[2]  
                )  
            )  
        } catch (e: Exception) {  
            Log.e(TAG, "Failed to persist rank cache: ${e.message}", e)  
        }  
    }  
  
    private suspend fun handleRankChange(  
        new: Int, old: Int, bind: PcrBind, noticeType: NoticeType  
    ) {  
        val timestamp = System.currentTimeMillis() / 1000  
        val change: String  
        if (noticeType == NoticeType.ONLINE) {  
            if (bind.onlineNotice == 0) return  
            val timeDiff = new - old  
            if (timeDiff < (if (bind.onlineNotice == 3) 60 else 600)) {  
                cache[Pair(bind.pcrid, bind.platform)]?.let { it[2] = old }  
                return  
            }  
            change = "上线了！"  
        } else {  
            val isJjc = noticeType == NoticeType.JJC  
            val shouldNotify = if (isJjc) bind.jjcNotice else bind.pjjcNotice  
            if (!shouldNotify) return  
            if (!bind.upNotice && new > old) return  
  
            val prefix = if (isJjc) "jjc: " else "pjjc: "  
            change = if (new < old) {  
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
        }  
  
        val msg = "${bind.name ?: bind.pcrid} $change"  
        sendNotification(msg, noticeType)  
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
    }  
  
    fun getCachedRank(pcrid: Long, platform: Int): IntArray? {  
        return cache[Pair(pcrid, platform)]  
    }  
}