package com.pcrjjc.app.ui.autodef  
  
import androidx.compose.foundation.ExperimentalFoundationApi  
import androidx.compose.foundation.layout.Arrangement  
import androidx.compose.foundation.layout.Column  
import androidx.compose.foundation.layout.ExperimentalLayoutApi  
import androidx.compose.foundation.layout.FlowRow  
import androidx.compose.foundation.layout.Row  
import androidx.compose.foundation.layout.Spacer  
import androidx.compose.foundation.layout.fillMaxSize  
import androidx.compose.foundation.layout.fillMaxWidth  
import androidx.compose.foundation.layout.height  
import androidx.compose.foundation.layout.padding  
import androidx.compose.foundation.layout.width  
import androidx.compose.foundation.lazy.LazyColumn  
import androidx.compose.foundation.lazy.items  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
import androidx.compose.material.icons.filled.ArrowDropDown  
import androidx.compose.material3.Button  
import androidx.compose.material3.Card  
import androidx.compose.material3.CardDefaults  
import androidx.compose.material3.CircularProgressIndicator  
import androidx.compose.material3.DropdownMenuItem  
import androidx.compose.material3.ExperimentalMaterial3Api  
import androidx.compose.material3.ExposedDropdownMenuBox  
import androidx.compose.material3.FilterChip  
import androidx.compose.material3.HorizontalDivider  
import androidx.compose.material3.Icon  
import androidx.compose.material3.IconButton  
import androidx.compose.material3.MaterialTheme  
import androidx.compose.material3.OutlinedButton  
import androidx.compose.material3.OutlinedTextField  
import androidx.compose.material3.Scaffold  
import androidx.compose.material3.SnackbarHost  
import androidx.compose.material3.SnackbarHostState  
import androidx.compose.material3.Text  
import androidx.compose.material3.TopAppBar  
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.LaunchedEffect  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.runtime.mutableStateOf  
import androidx.compose.runtime.remember  
import androidx.compose.runtime.setValue  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.text.style.TextAlign  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
import com.pcrjjc.app.util.Platform  
  
@OptIn(  
    ExperimentalMaterial3Api::class,  
    ExperimentalFoundationApi::class,  
    ExperimentalLayoutApi::class  
)  
@Composable  
fun AutoDefScreen(  
    viewModel: AutoDefViewModel = hiltViewModel(),  
    onNavigateBack: () -> Unit  
) {  
    val uiState by viewModel.uiState.collectAsState()  
    val snackbarHostState = remember { SnackbarHostState() }  
  
    LaunchedEffect(uiState.errorMessage) {  
        uiState.errorMessage?.let {  
            snackbarHostState.showSnackbar(it)  
            viewModel.clearError()  
        }  
    }  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = { Text("PJJC自动换防") },  
                navigationIcon = {  
                    IconButton(onClick = onNavigateBack) {  
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")  
                    }  
                }  
            )  
        },  
        snackbarHost = { SnackbarHost(snackbarHostState) }  
    ) { paddingValues ->  
        LazyColumn(  
            modifier = Modifier.fillMaxSize().padding(paddingValues)  
        ) {  
            item {  
                Column(  
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),  
                    verticalArrangement = Arrangement.spacedBy(10.dp)  
                ) {  
                    Spacer(modifier = Modifier.height(2.dp))  
                    Text(  
                        "使用“我的账号”里对应服务器的账号登录。点击开始后重新登录，每2秒检测被刺，被刺立即换防并重新上线。",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
  
                    // 服务器  
                    Text("选择服务器", style = MaterialTheme.typography.labelMedium)  
                    FlowRow(  
                        modifier = Modifier.fillMaxWidth(),  
                        horizontalArrangement = Arrangement.spacedBy(6.dp),  
                        verticalArrangement = Arrangement.spacedBy(4.dp)  
                    ) {  
                        Platform.entries.forEach { platform ->  
                            FilterChip(  
                                selected = uiState.selectedPlatform == platform,  
                                onClick = { viewModel.updatePlatform(platform) },  
                                enabled = !uiState.isRunning,  
                                label = { Text(platform.displayName, style = MaterialTheme.typography.bodySmall) }  
                            )  
                        }  
                    }  
  
                    // 账号下拉  
                    Text("选择账号", style = MaterialTheme.typography.labelMedium)  
                    var accountExpanded by remember { mutableStateOf(false) }  
                    val selectedAccountName = uiState.availableAccounts  
                        .firstOrNull { it.id == uiState.selectedAccountId }?.account  
                        ?: "该服务器暂无我的账号"  
                    ExposedDropdownMenuBox(  
                        expanded = accountExpanded,  
                        onExpandedChange = { if (!uiState.isRunning) accountExpanded = it }  
                    ) {  
                        OutlinedTextField(  
                            value = selectedAccountName,  
                            onValueChange = {},  
                            readOnly = true,  
                            enabled = !uiState.isRunning,  
                            label = { Text("账号") },  
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },  
                            modifier = Modifier.fillMaxWidth().menuAnchor()  
                        )  
                        ExposedDropdownMenu(  
                            expanded = accountExpanded,  
                            onDismissRequest = { accountExpanded = false }  
                        ) {  
                            uiState.availableAccounts.forEach { account ->  
                                DropdownMenuItem(  
                                    text = { Text(account.account) },  
                                    onClick = {  
                                        viewModel.updateSelectedAccount(account.id)  
                                        accountExpanded = false  
                                    }  
                                )  
                            }  
                        }  
                    }  
  
                    // 开始换防  
                    Button(  
                        onClick = { viewModel.startAutoDef() },  
                        modifier = Modifier.fillMaxWidth(),  
                        enabled = !uiState.isRunning  
                    ) {  
                        if (uiState.isRunning) {  
                            CircularProgressIndicator(  
                                modifier = Modifier.height(20.dp).width(20.dp),  
                                strokeWidth = 2.dp,  
                                color = MaterialTheme.colorScheme.onPrimary  
                            )  
                            Spacer(modifier = Modifier.width(8.dp))  
                            Text("换防监控中...")  
                        } else {  
                            Text("开始换防")  
                        }  
                    }  
  
                    // 终止换防  
                    OutlinedButton(  
                        onClick = { viewModel.stopAutoDef() },  
                        modifier = Modifier.fillMaxWidth(),  
                        enabled = uiState.isRunning  
                    ) {  
                        Text("终止换防")  
                    }  
  
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))  
                }  
            }  
  
            // 日志  
            if (uiState.logs.isEmpty()) {  
                item {  
                    Text(  
                        text = "点击“开始换防”后，被刺情况与换防结果将显示在此处",  
                        modifier = Modifier.fillMaxWidth().padding(16.dp),  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant,  
                        textAlign = TextAlign.Center  
                    )  
                }  
            } else {  
                item { Spacer(modifier = Modifier.height(8.dp)) }  
                items(uiState.logs.asReversed()) { line ->  
                    Card(  
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),  
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)  
                    ) {  
                        Text(  
                            text = line,  
                            modifier = Modifier.fillMaxWidth().padding(12.dp),  
                            style = MaterialTheme.typography.bodyMedium  
                        )  
                    }  
                }  
                item { Spacer(modifier = Modifier.height(24.dp)) }  
            }  
        }  
    }  
}