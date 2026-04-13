package com.pcrjjc.app.ui.daily  
  
import androidx.compose.foundation.background  
import androidx.compose.foundation.clickable  
import androidx.compose.foundation.layout.Arrangement  
import androidx.compose.foundation.layout.Box  
import androidx.compose.foundation.layout.Column  
import androidx.compose.foundation.layout.Row  
import androidx.compose.foundation.layout.Spacer  
import androidx.compose.foundation.layout.fillMaxSize  
import androidx.compose.foundation.layout.fillMaxWidth  
import androidx.compose.foundation.layout.height  
import androidx.compose.foundation.layout.padding  
import androidx.compose.foundation.layout.size  
import androidx.compose.foundation.layout.width  
import androidx.compose.foundation.lazy.LazyColumn  
import androidx.compose.foundation.lazy.items  
import androidx.compose.foundation.rememberScrollState  
import androidx.compose.foundation.text.KeyboardOptions  
import androidx.compose.foundation.verticalScroll  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
import androidx.compose.material.icons.filled.Block  
import androidx.compose.material.icons.filled.Edit  
import androidx.compose.material.icons.filled.PlayArrow  
import androidx.compose.material.icons.filled.Star  
import androidx.compose.material3.AlertDialog  
import androidx.compose.material3.Button  
import androidx.compose.material3.Card  
import androidx.compose.material3.CardDefaults  
import androidx.compose.material3.CircularProgressIndicator  
import androidx.compose.material3.ExperimentalMaterial3Api  
import androidx.compose.material3.HorizontalDivider  
import androidx.compose.material3.Icon  
import androidx.compose.material3.IconButton  
import androidx.compose.material3.MaterialTheme  
import androidx.compose.material3.OutlinedTextField  
import androidx.compose.material3.Scaffold  
import androidx.compose.material3.SnackbarHost  
import androidx.compose.material3.SnackbarHostState  
import androidx.compose.material3.Text  
import androidx.compose.material3.TextButton  
import androidx.compose.material3.TopAppBar  
import androidx.compose.material3.TopAppBarDefaults  
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.LaunchedEffect  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.runtime.remember  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.graphics.Color  
import androidx.compose.ui.text.font.FontWeight  
import androidx.compose.ui.text.input.KeyboardType  
import androidx.compose.ui.text.input.PasswordVisualTransformation  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun DailyScreen(  
    viewModel: DailyViewModel = hiltViewModel(),  
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
  
    // ===== 结果弹窗 =====  
    if (uiState.showResultDialog) {  
        AlertDialog(  
            onDismissRequest = { viewModel.dismissResult() },  
            title = { Text("执行结果") },  
            text = {  
                Text(  
                    text = uiState.executionResult ?: "",  
                    modifier = Modifier.verticalScroll(rememberScrollState())  
                )  
            },  
            confirmButton = {  
                TextButton(onClick = { viewModel.dismissResult() }) {  
                    Text("确定")  
                }  
            }  
        )  
    }  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = {  
                    Text(  
                        when (uiState.phase) {  
                            DailyPhase.LOGIN -> "清日常 - 登录"  
                            DailyPhase.ACCOUNTS -> "清日常 - 选择账号"  
                            DailyPhase.COMMANDS -> "清日常 - ${uiState.selectedAccount ?: "指令"}"  
                        }  
                    )  
                },  
                navigationIcon = {  
                    IconButton(onClick = {  
                        when (uiState.phase) {  
                            DailyPhase.LOGIN -> onNavigateBack()  
                            else -> viewModel.goBack()  
                        }  
                    }) {  
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")  
                    }  
                },  
                colors = TopAppBarDefaults.topAppBarColors(  
                    containerColor = MaterialTheme.colorScheme.primaryContainer,  
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer  
                )  
            )  
        },  
        snackbarHost = { SnackbarHost(snackbarHostState) }  
    ) { paddingValues ->  
        Box(modifier = Modifier.padding(paddingValues)) {  
            when (uiState.phase) {  
                DailyPhase.LOGIN -> LoginContent(  
                    uiState = uiState,  
                    onQqChanged = viewModel::onQqChanged,  
                    onPasswordChanged = viewModel::onPasswordChanged,  
                    onLogin = viewModel::login  
                )  
                DailyPhase.ACCOUNTS -> AccountsContent(  
                    uiState = uiState,  
                    onSelectAccount = viewModel::selectAccount  
                )  
                DailyPhase.COMMANDS -> CommandsContent(  
                    uiState = uiState,  
                    onExecuteCommand = viewModel::executeCommand  
                )  
            }  
  
            // ===== 执行中遮罩 =====  
            if (uiState.isExecuting) {  
                Box(  
                    modifier = Modifier  
                        .fillMaxSize()  
                        .background(Color.Black.copy(alpha = 0.4f))  
                        .clickable(enabled = false) { },  
                    contentAlignment = Alignment.Center  
                ) {  
                    Card(  
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)  
                    ) {  
                        Column(  
                            modifier = Modifier.padding(32.dp),  
                            horizontalAlignment = Alignment.CenterHorizontally  
                        ) {  
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))  
                            Spacer(modifier = Modifier.height(16.dp))  
                            Text("执行中，请稍候...", style = MaterialTheme.typography.bodyLarge)  
                        }  
                    }  
                }  
            }  
        }  
    }  
}  
  
