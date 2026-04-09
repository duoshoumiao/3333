package com.pcrjjc.app.service  
  
import android.app.Notification  
import android.app.Service  
import android.content.Intent  
import android.os.IBinder  
import android.util.Log  
import androidx.core.app.NotificationCompat  
import com.pcrjjc.app.PcrJjcApp  
import com.pcrjjc.app.R  
import com.pcrjjc.app.data.local.dao.AccountDao  
import com.pcrjjc.app.data.local.dao.BindDao  
import com.pcrjjc.app.data.local.dao.HistoryDao  
import com.pcrjjc.app.data.remote.BiliAuth  
import com.pcrjjc.app.data.remote.PcrClient  
import com.pcrjjc.app.data.remote.TwPcrClient  
import com.pcrjjc.app.domain.QueryEngine  
import com.pcrjjc.app.domain.RankMonitor  
import com.pcrjjc.app.util.Platform  
import dagger.hilt.android.AndroidEntryPoint  
import kotlinx.coroutines.*  
import javax.inject.Inject  
  
@AndroidEntryPoint  
class RankMonitorService : Service() {  
  
    companion object {  
        const val NOTIFICATION_ID = 1  
        const val TAG = "RankMonitorService"  
        const val EXTRA_INTERVAL_MS = "interval_ms"  
        const val DEFAULT_INTERVAL_MS = 1000L  // 默认1秒  
    }  
  
    @Inject lateinit var accountDao: AccountDao  
    @Inject lateinit var bindDao: BindDao  
    @Inject lateinit var historyDao: HistoryDao  
  
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)  
    private var pollingJob: Job? = null  
    private var intervalMs: Long = DEFAULT_INTERVAL_MS  
  
    override fun onBind(intent: Intent?): IBinder? = null  
  
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {  
        intervalMs = intent?.getLongExtra(EXTRA_INTERVAL_MS, DEFAULT_INTERVAL_MS) ?: DEFAULT_INTERVAL_MS  
        val notification = createNotification()  
        startForeground(NOTIFICATION_ID, notification)  
        startPolling()  
        return START_STICKY  
    }  
  
    private fun startPolling() {  
        pollingJob?.cancel()  
        pollingJob = serviceScope.launch {  
            val queryEngine = QueryEngine()  
            val rankMonitor = RankMonitor(applicationContext, historyDao, bindDao)  
  
            while (isActive) {  
                try {  
                    val accounts = accountDao.getAllAccountsSync()  
                    for (account in accounts) {  
                        try {  
                            val binds = bindDao.getBindsByPlatformSync(account.platform)  
                            if (binds.isEmpty()) continue  
  
                            val client: Any = when (account.platform) {  
                                Platform.TW_SERVER.id -> {  
                                    val twPlatform = account.viewerId.toLong() / 1000000000  
                                    val twClient = TwPcrClient(  
                                        account.account, account.password,  
                                        account.viewerId, twPlatform.toInt()  
                                    )  
                                    twClient.login()  
                                    twClient  
                                }  
                                else -> {  
                                    val biliAuth = BiliAuth(account.account, account.password, account.platform)  
                                    val pcrClient = PcrClient(biliAuth)  
                                    pcrClient.login()  
                                    pcrClient  
                                }  
                            }  
  
                            queryEngine.queryAll(binds, client) { result ->  
                                rankMonitor.processResult(result)  
                            }  
                        } catch (e: Exception) {  
                            Log.e(TAG, "Error querying platform ${account.platform}: ${e.message}", e)  
                        }  
                    }  
                    rankMonitor.flushHistories()  
                } catch (e: Exception) {  
                    Log.e(TAG, "Polling error: ${e.message}", e)  
                }  
                delay(intervalMs)  // 1秒间隔  
            }  
        }  
    }  
  
    override fun onDestroy() {  
        pollingJob?.cancel()  
        serviceScope.cancel()  
        super.onDestroy()  
    }  
  
    private fun createNotification(): Notification {  
        return NotificationCompat.Builder(this, PcrJjcApp.SERVICE_CHANNEL_ID)  
            .setSmallIcon(R.drawable.ic_notification)  
            .setContentTitle("竞技场监控")  
            .setContentText("正在监控竞技场排名变动（${intervalMs/1000}秒间隔）...")  
            .setPriority(NotificationCompat.PRIORITY_LOW)  
            .setOngoing(true)  
            .build()  
    }  
}
