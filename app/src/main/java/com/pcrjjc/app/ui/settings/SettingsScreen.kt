package com.pcrjjc.app.ui.settings  
  
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
import androidx.compose.material3.Text  
import androidx.compose.material3.TopAppBar  
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
  
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