// ==================== 登录界面 ====================  
  
@Composable  
private fun LoginContent(  
    uiState: DailyUiState,  
    onQqChanged: (String) -> Unit,  
    onPasswordChanged: (String) -> Unit,  
    onLogin: () -> Unit  
) {  
    Column(  
        modifier = Modifier  
            .fillMaxSize()  
            .padding(24.dp),  
        horizontalAlignment = Alignment.CenterHorizontally,  
        verticalArrangement = Arrangement.Center  
    ) {  
        Text(  
            text = "清日常登录",  
            style = MaterialTheme.typography.headlineMedium,  
            fontWeight = FontWeight.Bold  
        )  
        Spacer(modifier = Modifier.height(8.dp))  
        Text(  
            text = "使用QQ账号密码登录清日常服务",  
            style = MaterialTheme.typography.bodyMedium,  
            color = MaterialTheme.colorScheme.onSurfaceVariant  
        )  
  
        if (uiState.serverUrl.isNullOrBlank()) {  
            Spacer(modifier = Modifier.height(24.dp))  
            Card(  
                modifier = Modifier.fillMaxWidth(),  
                colors = CardDefaults.cardColors(  
                    containerColor = MaterialTheme.colorScheme.errorContainer  
                )  
            ) {  
                Text(  
                    text = "请先在设置中配置清日常服务器地址",  
                    modifier = Modifier.padding(16.dp),  
                    style = MaterialTheme.typography.bodyMedium,  
                    color = MaterialTheme.colorScheme.onErrorContainer  
                )  
            }  
            return  
        }  
  
        Spacer(modifier = Modifier.height(32.dp))  
  
        OutlinedTextField(  
            value = uiState.qqInput,  
            onValueChange = onQqChanged,  
            label = { Text("QQ号") },  
            placeholder = { Text("请输入QQ号") },  
            singleLine = true,  
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  
            modifier = Modifier.fillMaxWidth(),  
            enabled = !uiState.isLoading  
        )  
  
        Spacer(modifier = Modifier.height(16.dp))  
  
        OutlinedTextField(  
            value = uiState.passwordInput,  
            onValueChange = onPasswordChanged,  
            label = { Text("密码") },  
            placeholder = { Text("请输入密码") },  
            singleLine = true,  
            visualTransformation = PasswordVisualTransformation(),  
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),  
            modifier = Modifier.fillMaxWidth(),  
            enabled = !uiState.isLoading  
        )  
  
        Spacer(modifier = Modifier.height(24.dp))  
  
        Button(  
            onClick = onLogin,  
            modifier = Modifier.fillMaxWidth(),  
            enabled = !uiState.isLoading  
                    && uiState.qqInput.isNotBlank()  
                    && uiState.passwordInput.isNotBlank()  
        ) {  
            if (uiState.isLoading) {  
                CircularProgressIndicator(  
                    modifier = Modifier.size(20.dp),  
                    strokeWidth = 2.dp,  
                    color = MaterialTheme.colorScheme.onPrimary  
                )  
                Spacer(modifier = Modifier.width(8.dp))  
                Text("登录中...")  
            } else {  
                Text("登录")  
            }  
        }  
    }  
}  
  
// ==================== 账号列表界面 ====================  
  
