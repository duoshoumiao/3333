package com.pcrjjc.app  
  
import android.app.Application  
import android.app.NotificationChannel  
import android.app.NotificationManager  
import android.content.Intent  
import android.os.Build  
import android.util.Log  
import androidx.hilt.work.HiltWorkerFactory  
import androidx.work.Configuration  
import com.pcrjjc.app.data.local.SettingsDataStore  
import com.pcrjjc.app.service.RankMonitorService  
import dagger.hilt.android.HiltAndroidApp  
import kotlinx.coroutines.CoroutineScope  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.SupervisorJob  
import kotlinx.coroutines.flow.first  
import kotlinx.coroutines.launch  
import javax.inject.Inject  
  
@HiltAndroidApp  
class PcrJjcApp : Application(), Configuration.Provider {  
  
    @Inject  
    lateinit var workerFactory: HiltWorkerFactory  
  
    @Inject  
    lateinit var settingsDataStore: SettingsDataStore  
  
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)  
  
    override fun onCreate() {  
        super.onCreate()  
        createNotificationChannels()  
        restoreMonitoringState()  
    }  
  
    override val workManagerConfiguration: Configuration  
        get() = Configuration.Builder()  
            .setWorkerFactory(workerFactory)  
            .build()  
  
    /**  
     * App 启动时检查之前的监控状态，如果之前开启了监控则自动恢复  
     */  
    private fun restoreMonitoringState() {  
        appScope.launch {  
            try {  
                val isEnabled = settingsDataStore.isMonitoringEnabledFlow.first()  
                if (isEnabled) {  
                    val interval = settingsDataStore.pollingIntervalFlow.first()  
                    Log.i("PcrJjcApp", "恢复排名监控，间隔 ${interval} 秒")  
                    val intent = Intent(this@PcrJjcApp, RankMonitorService::class.java)  
                    intent.putExtra(RankMonitorService.EXTRA_INTERVAL_SECONDS, interval)  
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  
                        startForegroundService(intent)  
                    } else {  
                        startService(intent)  
                    }  
                }  
            } catch (e: Exception) {  
                Log.e("PcrJjcApp", "恢复监控状态失败: ${e.message}", e)  
            }  
        }  
    }  
  
    private fun createNotificationChannels() {  
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  
            val rankChannel = NotificationChannel(  
                RANK_CHANNEL_ID,  
                getString(R.string.notification_channel_name),  
                NotificationManager.IMPORTANCE_DEFAULT  
            ).apply {  
                description = getString(R.string.notification_channel_desc)  
            }  
  
            val serviceChannel = NotificationChannel(  
                SERVICE_CHANNEL_ID,  
                getString(R.string.foreground_channel_name),  
                NotificationManager.IMPORTANCE_LOW  
            ).apply {  
                description = getString(R.string.foreground_channel_desc)  
            }  
  
            val notificationManager = getSystemService(NotificationManager::class.java)  
            notificationManager.createNotificationChannel(rankChannel)  
            notificationManager.createNotificationChannel(serviceChannel)  
        }  
    }  
  
    companion object {  
        const val RANK_CHANNEL_ID = "rank_change_channel"  
        const val SERVICE_CHANNEL_ID = "service_channel"  
    }  
}