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
import androidx.compose.foundation.text.KeyboardActions  
import androidx.compose.foundation.text.KeyboardOptions  
import androidx.compose.foundation.verticalScroll  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
import androidx.compose.material.icons.filled.PlayArrow  
import androidx.compose.material.icons.filled.Send  
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
import androidx.compose.runtime.mutableStateOf  
import androidx.compose.runtime.remember  
import androidx.compose.runtime.setValue  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.text.font.FontWeight  
import androidx.compose.ui.text.input.ImeAction  
import androidx.compose.ui.text.input.KeyboardType  
import androidx.compose.ui.text.input.PasswordVisualTransformation  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun DailyScreen(  
    onNavigateBack: () -> Unit,  
    viewModel: DailyViewModel = hiltViewModel()  
) {  
    val uiState by viewModel.uiState.collectAsState()  
    val snackbarHostState = remember { SnackbarHostState() }  
  
    // 显示错误  
    LaunchedEffect(uiState.errorMessage) {  
        uiState.errorMessage?.let {  
            snackbarHostState.showSnackbar(it)  
            viewModel.clearError()  
        }  
    }  
  
    // 指令输入弹窗状态  
    var showCommandDialog by remember { mutableStateOf(false) }  
    var commandDialogText by remember { mutableStateOf("") }  
  
    // 执行结果弹窗  
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
  
    // 指令编辑弹窗  
    if (showCommandDialog) {  
        AlertDialog(  
            onDismissRequest = { showCommandDialog = false },  
            title = { Text("编辑指令") },  
            text = {  
                Column {  
                    Text(  
                        text = "编辑指令内容后点击发送执行",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                    Spacer(modifier = Modifier.height(12.dp))  
                    OutlinedTextField(  
                        value = commandDialogText,  
                        onValueChange = { commandDialogText = it },  
                        modifier = Modifier.fillMaxWidth(),  
                        label = { Text("指令") },  
                        singleLine = false,  
                        maxLines = 4  
                    )  
                }  
            },  
            confirmButton = {  
                TextButton(  
                    onClick = {  
                        showCommandDialog = false  
                        val text = commandDialogText.trim()  
                        if (text.isNotBlank()) {  
                            viewModel.executeCommand(text)  
                        }  
                    }  
                ) {  
                    Text("发送")  
                }  
            },  
            dismissButton = {  
                TextButton(onClick = { showCommandDialog = false }) {  
                    Text("取消")  
                }  
            }  
        )  
    }  
  
    val title = when (uiState.phase) {  
        DailyPhase.LOGIN -> "清日常 - 登录"  
        DailyPhase.ACCOUNTS -> "清日常 - 选择账号"  
        DailyPhase.COMMANDS -> "清日常 - ${uiState.selectedAccount ?: ""}"  
    }  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = { Text(title) },  
                navigationIcon = {  
                    IconButton(onClick = {  
                        if (uiState.phase == DailyPhase.LOGIN) {  
                            onNavigateBack()  
                        } else {  
                            viewModel.goBack()  
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
    ) { padding ->  
        Box(  
            modifier = Modifier  
                .fillMaxSize()  
                .padding(padding)  
        ) {  
            when (uiState.phase) {  
                DailyPhase.LOGIN -> LoginContent(  
                    qq = uiState.qqInput,  
                    password = uiState.passwordInput,  
                    isLoading = uiState.isLoading,  
                    serverUrl = uiState.serverUrl,  
                    onQqChanged = viewModel::onQqInputChanged,  
                    onPasswordChanged = viewModel::onPasswordInputChanged,  
                    onLogin = viewModel::login  
                )  
                DailyPhase.ACCOUNTS -> AccountsContent(  
                    accounts = uiState.accounts,  
                    isLoading = uiState.isLoading,  
                    onSelectAccount = viewModel::selectAccount  
                )  
                DailyPhase.COMMANDS -> CommandsContent(  
                    selectedAccount = uiState.selectedAccount ?: "",  
                    onCommandClick = { cmd ->  
                        commandDialogText = extractCommandPrefix(cmd.command)  
                        showCommandDialog = true  
                    }  
                )  
            }  
  
            // 执行中遮罩  
            if (uiState.isExecuting) {  
                Box(  
                    modifier = Modifier  
                        .fillMaxSize()  
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))  
                        .clickable(enabled = false) {},  
                    contentAlignment = Alignment.Center  
                ) {  
                    Card(  
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)  
                    ) {  
                        Column(  
                            modifier = Modifier.padding(32.dp),  
                            horizontalAlignment = Alignment.CenterHorizontally  
                        ) {  
                            CircularProgressIndicator()  
                            Spacer(modifier = Modifier.height(16.dp))  
                            Text("正在执行指令...")  
                        }  
                    }  
                }  
            }  
        }  
    }  
}  
  
/**  
 * 从指令文本中提取可编辑的前缀。  
 * 例如 "#查装备 [<rank>] [fav]" → "#查装备 "  
 * 例如 "#查缺角色" → "#查缺角色"  
 */  
private fun extractCommandPrefix(command: String): String {  
    // 找到第一个 [ 或 < 的位置，截取前面部分作为前缀  
    val idx = command.indexOfFirst { it == '[' || it == '<' }  
    return if (idx > 0) {  
        command.substring(0, idx).trimEnd() + " "  
    } else {  
        // 没有参数标记，直接用整个指令名  
        command.split(" ").firstOrNull()?.let {  
            if (command.contains(" ")) "$it " else it  
        } ?: command  
    }  
}  
  
// ==================== 登录界面 ====================  
  
@Composable  
private fun LoginContent(  
    qq: String,  
    password: String,  
    isLoading: Boolean,  
    serverUrl: String?,  
    onQqChanged: (String) -> Unit,  
    onPasswordChanged: (String) -> Unit,  
    onLogin: () -> Unit  
) {  
    Column(  
        modifier = Modifier  
            .fillMaxSize()  
            .padding(24.dp),  
        verticalArrangement = Arrangement.Center,  
        horizontalAlignment = Alignment.CenterHorizontally  
    ) {  
        Text(  
            text = "清日常",  
            style = MaterialTheme.typography.headlineMedium,  
            fontWeight = FontWeight.Bold  
        )  
  
        Spacer(modifier = Modifier.height(8.dp))  
  
        if (serverUrl.isNullOrBlank()) {  
            Text(  
                text = "请先在设置中配置清日常服务器地址",  
                style = MaterialTheme.typography.bodyMedium,  
                color = MaterialTheme.colorScheme.error  
            )  
        } else {  
            Text(  
                text = "服务器: $serverUrl",  
                style = MaterialTheme.typography.bodySmall,  
                color = MaterialTheme.colorScheme.onSurfaceVariant  
            )  
        }  
  
        Spacer(modifier = Modifier.height(32.dp))  
  
        OutlinedTextField(  
            value = qq,  
            onValueChange = onQqChanged,  
            modifier = Modifier.fillMaxWidth(),  
            label = { Text("QQ号") },  
            singleLine = true,  
            keyboardOptions = KeyboardOptions(  
                keyboardType = KeyboardType.Number,  
                imeAction = ImeAction.Next  
            )  
        )  
  
        Spacer(modifier = Modifier.height(12.dp))  
  
        OutlinedTextField(  
            value = password,  
            onValueChange = onPasswordChanged,  
            modifier = Modifier.fillMaxWidth(),  
            label = { Text("密码") },  
            singleLine = true,  
            visualTransformation = PasswordVisualTransformation(),  
            keyboardOptions = KeyboardOptions(  
                keyboardType = KeyboardType.Password,  
                imeAction = ImeAction.Done  
            ),  
            keyboardActions = KeyboardActions(onDone = { onLogin() })  
        )  
  
        Spacer(modifier = Modifier.height(24.dp))  
  
        Button(  
            onClick = onLogin,  
            modifier = Modifier.fillMaxWidth(),  
            enabled = !isLoading && qq.isNotBlank() && password.isNotBlank() && !serverUrl.isNullOrBlank()  
        ) {  
            if (isLoading) {  
                CircularProgressIndicator(  
                    modifier = Modifier.size(20.dp),  
                    strokeWidth = 2.dp,  
                    color = MaterialTheme.colorScheme.onPrimary  
                )  
                Spacer(modifier = Modifier.width(8.dp))  
            }  
            Text("登录")  
        }  
    }  
}  
  
// ==================== 账号列表界面 ====================  
  
@Composable  
private fun AccountsContent(  
    accounts: List<String>,  
    isLoading: Boolean,  
    onSelectAccount: (String) -> Unit  
) {  
    if (isLoading) {  
        Box(  
            modifier = Modifier.fillMaxSize(),  
            contentAlignment = Alignment.Center  
        ) {  
            Column(horizontalAlignment = Alignment.CenterHorizontally) {  
                CircularProgressIndicator()  
                Spacer(modifier = Modifier.height(16.dp))  
                Text("加载账号列表...")  
            }  
        }  
    } else if (accounts.isEmpty()) {  
        Box(  
            modifier = Modifier.fillMaxSize(),  
            contentAlignment = Alignment.Center  
        ) {  
            Text(  
                text = "暂无账号",  
                style = MaterialTheme.typography.bodyLarge,  
                color = MaterialTheme.colorScheme.onSurfaceVariant  
            )  
        }  
    } else {  
        LazyColumn(  
            modifier = Modifier  
                .fillMaxSize()  
                .padding(16.dp),  
            verticalArrangement = Arrangement.spacedBy(8.dp)  
        ) {  
            items(accounts) { account ->  
                Card(  
                    modifier = Modifier  
                        .fillMaxWidth()  
                        .clickable { onSelectAccount(account) },  
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
                ) {  
                    Row(  
                        modifier = Modifier  
                            .fillMaxWidth()  
                            .padding(16.dp),  
                        verticalAlignment = Alignment.CenterVertically  
                    ) {  
                        Column(modifier = Modifier.weight(1f)) {  
                            Text(  
                                text = account,  
                                style = MaterialTheme.typography.titleMedium,  
                                fontWeight = FontWeight.Bold  
                            )  
                            Text(  
                                text = "点击进入指令面板",  
                                style = MaterialTheme.typography.bodySmall,  
                                color = MaterialTheme.colorScheme.onSurfaceVariant  
                            )  
                        }  
                        Icon(  
                            imageVector = Icons.Default.PlayArrow,  
                            contentDescription = "进入",  
                            tint = MaterialTheme.colorScheme.primary  
                        )  
                    }  
                }  
            }  
        }  
    }  
}  
  
// ==================== 指令列表界面 ====================  
  
@Composable  
private fun CommandsContent(  
    selectedAccount: String,  
    onCommandClick: (CommandItem) -> Unit  
) {  
    Column(  
        modifier = Modifier  
            .fillMaxSize()  
            .padding(horizontal = 16.dp)  
    ) {  
        Spacer(modifier = Modifier.height(12.dp))  
        Text(  
            text = "账号: $selectedAccount",  
            style = MaterialTheme.typography.titleMedium,  
            fontWeight = FontWeight.Bold  
        )  
        Spacer(modifier = Modifier.height(4.dp))  
        Text(  
            text = "点击指令可编辑参数后执行，格式：命令 昵称 参数",  
            style = MaterialTheme.typography.bodySmall,  
            color = MaterialTheme.colorScheme.onSurfaceVariant  
        )  
        Spacer(modifier = Modifier.height(12.dp))  
        HorizontalDivider()  
  
        LazyColumn(  
            verticalArrangement = Arrangement.spacedBy(4.dp)  
        ) {  
            item { Spacer(modifier = Modifier.height(8.dp)) }  
            items(DAILY_COMMANDS) { cmd ->  
                CommandCard(cmd = cmd, onClick = { onCommandClick(cmd) })  
            }  
            item { Spacer(modifier = Modifier.height(16.dp)) }  
        }  
    }  
}  
  
@Composable  
private fun CommandCard(  
    cmd: CommandItem,  
    onClick: () -> Unit  
) {  
    Card(  
        modifier = Modifier  
            .fillMaxWidth()  
            .clickable(onClick = onClick),  
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),  
        colors = CardDefaults.cardColors(  
            containerColor = MaterialTheme.colorScheme.surfaceVariant  
        )  
    ) {  
        Row(  
            modifier = Modifier.padding(12.dp),  
            verticalAlignment = Alignment.CenterVertically  
        ) {  
            Icon(  
                imageVector = Icons.Default.Send,  
                contentDescription = null,  
                tint = MaterialTheme.colorScheme.primary,  
                modifier = Modifier.size(18.dp)  
            )  
            Spacer(modifier = Modifier.width(10.dp))  
            Column(modifier = Modifier.weight(1f)) {  
                Text(  
                    text = cmd.command,  
                    style = MaterialTheme.typography.bodyMedium,  
                    fontWeight = FontWeight.Bold,  
                    color = MaterialTheme.colorScheme.primary  
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