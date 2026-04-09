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
  
@AndroidEntryPoint  
class BootReceiver : BroadcastReceiver() {  
  
    @Inject  
    lateinit var settingsDataStore: SettingsDataStore  
  
    override fun onReceive(context: Context, intent: Intent?) {  
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return  
  
        CoroutineScope(Dispatchers.IO).launch {  
            val enabled = settingsDataStore.isMonitoringEnabledSync()  
            if (enabled) {  
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
}