package com.pcrjjc.app.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pcrjjc.app.PcrJjcApp
import com.pcrjjc.app.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Foreground service for rank monitoring
 */
@AndroidEntryPoint
class RankMonitorService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, PcrJjcApp.SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("竞技场监控")
            .setContentText("正在监控竞技场排名变动...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
