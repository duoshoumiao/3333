package com.pcrjjc.app.ui.friend  
  
import androidx.compose.foundation.horizontalScroll  
import androidx.compose.foundation.layout.Arrangement  
import androidx.compose.foundation.layout.Column  
import androidx.compose.foundation.layout.Row  
import androidx.compose.foundation.layout.Spacer  
import androidx.compose.foundation.layout.fillMaxSize  
import androidx.compose.foundation.layout.fillMaxWidth  
import androidx.compose.foundation.layout.height  
import androidx.compose.foundation.layout.padding  
import androidx.compose.foundation.rememberScrollState  
import androidx.compose.foundation.text.KeyboardOptions  
import androidx.compose.foundation.verticalScroll  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
import androidx.compose.material3.Button  
import androidx.compose.material3.CircularProgressIndicator  
import androidx.compose.material3.ExperimentalMaterial3Api  
import androidx.compose.material3.FilterChip  
import androidx.compose.material3.Icon  
import androidx.compose.material3.IconButton  
import androidx.compose.material3.MaterialTheme  
import androidx.compose.material3.OutlinedTextField  
import androidx.compose.material3.Scaffold  
import androidx.compose.material3.Text  
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog  
import androidx.compose.material3.Card  
import androidx.compose.material3.HorizontalDivider  
import androidx.compose.material3.OutlinedButton  
import androidx.compose.material3.TextButton  
import androidx.compose.runtime.mutableStateOf  
import androidx.compose.runtime.remember  
import androidx.compose.runtime.setValue  
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.text.input.KeyboardType  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun FriendScreen(  
    viewModel: FriendViewModel = hiltViewModel(),  
    onNavigateBack: () -> Unit  
) {  
    val uiState by viewModel.uiState.collectAsState() 
    // 待删除的好友 UID（非 null 时弹确认框）  
    var pendingRemoveUid by remember { mutableStateOf<Long?>(null) }	
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = { Text("好友申请") },  
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
            verticalArrangement = Arrangement.spacedBy(12.dp)  
        ) {  
            // 账号选择（「我的账号」isMaster 列表，与 SupportScreen 一致）  
            Row(  
                modifier = Modifier  
                    .fillMaxWidth()  
                    .horizontalScroll(rememberScrollState()),  
                horizontalArrangement = Arrangement.spacedBy(8.dp)  
            ) {  
                uiState.masterAccounts.forEach { acc ->  
                    FilterChip(  
                        selected = uiState.selectedAccount?.id == acc.id,  
                        onClick = { viewModel.selectAccount(acc) },  
                        label = { Text(acc.account) }  
                    )  
                }  
            }  
  
            if (uiState.masterAccounts.isEmpty()) {  
                Text(  
                    "暂无「我的账号」，请先在 账号管理→我的账号 中添加",  
                    style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            }  
  
            OutlinedTextField(  
                value = uiState.targetUid,  
                onValueChange = { viewModel.updateTargetUid(it) },  
                label = { Text("目标玩家UID") },  
                singleLine = true,  
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  
                modifier = Modifier.fillMaxWidth()  
            )  
  
            Button(  
                onClick = { viewModel.sendFriendRequest() },  
                enabled = !uiState.isLoading,  
                modifier = Modifier.fillMaxWidth()  
            ) {  
                if (uiState.isLoading) {  
                    CircularProgressIndicator(  
                        modifier = Modifier.height(18.dp),  
                        strokeWidth = 2.dp  
                    )  
                } else {  
                    Text("发送好友申请")  
                }  
            }  
  
            uiState.error?.let {  
                Text(it, color = MaterialTheme.colorScheme.error,  
                    style = MaterialTheme.typography.bodySmall)  
            }  
            uiState.message?.let {  
                Text(it, color = MaterialTheme.colorScheme.primary,  
                    style = MaterialTheme.typography.bodySmall)  
            }
            // ===== 好友管理 =====  
            HorizontalDivider()  
            Row(  
                modifier = Modifier.fillMaxWidth(),  
                verticalAlignment = Alignment.CenterVertically  
            ) {  
                Text(  
                    "好友管理（${uiState.friendList.size}）",  
                    style = MaterialTheme.typography.titleMedium,  
                    modifier = Modifier.weight(1f)  
                )  
                OutlinedButton(  
                    onClick = { viewModel.loadFriendList() },  
                    enabled = !uiState.isLoadingFriends  
                ) { Text("刷新") }  
            }  
  
            if (uiState.isLoadingFriends) {  
                CircularProgressIndicator(  
                    modifier = Modifier.align(Alignment.CenterHorizontally)  
                )  
            } else if (uiState.friendList.isEmpty()) {  
                Text(  
                    "暂无好友（点上方刷新获取列表）",  
                    style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            } else {  
                uiState.friendList.forEach { friend ->  
                    val vid = (friend["viewer_id"] as? Number)?.toLong() ?: 0L  
                    val name = friend["name"] as? String ?: ""  
                    val level = (friend["level"] as? Number)?.toInt() ?: 0  
                    Card(modifier = Modifier.fillMaxWidth()) {  
                        Row(  
                            modifier = Modifier  
                                .fillMaxWidth()  
                                .padding(horizontal = 12.dp, vertical = 8.dp),  
                            verticalAlignment = Alignment.CenterVertically  
                        ) {  
                            Column(modifier = Modifier.weight(1f)) {  
                                Text(name.ifBlank { "UID: $vid" })  
                                Text(  
                                    "Lv.$level  UID: $vid",  
                                    style = MaterialTheme.typography.bodySmall,  
                                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                                )  
                            }  
                            OutlinedButton(onClick = { pendingRemoveUid = vid }) {  
                                Text("删除", color = MaterialTheme.colorScheme.error)  
                            }  
                        }  
                    }  
                }  
            }			
        }  
    }
    // 删除好友确认弹窗  
        pendingRemoveUid?.let { uid ->  
            AlertDialog(  
                onDismissRequest = { pendingRemoveUid = null },  
                title = { Text("删除好友") },  
                text = { Text("确定删除好友 UID: $uid 吗？") },  
                confirmButton = {  
                    TextButton(onClick = {  
                        viewModel.removeFriend(uid)  
                        pendingRemoveUid = null  
                    }) { Text("确定", color = MaterialTheme.colorScheme.error) }  
                },  
                dismissButton = {  
                    TextButton(onClick = { pendingRemoveUid = null }) { Text("取消") }  
                }  
            )  
        }	
}