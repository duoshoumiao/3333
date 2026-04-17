package com.pcrjjc.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pcrjjc.app.BuildConfig
import com.pcrjjc.app.R
import com.pcrjjc.app.data.local.SettingsDataStore
import com.pcrjjc.app.domain.UpdateChecker
import com.pcrjjc.app.domain.UpdateInfo
import com.pcrjjc.app.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(context, workerParams) {

    // 直接创建 UpdateChecker 实例
    private val updateChecker by lazy { UpdateChecker(context) }

    companion object {
        private const val TAG = "UpdateCheckWorker"
        private const val WORK_NAME = "daily_update_check"
        private const val CHANNEL_ID = "update_channel"
        private const val NOTIFICATION_ID = 1001

        /**
         * 启动每日更新检查定时任务
         */
        fun scheduleDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                1, TimeUnit.DAYS  // 每天检查一次
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(TAG, "Scheduled daily update check")
        }

        /**
         * 取消每日更新检查
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Cancelled daily update check")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting daily update check...")

        // 获取上次检查时间和已通知版本
        val lastCheckTime = settingsDataStore.getLastUpdateCheckTimeSync()
        val notifiedVersion = settingsDataStore.getNotifiedVersionSync()
        val currentVersion = BuildConfig.VERSION_NAME

        // 检查是否今天已经检查过
        val today = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
        val lastCheckDay = lastCheckTime / (24 * 60 * 60 * 1000)

        if (today == lastCheckDay) {
            Log.i(TAG, "Already checked today, skipping")
            return Result.success()
        }

        // 检查更新
        val updateInfo = updateChecker.checkForUpdate(currentVersion)

        // 记录检查时间
        settingsDataStore.setLastUpdateCheckTime(System.currentTimeMillis())

        if (updateInfo != null) {
            // 发现新版本，如果这个版本之前没有提醒过，则发送通知
            if (notifiedVersion != updateInfo.versionName) {
                showUpdateNotification(updateInfo.versionName, updateInfo.releaseNotes)
                settingsDataStore.setNotifiedVersion(updateInfo.versionName)
                Log.i(TAG, "Found new version: ${updateInfo.versionName}, showing notification")
            } else {
                Log.i(TAG, "Already notified for version: ${updateInfo.versionName}")
            }
        } else {
            Log.i(TAG, "No update available")
        }

        return Result.success()
    }

    private fun showUpdateNotification(versionName: String, releaseNotes: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 创建通知渠道 (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "更新提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "应用更新提醒"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 创建点击通知后打开应用的 Intent
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "发现新版本: v$versionName"
        val content = if (releaseNotes.isNotBlank()) {
            releaseNotes.lines().firstOrNull() ?: "点击查看更新内容"
        } else {
            "点击查看更新内容"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}