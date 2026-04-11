package com.pcrjjc.app  
  
import android.app.Application  
import android.app.NotificationChannel  
import android.app.NotificationManager  
import android.content.Intent  
import android.os.Build  
import androidx.hilt.work.HiltWorkerFactory  
import androidx.work.Configuration  
import com.pcrjjc.app.data.local.SettingsDataStore  
import com.pcrjjc.app.service.RankMonitorService  
import dagger.hilt.android.HiltAndroidApp  
import kotlinx.coroutines.CoroutineScope  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.launch  
import javax.inject.Inject  
  
@HiltAndroidApp  
class PcrJjcApp : Application(), Configuration.Provider {  
  
    @Inject  
    lateinit var workerFactory: HiltWorkerFactory  
  
    @Inject  
    lateinit var settingsDataStore: SettingsDataStore  
  
    override fun onCreate() {  
        super.onCreate()  
        createNotificationChannels()  
        restoreMonitoringState()  
    }  
  
    override val workManagerConfiguration: Configuration  
        get() = Configuration.Builder()  
            .setWorkerFactory(workerFactory)  
            .build()  
  
    private fun restoreMonitoringState() {  
        CoroutineScope(Dispatchers.IO).launch {  
            val enabled = settingsDataStore.isMonitoringEnabledSync()  
            if (enabled) {  
                val interval = settingsDataStore.getPollingIntervalSync()  
                val intent = Intent(this@PcrJjcApp, RankMonitorService::class.java)  
                intent.putExtra(RankMonitorService.EXTRA_INTERVAL_SECONDS, interval)  
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  
                    startForegroundService(intent)  
                } else {  
                    startService(intent)  
                }  
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
  
            val captchaChannel = NotificationChannel(  
                CAPTCHA_CHANNEL_ID,  
                "验证码通知",  
                NotificationManager.IMPORTANCE_HIGH  
            ).apply {  
                description = "登录需要手动验证码时的通知"  
            }  
  
            val notificationManager = getSystemService(NotificationManager::class.java)  
            notificationManager.createNotificationChannel(rankChannel)  
            notificationManager.createNotificationChannel(serviceChannel)  
            notificationManager.createNotificationChannel(captchaChannel)  
        }  
    }  
  
    companion object {  
        const val RANK_CHANNEL_ID = "rank_change_channel"  
        const val SERVICE_CHANNEL_ID = "service_channel"  
        const val CAPTCHA_CHANNEL_ID = "captcha_channel"  
    }  
}