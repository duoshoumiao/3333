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
import dagger.hilt.android.AndroidEntryPoint  
  
/**  
 * Foreground service for rank monitoring  
 */  
@AndroidEntryPoint  
class RankMonitorService : Service() {  
  
    companion object {  
        const val NOTIFICATION_ID = 1  
        private const val TAG = "RankMonitorService"  
    }  
  
    override fun onBind(intent: Intent?): IBinder? = null  
  
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {  
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {  
            if (ContextCompat.checkSelfPermission(  
                    this,  
                    Manifest.permission.POST_NOTIFICATIONS  
                ) != PackageManager.PERMISSION_GRANTED  
            ) {  
                Log.w(TAG, "通知权限未授予，停止前台服务")  
                stopSelf()  
                return START_NOT_STICKY  
            }  
        }  
  
        val notification = createNotification()  
        try {  
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {  
                ServiceCompat.startForeground(  
                    this,  
                    NOTIFICATION_ID,  
                    notification,  
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