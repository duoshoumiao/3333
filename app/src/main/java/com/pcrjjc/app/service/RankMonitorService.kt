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
import kotlinx.coroutines.CoroutineScope  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.Job  
import kotlinx.coroutines.SupervisorJob  
import kotlinx.coroutines.delay  
import kotlinx.coroutines.isActive  
import kotlinx.coroutines.launch  
import javax.inject.Inject  
  
@AndroidEntryPoint  
class RankMonitorService : Service() {  
  
    companion object {  
        const val NOTIFICATION_ID = 1  
        const val EXTRA_INTERVAL_SECONDS = "extra_interval_seconds"  
        private const val TAG = "RankMonitorService"  
    }  
  
    @Inject lateinit var accountDao: AccountDao  
    @Inject lateinit var bindDao: BindDao  
    @Inject lateinit var historyDao: HistoryDao  
  
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)  
    private var pollingJob: Job? = null  
  
    override fun onBind(intent: Intent?): IBinder? = null  
  
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {  
        val intervalSeconds = intent?.getLongExtra(EXTRA_INTERVAL_SECONDS, 30) ?: 30  
        val notification = createNotification(intervalSeconds)  
        startForeground(NOTIFICATION_ID, notification)  
  
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
                                    val twPlatform = (account.viewerId.toLong() / 1000000000).toInt()  
                                    val twClient = TwPcrClient(  
                                        account.account, account.password,  
                                        account.viewerId, twPlatform  
                                    )  
                                    twClient.login()  
                                    twClient  
                                }  
                                else -> {  
                                    val biliAuth = BiliAuth(  
                                        account.account, account.password, account.platform  
                                    )  
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
                    Log.e(TAG, "Polling cycle failed: ${e.message}", e)  
                }  
                delay(intervalSeconds * 1000)  
            }  
        }  
        return START_STICKY  
    }  
  
    override fun onDestroy() {  
        super.onDestroy()  
        pollingJob?.cancel()  
    }  
  
    private fun createNotification(intervalSeconds: Long): Notification {  
        return NotificationCompat.Builder(this, PcrJjcApp.SERVICE_CHANNEL_ID)  
            .setSmallIcon(R.drawable.ic_notification)  
            .setContentTitle("竞技场监控")  
            .setContentText("正在监控排名变动，间隔 ${intervalSeconds} 秒")  
            .setPriority(NotificationCompat.PRIORITY_LOW)  
            .setOngoing(true)  
            .build()  
    }  
}