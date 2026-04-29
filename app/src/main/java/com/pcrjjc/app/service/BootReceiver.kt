package com.pcrjjc.app.service  
  
import android.content.BroadcastReceiver  
import android.content.Context  
import android.content.Intent  
import android.os.Build  
import com.pcrjjc.app.data.local.SettingsDataStore  
import dagger.hilt.android.AndroidEntryPoint  
import kotlinx.coroutines.CoroutineScope  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.launch  
import javax.inject.Inject  
import android.Manifest  
import android.content.pm.PackageManager  
import androidx.core.content.ContextCompat
  
@AndroidEntryPoint  
class BootReceiver : BroadcastReceiver() {  
  
    @Inject  
    lateinit var settingsDataStore: SettingsDataStore  
  
    override fun onReceive(context: Context, intent: Intent?) {  
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return  
  
        CoroutineScope(Dispatchers.IO).launch {  
            // Android 13+ 需要先检查通知权限，否则前台服务会崩溃  
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {  
                if (androidx.core.content.ContextCompat.checkSelfPermission(  
                        context, android.Manifest.permission.POST_NOTIFICATIONS  
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED  
                ) {  
                    return@launch  
                }  
            }  
            val interval = settingsDataStore.getPollingIntervalSync()  
            val serviceIntent = Intent(context, RankMonitorService::class.java)  
            serviceIntent.putExtra(RankMonitorService.EXTRA_INTERVAL_SECONDS, interval)  
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  
                context.startForegroundService(serviceIntent)  
            } else {  
                context.startService(serviceIntent)  
            }  
        }  
    }  
}