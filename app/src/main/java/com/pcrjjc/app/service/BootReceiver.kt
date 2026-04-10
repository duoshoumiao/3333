package com.pcrjjc.app.service  
  
import android.content.BroadcastReceiver  
import android.content.Context  
import android.content.Intent  
import android.os.Build  
import dagger.hilt.android.AndroidEntryPoint  
import kotlinx.coroutines.CoroutineScope  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.launch  
  
@AndroidEntryPoint  
class BootReceiver : BroadcastReceiver() {  
  
    override fun onReceive(context: Context, intent: Intent?) {  
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return  
  
        CoroutineScope(Dispatchers.IO).launch {  
            val serviceIntent = Intent(context, RankMonitorService::class.java)  
            serviceIntent.putExtra(RankMonitorService.EXTRA_INTERVAL_SECONDS, 1L)  
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  
                context.startForegroundService(serviceIntent)  
            } else {  
                context.startService(serviceIntent)  
            }  
        }  
    }  
}