package com.pcrjjc.app  

import android.app.Application  
import android.app.NotificationChannel  
import android.app.NotificationManager  
import android.content.Intent  
import android.os.Build  
import androidx.hilt.work.HiltWorkerFactory  
import androidx.work.Configuration  
import com.pcrjjc.app.data.local.SettingsDataStore  
import com.pcrjjc.app.domain.FortnightlyPushManager  
import com.pcrjjc.app.service.RankMonitorService  
import com.pcrjjc.app.service.UpdateCheckWorker  
import com.pcrjjc.app.worker.FortnightlyCheckWorker  
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

    @Inject  
    lateinit var fortnightlyPushManager: FortnightlyPushManager  

    override fun onCreate() {  
        super.onCreate()  
        createNotificationChannels()  
        restoreMonitoringState()  
        scheduleDailyUpdateCheck()  
        scheduleFortnightlyCheck()  
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

    private fun scheduleDailyUpdateCheck() {  
        UpdateCheckWorker.scheduleDaily(this)  
    }  

    /**  
     * 半月刊新活动推送：  
     * 1) 注册 15 分钟周期的后台检查任务（重启后由 WorkManager 自动恢复）；  
     * 2) 冷启动时补一次检查，保证用户打开 App 就能收到最新推送。  
     */  
    private fun scheduleFortnightlyCheck() {  
        FortnightlyCheckWorker.schedule(this)  
        CoroutineScope(Dispatchers.IO).launch {  
            try {  
                fortnightlyPushManager.checkAndPush(refresh = true)  
            } catch (e: Exception) {  
                // 启动期检查失败不影响应用启动  
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

            // 会战通知渠道  
            val clanBattleChannel = NotificationChannel(  
                CLAN_BATTLE_CHANNEL_ID,  
                "会战通知",  
                NotificationManager.IMPORTANCE_DEFAULT  
            ).apply {  
                description = "会战状态变动通知（击杀清除等）"  
            }  

            // 半月刊新活动推送渠道  
            val fortnightlyChannel = NotificationChannel(  
                FORTNIGHTLY_CHANNEL_ID,  
                "半月刊新活动",  
                NotificationManager.IMPORTANCE_DEFAULT  
            ).apply {  
                description = "订阅的半月刊类别有新活动时提醒"  
            }  

            val notificationManager = getSystemService(NotificationManager::class.java)  
            notificationManager.createNotificationChannel(rankChannel)  
            notificationManager.createNotificationChannel(serviceChannel)  
            notificationManager.createNotificationChannel(captchaChannel)  
            notificationManager.createNotificationChannel(clanBattleChannel)  
            notificationManager.createNotificationChannel(fortnightlyChannel)  
        }  
    }  

    companion object {  
        const val RANK_CHANNEL_ID = "rank_change_channel"  
        const val SERVICE_CHANNEL_ID = "service_channel"  
        const val CAPTCHA_CHANNEL_ID = "captcha_channel"  
        const val CLAN_BATTLE_CHANNEL_ID = "clan_battle_channel"  
        const val FORTNIGHTLY_CHANNEL_ID = "fortnightly_activity_channel"  
    }  
}  