@Composable  
private fun AccountsContent(  
    uiState: DailyUiState,  
    onSelectAccount: (String) -> Unit  
) {  
    Column(  
        modifier = Modifier  
            .fillMaxSize()  
            .padding(16.dp)  
    ) {  
        Text(  
            text = "选择账号",  
            style = MaterialTheme.typography.titleLarge,  
            fontWeight = FontWeight.Bold  
        )  
        Spacer(modifier = Modifier.height(4.dp))  
        Text(  
            text = "共 ${uiState.accounts.size} 个账号",  
            style = MaterialTheme.typography.bodyMedium,  
            color = MaterialTheme.colorScheme.onSurfaceVariant  
        )  
        Spacer(modifier = Modifier.height(16.dp))  
  
        if (uiState.accounts.isEmpty()) {  
            Column(  
                modifier = Modifier.fillMaxSize(),  
                horizontalAlignment = Alignment.CenterHorizontally,  
                verticalArrangement = Arrangement.Center  
            ) {  
                Text(  
                    text = "该QQ下暂无账号",  
                    style = MaterialTheme.typography.bodyLarge,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            }  
        } else {  
            LazyColumn(  
                verticalArrangement = Arrangement.spacedBy(8.dp)  
            ) {  
                items(uiState.accounts) { alias ->  
                    Card(  
                        modifier = Modifier  
                            .fillMaxWidth()  
                            .clickable { onSelectAccount(alias) },  
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
                    ) {  
                        Column(modifier = Modifier.padding(16.dp)) {  
                            Text(  
                                text = alias,  
                                style = MaterialTheme.typography.titleMedium  
                            )  
                            Spacer(modifier = Modifier.height(4.dp))  
                            Text(  
                                text = "点击查看可用指令",  
                                style = MaterialTheme.typography.bodySmall,  
                                color = MaterialTheme.colorScheme.onSurfaceVariant  
                            )  
                        }  
                    }  
                }  
            }  
        }  
    }  
}  
  
// ==================== 指令列表界面 ====================  
  
@Composable  
private fun CommandsContent(  
    uiState: DailyUiState,  
    onExecuteCommand: (CommandItem) -> Unit  
) {  
    Column(  
        modifier = Modifier  
            .fillMaxSize()  
            .padding(horizontal = 16.dp)  
    ) {  
        Spacer(modifier = Modifier.height(12.dp))  
        Text(  
            text = "账号: ${uiState.selectedAccount ?: ""}",  
            style = MaterialTheme.typography.titleMedium,  
            fontWeight = FontWeight.Bold  
        )  
        Spacer(modifier = Modifier.height(4.dp))  
        Text(  
            text = "指令格式：命令 昵称 参数，<>表示必填，[]表示可选，|表示分割",  
            style = MaterialTheme.typography.bodySmall,  
            color = MaterialTheme.colorScheme.onSurfaceVariant  
        )  
        Spacer(modifier = Modifier.height(4.dp))  
        // 图例  
        Row(  
            horizontalArrangement = Arrangement.spacedBy(12.dp),  
            verticalAlignment = Alignment.CenterVertically  
        ) {  
            LegendDot(color = MaterialTheme.colorScheme.primary, label = "可执行")  
            LegendDot(color = MaterialTheme.colorScheme.tertiary, label = "需参数")  
            LegendDot(color = MaterialTheme.colorScheme.outline, label = "暂不支持")  
        }  
        Spacer(modifier = Modifier.height(8.dp))  
        HorizontalDivider()  
  
        LazyColumn(  
            verticalArrangement = Arrangement.spacedBy(4.dp)  
        ) {  
            item { Spacer(modifier = Modifier.height(8.dp)) }  
            items(DAILY_COMMANDS) { cmd ->  
                CommandCard(cmd = cmd, onClick = { onExecuteCommand(cmd) })  
            }  
            item { Spacer(modifier = Modifier.height(16.dp)) }  
        }  
    }  
}  
  
@Composable  
private fun LegendDot(color: Color, label: String) {  
    Row(verticalAlignment = Alignment.CenterVertically) {  
        Box(  
            modifier = Modifier  
                .size(10.dp)  
                .background(color, shape = MaterialTheme.shapes.small)  
        )  
        Spacer(modifier = Modifier.width(4.dp))  
        Text(text = label, style = MaterialTheme.typography.labelSmall)  
    }  
}  
  
@Composable  
private fun CommandCard(cmd: CommandItem, onClick: () -> Unit) {  
    val containerColor = when (cmd.type) {  
        CommandType.SIMPLE, CommandType.DO_DAILY ->  
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)  
        CommandType.NEEDS_PARAMS ->  
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)  
        CommandType.UNSUPPORTED ->  
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)  
    }  
  
    val commandColor = when (cmd.type) {  
        CommandType.SIMPLE, CommandType.DO_DAILY -> MaterialTheme.colorScheme.primary  
        CommandType.NEEDS_PARAMS -> MaterialTheme.colorScheme.tertiary  
        CommandType.UNSUPPORTED -> MaterialTheme.colorScheme.outline  
    }  
  
    val icon = when (cmd.type) {  
        CommandType.SIMPLE -> Icons.Default.PlayArrow  
        CommandType.DO_DAILY -> Icons.Default.Star  
        CommandType.NEEDS_PARAMS -> Icons.Default.Edit  
        CommandType.UNSUPPORTED -> Icons.Default.Block  
    }  
  
    Card(  
        modifier = Modifier  
            .fillMaxWidth()  
            .clickable(onClick = onClick),  
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),  
        colors = CardDefaults.cardColors(containerColor = containerColor)  
    ) {  
        Row(  
            modifier = Modifier.padding(12.dp),  
            verticalAlignment = Alignment.CenterVertically  
        ) {  
            Icon(  
                imageVector = icon,  
                contentDescription = null,  
                tint = commandColor,  
                modifier = Modifier.size(20.dp)  
            )  
            Spacer(modifier = Modifier.width(10.dp))  
            Column(modifier = Modifier.weight(1f)) {  
                Text(  
                    text = cmd.command,  
                    style = MaterialTheme.typography.bodyMedium,  
                    fontWeight = FontWeight.Bold,  
                    color = commandColor  
                )  
                if (cmd.description.isNotBlank()) {  
                    Spacer(modifier = Modifier.height(2.dp))  
                    Text(  
                        text = cmd.description,  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                }  
            }  
        }  
    }  
}