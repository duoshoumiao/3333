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
        }  
    }  
}