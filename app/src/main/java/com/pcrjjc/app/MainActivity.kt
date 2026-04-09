package com.pcrjjc.app  
  
import android.Manifest  
import android.content.pm.PackageManager  
import android.os.Build  
import android.os.Bundle  
import androidx.activity.ComponentActivity  
import androidx.activity.compose.setContent  
import androidx.activity.result.contract.ActivityResultContracts  
import androidx.compose.foundation.layout.fillMaxSize  
import androidx.compose.material3.MaterialTheme  
import androidx.compose.material3.Surface  
import androidx.compose.ui.Modifier  
import androidx.core.content.ContextCompat  
import com.pcrjjc.app.ui.navigation.PcrJjcNavHost  
import com.pcrjjc.app.ui.theme.PcrJjcTheme  
import dagger.hilt.android.AndroidEntryPoint  
  
@AndroidEntryPoint  
class MainActivity : ComponentActivity() {  
  
    private val requestPermissionLauncher =  
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _: Boolean ->  
        }  
  
    override fun onCreate(savedInstanceState: Bundle?) {  
        super.onCreate(savedInstanceState)  
        requestNotificationPermission()  
        setContent {  
            PcrJjcTheme {  
                Surface(  
                    modifier = Modifier.fillMaxSize(),  
                    color = MaterialTheme.colorScheme.background  
                ) {  
                    PcrJjcNavHost()  
                }  
            }  
        }  
    }  
  
    private fun requestNotificationPermission() {  
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {  
            if (ContextCompat.checkSelfPermission(  
                    this,  
                    Manifest.permission.POST_NOTIFICATIONS  
                ) != PackageManager.PERMISSION_GRANTED  
            ) {  
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)  
            }  
        }  
    }  
}