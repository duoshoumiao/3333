package com.pcrjjc.app.ui.settings  
  
import androidx.compose.foundation.layout.Arrangement  
import androidx.compose.foundation.layout.Column  
import androidx.compose.foundation.layout.Row  
import androidx.compose.foundation.layout.Spacer  
import androidx.compose.foundation.layout.fillMaxSize  
import androidx.compose.foundation.layout.fillMaxWidth  
import androidx.compose.foundation.layout.height  
import androidx.compose.foundation.layout.padding  
import androidx.compose.foundation.layout.size  
import androidx.compose.foundation.layout.width  
import androidx.compose.foundation.rememberScrollState  
import androidx.compose.foundation.verticalScroll  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
import androidx.compose.material3.Button  
import androidx.compose.material3.Card  
import androidx.compose.material3.CardDefaults  
import androidx.compose.material3.Checkbox  
import androidx.compose.material3.CircularProgressIndicator  
import androidx.compose.material3.ExperimentalMaterial3Api  
import androidx.compose.material3.Icon  
import androidx.compose.material3.IconButton  
import androidx.compose.material3.LinearProgressIndicator  
import androidx.compose.material3.MaterialTheme  
import androidx.compose.material3.Scaffold  
import androidx.compose.material3.Text  
import androidx.compose.material3.TopAppBar  
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
import com.pcrjjc.app.BuildConfig  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun SettingsScreen(  
    viewModel: SettingsViewModel = hiltViewModel(),  
    onNavigateBack: () -> Unit  
) {  
    val uiState by viewModel.uiState.collectAsState()  
  
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
        }  
    ) { paddingValues ->  
        Column(  
            modifier = Modifier  
                .fillMaxSize()  
                .padding(paddingValues)  
                .padding(16.dp)  
                .verticalScroll(rememberScrollState()),  
            verticalArrangement = Arrangement.spacedBy(16.dp)  
        ) {  
            // Monitoring status (always on, read-only)  
            Card(  
                modifier = Modifier.fillMaxWidth(),  
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
            ) {  
                Column(modifier = Modifier.padding(16.dp)) {  
                    Text("排名监控", style = MaterialTheme.typography.titleMedium)  
                    Spacer(modifier = Modifier.height(8.dp))  
                    Text(  
                        text = "已强制开启，轮询间隔 1 秒",  
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
                            NoticeCheckbox(  
                                label = "上线提醒",  
                                checked = bind.onlineNotice != 0,  
                                onCheckedChange = { checked ->  
                                    viewModel.updateBindNotice(bind, onlineNotice = if (checked) 1 else 0)  
                                }  
                            )  
                        }  
                    }  
                }  
            }  
  
            // ★ 检查更新  
            Card(  
                modifier = Modifier.fillMaxWidth(),  
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
            ) {  
                Column(modifier = Modifier.padding(16.dp)) {  
                    Text("关于", style = MaterialTheme.typography.titleMedium)  
                    Spacer(modifier = Modifier.height(8.dp))  
                    Text(  
                        text = "当前版本: v${BuildConfig.VERSION_NAME}",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                    Spacer(modifier = Modifier.height(12.dp))  
  
                    // 检查更新按钮  
                    Button(  
                        onClick = { viewModel.checkForUpdate() },  
                        modifier = Modifier.fillMaxWidth(),  
                        enabled = !uiState.isCheckingUpdate && !uiState.isDownloading  
                    ) {  
                        if (uiState.isCheckingUpdate) {  
                            CircularProgressIndicator(  
                                modifier = Modifier.size(20.dp),  
                                strokeWidth = 2.dp  
                            )  
                            Spacer(modifier = Modifier.width(8.dp))  
                        }  
                        Text("检查更新")  
                    }  
  
                    // 下载进度条  
                    if (uiState.isDownloading) {  
                        Spacer(modifier = Modifier.height(8.dp))  
                        LinearProgressIndicator(  
                            progress = { uiState.downloadProgress },  
                            modifier = Modifier.fillMaxWidth()  
                        )  
                    }  
  
                    // 状态消息  
                    uiState.updateMessage?.let { msg ->  
                        Spacer(modifier = Modifier.height(8.dp))  
                        Text(  
                            text = msg,  
                            style = MaterialTheme.typography.bodySmall,  
                            color = MaterialTheme.colorScheme.onSurfaceVariant  
                        )  
                    }  
  
                    // 有新版本时显示下载按钮  
                    if (uiState.updateInfo != null && !uiState.isDownloading) {  
                        Spacer(modifier = Modifier.height(8.dp))  
                        Button(  
                            onClick = { viewModel.downloadAndInstall() },  
                            modifier = Modifier.fillMaxWidth()  
                        ) {  
                            Text("下载并安装 v${uiState.updateInfo!!.versionName}")  
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