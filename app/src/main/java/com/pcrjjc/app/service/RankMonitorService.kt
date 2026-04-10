package com.pcrjjc.app.service  
  
import android.Manifest  
import android.app.Notification  
import android.app.Service  
import android.content.Intent  
import android.content.pm.PackageManager  
import android.content.pm.ServiceInfo  
import android.os.Build  
import android.os.IBinder  
import android.util.Log  
import androidx.core.app.NotificationCompat  
import androidx.core.app.ServiceCompat  
import androidx.core.content.ContextCompat  
import com.pcrjjc.app.PcrJjcApp  
import com.pcrjjc.app.R  
import com.pcrjjc.app.data.local.dao.AccountDao  
import com.pcrjjc.app.data.local.dao.BindDao  
import com.pcrjjc.app.data.local.dao.HistoryDao  
import com.pcrjjc.app.data.local.dao.RankCacheDao  
import com.pcrjjc.app.domain.ClientManager  
import com.pcrjjc.app.domain.QueryEngine  
import com.pcrjjc.app.domain.RankMonitor  
import dagger.hilt.android.AndroidEntryPoint  
import kotlinx.coroutines.CoroutineScope  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.Job  
import kotlinx.coroutines.SupervisorJob  
import kotlinx.coroutines.cancel  
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
    @Inject lateinit var rankCacheDao: RankCacheDao  
    @Inject lateinit var clientManager: ClientManager  
  
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)  
    private var pollingJob: Job? = null  
  
    override fun onBind(intent: Intent?): IBinder? = null  
  
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {  
        val intervalSeconds = intent?.getLongExtra(EXTRA_INTERVAL_SECONDS, 30) ?: 30  
  
        // Check notification permission on Android 13+  
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {  
            if (ContextCompat.checkSelfPermission(  
                    this, Manifest.permission.POST_NOTIFICATIONS  
                ) != PackageManager.PERMISSION_GRANTED  
            ) {  
                Log.w(TAG, "通知权限未授予，停止前台服务")  
                stopSelf()  
                return START_NOT_STICKY  
            }  
        }  
  
        // Start foreground  
        val notification = createNotification(intervalSeconds)  
        try {  
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {  
                ServiceCompat.startForeground(  
                    this, NOTIFICATION_ID, notification,  
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC  
                )  
            } else {  
                startForeground(NOTIFICATION_ID, notification)  
            }  
        } catch (e: Exception) {  
            Log.e(TAG, "启动前台服务失败", e)  
            stopSelf()  
            return START_NOT_STICKY  
        }  
  
        // Cancel previous polling job and start new one  
        pollingJob?.cancel()  
        pollingJob = serviceScope.launch {  
            Log.i(TAG, "开始轮询，间隔 ${intervalSeconds} 秒")  
            val queryEngine = QueryEngine()  
            val rankMonitor = RankMonitor(this@RankMonitorService, historyDao, bindDao, rankCacheDao)  
  
            while (isActive) {  
                try {  
                    val accounts = accountDao.getAllAccountsSync()  
                    if (accounts.isEmpty()) {  
                        Log.w(TAG, "No accounts configured, waiting...")  
                    } else {  
                        for (account in accounts) {  
                            if (!isActive) break  
                            try {  
                                val binds = bindDao.getBindsByPlatformSync(account.platform)  
                                if (binds.isEmpty()) continue  
  
                                val client = clientManager.getClient(account)  
  
                                queryEngine.queryAll(binds, client) { result ->  
                                    rankMonitor.processResult(result)  
                                }  
                            } catch (e: Exception) {  
                                Log.e(TAG, "Error querying platform ${account.platform}: ${e.message}", e)  
                                clientManager.clearClient(account.id)  
                            }  
                        }  
                        rankMonitor.flushHistories()  
                    }  
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
        serviceScope.cancel()  
        Log.i(TAG, "Service destroyed, polling stopped")  
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