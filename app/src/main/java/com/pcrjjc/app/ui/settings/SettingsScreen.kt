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
import androidx.compose.foundation.text.KeyboardOptions    
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
import androidx.compose.material3.OutlinedButton    
import androidx.compose.material3.OutlinedTextField    
import androidx.compose.material3.Scaffold    
import androidx.compose.material3.Text    
import androidx.compose.material3.TopAppBar    
import androidx.compose.runtime.Composable    
import androidx.compose.runtime.collectAsState    
import androidx.compose.runtime.getValue    
import androidx.compose.ui.Alignment    
import androidx.compose.ui.Modifier    
import androidx.compose.ui.text.input.KeyboardType    
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
            // ========== 检查更新 / 关于 Card ==========
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

                    if (uiState.isDownloading) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { uiState.downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    uiState.updateMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

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

            // ========== 服务器地址 Card（截图拆队用）==========    
            Card(    
                modifier = Modifier.fillMaxWidth(),    
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)    
            ) {    
                Column(modifier = Modifier.padding(16.dp)) {    
                    Text("服务器地址（选填）", style = MaterialTheme.typography.titleMedium)    
                    Spacer(modifier = Modifier.height(8.dp))    
                    Text(    
                        text = "填写个人服务器地址，留空则使用默认接口",    
                        style = MaterialTheme.typography.bodySmall,    
                        color = MaterialTheme.colorScheme.onSurfaceVariant    
                    )    
                    Spacer(modifier = Modifier.height(12.dp))    
                    Row(    
                        modifier = Modifier.fillMaxWidth(),    
                        horizontalArrangement = Arrangement.spacedBy(8.dp),    
                        verticalAlignment = Alignment.CenterVertically    
                    ) {    
                        OutlinedTextField(    
                            value = uiState.serverIpInput,    
                            onValueChange = { viewModel.onServerIpInputChanged(it) },    
                            label = { Text("IP地址") },    
                            placeholder = { Text("例: 114.514.1.1") },    
                            singleLine = true,    
                            modifier = Modifier.weight(2f)    
                        )    
                        OutlinedTextField(    
                            value = uiState.serverPortInput,    
                            onValueChange = { viewModel.onServerPortInputChanged(it) },    
                            label = { Text("端口") },    
                            placeholder = { Text("例: 8020") },    
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),    
                            singleLine = true,    
                            modifier = Modifier.weight(1f),    
                            isError = uiState.serverPortInput.isNotEmpty() &&    
                                uiState.serverPortInput.toIntOrNull()    
                                    .let { it == null || it < 1 || it > 65535 }    
                        )    
                    }    
                    Spacer(modifier = Modifier.height(8.dp))    
                    Button(    
                        onClick = { viewModel.saveServerAddress() },    
                        modifier = Modifier.fillMaxWidth(),    
                        enabled = uiState.serverPortInput.isEmpty() ||    
                            uiState.serverPortInput.toIntOrNull()    
                                .let { it != null && it in 1..65535 }    
                    ) {    
                        Text("保存")    
                    }    
                    if (uiState.serverSaved) {    
                        Spacer(modifier = Modifier.height(4.dp))    
                        Text(    
                            text = if (uiState.serverIpInput.isBlank()) "已清除，将使用默认接口" else "已保存",    
                            style = MaterialTheme.typography.bodySmall,    
                            color = MaterialTheme.colorScheme.primary    
                        )    
                    }    
                }    
            }    
  
            // ========== 清日常服务器地址 Card（新增）==========    
            Card(    
                modifier = Modifier.fillMaxWidth(),    
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)    
            ) {    
                Column(modifier = Modifier.padding(16.dp)) {    
                    Text("清日常服务器地址", style = MaterialTheme.typography.titleMedium)    
                    Spacer(modifier = Modifier.height(8.dp))    
                    Text(    
                        text = "填写清日常服务器地址，用于清日常功能",    
                        style = MaterialTheme.typography.bodySmall,    
                        color = MaterialTheme.colorScheme.onSurfaceVariant    
                    )    
                    Spacer(modifier = Modifier.height(12.dp))    
                    Row(    
                        modifier = Modifier.fillMaxWidth(),    
                        horizontalArrangement = Arrangement.spacedBy(8.dp),    
                        verticalAlignment = Alignment.CenterVertically    
                    ) {    
                        OutlinedTextField(    
                            value = uiState.dailyServerIpInput,    
                            onValueChange = { viewModel.onDailyServerIpInputChanged(it) },    
                            label = { Text("IP地址") },    
                            placeholder = { Text("例: 192.168.1.100") },    
                            singleLine = true,    
                            modifier = Modifier.weight(2f)    
                        )    
                        OutlinedTextField(    
                            value = uiState.dailyServerPortInput,    
                            onValueChange = { viewModel.onDailyServerPortInputChanged(it) },    
                            label = { Text("端口") },    
                            placeholder = { Text("例: 2280") },    
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),    
                            singleLine = true,    
                            modifier = Modifier.weight(1f),    
                            isError = uiState.dailyServerPortInput.isNotEmpty() &&    
                                uiState.dailyServerPortInput.toIntOrNull()    
                                    .let { it == null || it < 1 || it > 65535 }    
                        )    
                    }    
                    Spacer(modifier = Modifier.height(8.dp))    
                    Button(    
                        onClick = { viewModel.saveDailyServerAddress() },    
                        modifier = Modifier.fillMaxWidth(),    
                        enabled = uiState.dailyServerIpInput.isNotBlank() && (    
                            uiState.dailyServerPortInput.isEmpty() ||    
                            uiState.dailyServerPortInput.toIntOrNull()    
                                .let { it != null && it in 1..65535 }    
                        )    
                    ) {    
                        Text("保存")    
                    }    
                    if (uiState.dailyServerSaved) {    
                        Spacer(modifier = Modifier.height(4.dp))    
                        Text(    
                            text = "已保存",    
                            style = MaterialTheme.typography.bodySmall,    
                            color = MaterialTheme.colorScheme.primary    
                        )    
                    }    
                }    
            }    
  
            // ========== 头像缓存 Card ==========    
            Card(    
                modifier = Modifier.fillMaxWidth(),    
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)    
            ) {    
                Column(modifier = Modifier.padding(16.dp)) {    
                    Text("头像缓存", style = MaterialTheme.typography.titleMedium)    
                    Spacer(modifier = Modifier.height(8.dp))    
                    Text(    
                        text = "提前下载所有角色头像到本地，避免查看详细资料时超时",    
                        style = MaterialTheme.typography.bodySmall,    
                        color = MaterialTheme.colorScheme.onSurfaceVariant    
                    )    
                    Spacer(modifier = Modifier.height(4.dp))    
                    Text(    
                        text = "已缓存: ${uiState.cachedAvatarCount} 个头像",    
                        style = MaterialTheme.typography.bodySmall,    
                        color = MaterialTheme.colorScheme.onSurfaceVariant    
                    )    
                    if (uiState.rosterCount > 0) {    
                        Text(    
                            text = "花名册: ${uiState.rosterCount} 个角色",    
                            style = MaterialTheme.typography.bodySmall,    
                            color = MaterialTheme.colorScheme.onSurfaceVariant    
                        )    
                    }    
                    Spacer(modifier = Modifier.height(12.dp))    
  
                    OutlinedButton(    
                        onClick = { viewModel.updateRoster() },    
                        modifier = Modifier.fillMaxWidth(),    
                        enabled = !uiState.isUpdatingRoster && !uiState.isDownloadingAvatars    
                    ) {    
                        if (uiState.isUpdatingRoster) {    
                            CircularProgressIndicator(    
                                modifier = Modifier.size(20.dp),    
                                strokeWidth = 2.dp    
                            )    
                            Spacer(modifier = Modifier.width(8.dp))    
                        }    
                        Text("更新花名册")    
                    }    
  
                    uiState.rosterMessage?.let { msg ->    
                        Spacer(modifier = Modifier.height(4.dp))    
                        Text(    
                            text = msg,    
                            style = MaterialTheme.typography.bodySmall,    
                            color = MaterialTheme.colorScheme.onSurfaceVariant    
                        )    
                    }    
  
                    Spacer(modifier = Modifier.height(8.dp))    
  
                    Button(    
                        onClick = { viewModel.downloadAllAvatars() },    
                        modifier = Modifier.fillMaxWidth(),    
                        enabled = !uiState.isDownloadingAvatars    
                    ) {    
                        if (uiState.isDownloadingAvatars) {    
                            CircularProgressIndicator(    
                                modifier = Modifier.size(20.dp),    
                                strokeWidth = 2.dp    
                            )    
                            Spacer(modifier = Modifier.width(8.dp))    
                        }    
                        Text("下载全部头像")    
                    }    
  
                    if (uiState.isDownloadingAvatars) {    
                        Spacer(modifier = Modifier.height(8.dp))    
                        LinearProgressIndicator(    
                            progress = { uiState.avatarDownloadProgress },    
                            modifier = Modifier.fillMaxWidth()    
                        )    
                    }    
  
                    uiState.avatarDownloadMessage?.let { msg ->    
                        Spacer(modifier = Modifier.height(8.dp))    
                        Text(    
                            text = msg,    
                            style = MaterialTheme.typography.bodySmall,    
                            color = MaterialTheme.colorScheme.onSurfaceVariant    
                        )    
                    }    
                }    
            }    
  
            // ========== 轮询间隔设置 Card ==========    
            Card(    
                modifier = Modifier.fillMaxWidth(),    
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)    
            ) {    
                Column(modifier = Modifier.padding(16.dp)) {    
                    Text("排名监控", style = MaterialTheme.typography.titleMedium)    
                    Spacer(modifier = Modifier.height(8.dp))    
                    Text(    
                        text = "当前轮询间隔: ${uiState.pollingInterval} 秒",    
                        style = MaterialTheme.typography.bodySmall,    
                        color = MaterialTheme.colorScheme.onSurfaceVariant    
                    )    
                    Spacer(modifier = Modifier.height(12.dp))    
                    Row(    
                        modifier = Modifier.fillMaxWidth(),    
                        verticalAlignment = Alignment.CenterVertically,    
                        horizontalArrangement = Arrangement.spacedBy(12.dp)    
                    ) {    
                        OutlinedTextField(    
                            value = uiState.pollingIntervalInput,    
                            onValueChange = { viewModel.onIntervalInputChanged(it) },    
                            label = { Text("间隔(秒)") },    
                            keyboardOptions = KeyboardOptions(    
                                keyboardType = KeyboardType.Number    
                            ),    
                            singleLine = true,    
                            modifier = Modifier.weight(1f),    
                            isError = uiState.pollingIntervalInput.toLongOrNull()    
                                .let { it == null || it < 1 }    
                        )    
                        Button(    
                            onClick = { viewModel.savePollingInterval() },    
                            enabled = uiState.pollingIntervalInput.toLongOrNull()    
                                .let { it != null && it >= 1 }    
                        ) {    
                            Text("保存")    
                        }    
                    }    
                    if (uiState.intervalSaved) {    
                        Spacer(modifier = Modifier.height(4.dp))    
                        Text(    
                            text = "已保存，轮询间隔已更新为 ${uiState.pollingInterval} 秒",    
                            style = MaterialTheme.typography.bodySmall,    
                            color = MaterialTheme.colorScheme.primary    
                        )    
                    }    
                }    
            }    
  
            // ========== 通知设置 ==========    
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
                                onCheckedChange = {    
                                    viewModel.updateBindNotice(bind, jjcNotice = it)    
                                }    
                            )    
                            NoticeCheckbox(    
                                label = "PJJC排名变动",    
                                checked = bind.pjjcNotice,    
                                onCheckedChange = {    
                                    viewModel.updateBindNotice(bind, pjjcNotice = it)    
                                }    
                            )    
                            NoticeCheckbox(    
                                label = "排名上升也通知",    
                                checked = bind.upNotice,    
                                onCheckedChange = {    
                                    viewModel.updateBindNotice(bind, upNotice = it)    
                                }    
                            )    
                            NoticeCheckbox(    
                                label = "上线提醒",    
                                checked = bind.onlineNotice != 0,    
                                onCheckedChange = { checked ->    
                                    viewModel.updateBindNotice(    
                                        bind,    
                                        onlineNotice = if (checked) 1 else 0    
                                    )    
                                }    
                            )    
                        }    
                    }    
                }    
            }    
  
            // ========== 检查更新 / 关于 Card ==========    
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
  
                    if (uiState.isDownloading) {    
                        Spacer(modifier = Modifier.height(8.dp))    
                        LinearProgressIndicator(    
                            progress = { uiState.downloadProgress },    
                            modifier = Modifier.fillMaxWidth()    
                        )    
                    }    
  
                    uiState.updateMessage?.let { msg ->    
                        Spacer(modifier = Modifier.height(8.dp))    
                        Text(    
                            text = msg,    
                            style = MaterialTheme.typography.bodySmall,    
                            color = MaterialTheme.colorScheme.onSurfaceVariant    
                        )    
                    }    
  
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