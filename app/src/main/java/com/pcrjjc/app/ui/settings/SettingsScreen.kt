// app/src/main/java/com/pcrjjc/app/ui/settings/SettingsScreen.kt  
package com.pcrjjc.app.ui.settings  
  
import android.Manifest  
import android.content.pm.PackageManager  
import android.os.Build  
import androidx.activity.compose.rememberLauncherForActivityResult  
import androidx.activity.result.contract.ActivityResultContracts  
import androidx.compose.foundation.layout.Arrangement  
import androidx.compose.foundation.layout.Column  
import androidx.compose.foundation.layout.Row  
import androidx.compose.foundation.layout.Spacer  
import androidx.compose.foundation.layout.fillMaxSize  
import androidx.compose.foundation.layout.fillMaxWidth  
import androidx.compose.foundation.layout.height  
import androidx.compose.foundation.layout.padding  
import androidx.compose.foundation.rememberScrollState  
import androidx.compose.foundation.verticalScroll  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
import androidx.compose.material3.Card  
import androidx.compose.material3.CardDefaults  
import androidx.compose.material3.Checkbox  
import androidx.compose.material3.ExperimentalMaterial3Api  
import androidx.compose.material3.Icon  
import androidx.compose.material3.IconButton  
import androidx.compose.material3.MaterialTheme  
import androidx.compose.material3.Scaffold  
import androidx.compose.material3.Slider  
import androidx.compose.material3.SnackbarHost  
import androidx.compose.material3.SnackbarHostState  
import androidx.compose.material3.Switch  
import androidx.compose.material3.Text  
import androidx.compose.material3.TopAppBar  
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.runtime.remember  
import androidx.compose.runtime.rememberCoroutineScope  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.platform.LocalContext  
import androidx.compose.ui.unit.dp  
import androidx.core.content.ContextCompat  
import androidx.hilt.navigation.compose.hiltViewModel  
import kotlinx.coroutines.launch  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun SettingsScreen(  
    viewModel: SettingsViewModel = hiltViewModel(),  
    onNavigateBack: () -> Unit  
) {  
    val uiState by viewModel.uiState.collectAsState()  
    val context = LocalContext.current  
    val snackbarHostState = remember { SnackbarHostState() }  
    val scope = rememberCoroutineScope()  
  
    val notificationPermissionLauncher = rememberLauncherForActivityResult(  
        contract = ActivityResultContracts.RequestPermission()  
    ) { isGranted ->  
        if (isGranted) {  
            viewModel.toggleMonitoring(true)  
        } else {  
            scope.launch {  
                snackbarHostState.showSnackbar("需要通知权限才能启用排名监控")  
            }  
        }  
    }  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = { Text("设置") },  
                navigationIcon = {  
                    IconButton(onClick = onNavigateBack) {  
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")  
                    }  
                }  
            )  
        },  
        snackbarHost = { SnackbarHost(snackbarHostState) }  
    ) { paddingValues ->  
        Column(  
            modifier = Modifier  
                .fillMaxSize()  
                .padding(paddingValues)  
                .padding(16.dp)  
                .verticalScroll(rememberScrollState()),  
            verticalArrangement = Arrangement.spacedBy(16.dp)  
        ) {  
            // Monitoring toggle  
            Card(  
                modifier = Modifier.fillMaxWidth(),  
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
            ) {  
                Column(modifier = Modifier.padding(16.dp)) {  
                    Row(  
                        modifier = Modifier.fillMaxWidth(),  
                        horizontalArrangement = Arrangement.SpaceBetween,  
                        verticalAlignment = Alignment.CenterVertically  
                    ) {  
                        Text("启用排名监控", style = MaterialTheme.typography.titleMedium)  
                        Switch(  
                            checked = uiState.isMonitoringEnabled,  
                            onCheckedChange = { enabled ->  
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {  
                                    if (ContextCompat.checkSelfPermission(  
                                            context,  
                                            Manifest.permission.POST_NOTIFICATIONS  
                                        ) != PackageManager.PERMISSION_GRANTED  
                                    ) {  
                                        notificationPermissionLauncher.launch(  
                                            Manifest.permission.POST_NOTIFICATIONS  
                                        )  
                                        return@Switch  
                                    }  
                                }  
                                viewModel.toggleMonitoring(enabled)  
                            }  
                        )  
                    }  
                    Spacer(modifier = Modifier.height(8.dp))  
                    Text(  
                        text = "开启后将定期查询排名变化并推送通知",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                }  
            }  
  
            // Polling interval  
            Card(  
                modifier = Modifier.fillMaxWidth(),  
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
            ) {  
                Column(modifier = Modifier.padding(16.dp)) {  
                    Text("轮询间隔", style = MaterialTheme.typography.titleMedium)  
                    Spacer(modifier = Modifier.height(8.dp))  
                    Text(  
                        text = "${uiState.pollingIntervalMinutes} 分钟",  
                        style = MaterialTheme.typography.bodyLarge  
                    )  
                    Slider(  
                        value = uiState.pollingIntervalMinutes.toFloat(),  
                        onValueChange = { viewModel.setPollingInterval(it.toLong()) },  
                        valueRange = 15f..120f,  
                        steps = 6  
                    )  
                    Text(  
                        text = "最小间隔15分钟（WorkManager限制）",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                }  
            }  
  
            // Per-bind notification settings  
            if (uiState.binds.isNotEmpty()) {  
                Text("通知设置", style = MaterialTheme.typography.titleMedium)  
                uiState.binds.forEach { bind ->  
                    Card(  
                        modifier = Modifier.fillMaxWidth(),  
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)  
                    ) {  
                        Column(modifier = Modifier.padding(16.dp)) {  
                            Text(  
                                text = bind.name ?: "UID: ${bind.pcrid}",  
                                style = MaterialTheme.typography.titleSmall  
                            )  
                            Spacer(modifier = Modifier.height(8.dp))  
  
                            NoticeCheckbox(  
                                label = "JJC排名变动",  
                                checked = bind.jjcNotice,  
                                onCheckedChange = { viewModel.updateBindNotice(bind, jjcNotice = it) }  
                            )  
                            NoticeCheckbox(  
                                label = "PJJC排名变动",  
                                checked = bind.pjjcNotice,  
                                onCheckedChange = { viewModel.updateBindNotice(bind, pjjcNotice = it) }  
                            )  
                            NoticeCheckbox(  
                                label = "排名上升也通知",  
                                checked = bind.upNotice,  
                                onCheckedChange = { viewModel.updateBindNotice(bind, upNotice = it) }  
                            )  
                        }  
                    }  
                }  
            }  
        }  
    }  
}  
  
@Composable  
private fun NoticeCheckbox(  
    label: String,  
    checked: Boolean,  
    onCheckedChange: (Boolean) -> Unit  
) {  
    Row(  
        modifier = Modifier.fillMaxWidth(),  
        horizontalArrangement = Arrangement.SpaceBetween,  
        verticalAlignment = Alignment.CenterVertically  
    ) {  
        Text(text = label, style = MaterialTheme.typography.bodyMedium)  
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)  
    }  
}