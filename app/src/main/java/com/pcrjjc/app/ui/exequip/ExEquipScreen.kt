package com.pcrjjc.app.ui.exequip  
  
import androidx.compose.foundation.layout.Arrangement  
import androidx.compose.foundation.layout.Column  
import androidx.compose.foundation.layout.ExperimentalLayoutApi  
import androidx.compose.foundation.layout.FlowRow  
import androidx.compose.foundation.layout.Spacer  
import androidx.compose.foundation.layout.fillMaxSize  
import androidx.compose.foundation.layout.fillMaxWidth  
import androidx.compose.foundation.layout.height  
import androidx.compose.foundation.layout.padding  
import androidx.compose.foundation.layout.width  
import androidx.compose.foundation.rememberScrollState  
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
import androidx.compose.material3.OutlinedButton  
import androidx.compose.material3.Scaffold  
import androidx.compose.material3.SnackbarHost  
import androidx.compose.material3.SnackbarHostState  
import androidx.compose.material3.Text  
import androidx.compose.material3.TopAppBar  
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.LaunchedEffect  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.runtime.remember  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
  
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)  
@Composable  
fun ExEquipScreen(  
    viewModel: ExEquipViewModel = hiltViewModel(),  
    onNavigateBack: () -> Unit  
) {  
    val uiState by viewModel.uiState.collectAsState()  
    val snackbarHostState = remember { SnackbarHostState() }  
  
    LaunchedEffect(uiState.error) {  
        uiState.error?.let {  
            snackbarHostState.showSnackbar(it)  
            viewModel.clearError()  
        }  
    }  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = { Text("EX状态管理") },  
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
                .padding(horizontal = 16.dp)  
                .verticalScroll(rememberScrollState()),  
            verticalArrangement = Arrangement.spacedBy(12.dp)  
        ) {  
            Spacer(modifier = Modifier.height(2.dp))  
            Text(  
                "从「我的账号」里选择账号，保存当前所有角色穿戴的普通EX装备状态，之后可一键恢复。数据仅保存在本地，不影响账号配置。",  
                style = MaterialTheme.typography.bodySmall,  
                color = MaterialTheme.colorScheme.onSurfaceVariant  
            )  
  
            // 账号选择  
            Text("选择账号", style = MaterialTheme.typography.labelMedium)  
            if (uiState.masterAccounts.isEmpty()) {  
                Text(  
                    text = "没有可用的账号。请先在「我的账号」中添加账号。",  
                    style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            } else {  
                FlowRow(  
                    modifier = Modifier.fillMaxWidth(),  
                    horizontalArrangement = Arrangement.spacedBy(6.dp),  
                    verticalArrangement = Arrangement.spacedBy(4.dp)  
                ) {  
                    uiState.masterAccounts.forEach { account ->  
                        FilterChip(  
                            selected = uiState.selectedAccount?.id == account.id,  
                            onClick = { viewModel.selectAccount(account) },  
                            label = { Text(account.account, style = MaterialTheme.typography.bodySmall) }  
                        )  
                    }  
                }  
            }  
  
            // 操作按钮  
            Button(  
                onClick = { viewModel.onSave() },  
                modifier = Modifier.fillMaxWidth(),  
                enabled = !uiState.isLoading && uiState.selectedAccount != null  
            ) {  
                if (uiState.isLoading) {  
                    CircularProgressIndicator(  
                        modifier = Modifier.height(20.dp).width(20.dp),  
                        strokeWidth = 2.dp,  
                        color = MaterialTheme.colorScheme.onPrimary  
                    )  
                    Spacer(modifier = Modifier.width(8.dp))  
                    Text("执行中...")  
                } else {  
                    Text("保存ex状态")  
                }  
            }  
  
            OutlinedButton(  
                onClick = { viewModel.onRestore() },  
                modifier = Modifier.fillMaxWidth(),  
                enabled = !uiState.isLoading && uiState.selectedAccount != null  
            ) {  
                Text("恢复ex状态")  
            }  
  
            // 日志展示  
            if (uiState.log.isNotBlank()) {  
                Text("执行结果", style = MaterialTheme.typography.labelMedium)  
                Text(  
                    text = uiState.log,  
                    style = MaterialTheme.typography.bodyMedium  
                )  
            }  
        }  
    }  
}