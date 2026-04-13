package com.pcrjjc.app.ui.daily  
  
import androidx.compose.animation.AnimatedVisibility  
import androidx.compose.animation.expandVertically  
import androidx.compose.animation.shrinkVertically  
import androidx.compose.foundation.background  
import androidx.compose.foundation.clickable  
import androidx.compose.foundation.layout.Arrangement  
import androidx.compose.foundation.layout.Box  
import androidx.compose.foundation.layout.Column  
import androidx.compose.foundation.layout.ExperimentalLayoutApi  
import androidx.compose.foundation.layout.FlowRow  
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
import androidx.compose.material.icons.filled.AccessTime  
import androidx.compose.material.icons.filled.ExpandLess  
import androidx.compose.material.icons.filled.ExpandMore  
import androidx.compose.material.icons.filled.PlayArrow  
import androidx.compose.material.icons.filled.Refresh  
import androidx.compose.material.icons.filled.Schedule  
import androidx.compose.material.icons.filled.Send  
import androidx.compose.material3.AlertDialog  
import androidx.compose.material3.Button  
import androidx.compose.material3.Card  
import androidx.compose.material3.CardDefaults  
import androidx.compose.material3.Checkbox  
import androidx.compose.material3.CircularProgressIndicator  
import androidx.compose.material3.ExperimentalMaterial3Api  
import androidx.compose.material3.FilterChip  
import androidx.compose.material3.HorizontalDivider  
import androidx.compose.material3.Icon  
import androidx.compose.material3.IconButton  
import androidx.compose.material3.MaterialTheme  
import androidx.compose.material3.OutlinedTextField  
import androidx.compose.material3.Scaffold  
import androidx.compose.material3.SnackbarHost  
import androidx.compose.material3.SnackbarHostState  
import androidx.compose.material3.Switch  
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
import androidx.compose.ui.text.style.TextAlign  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
import androidx.compose.material.icons.filled.Delete  
import androidx.compose.material.icons.filled.Person
  
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
  
    // 显示定时配置错误  
    LaunchedEffect(uiState.cronError) {  
        uiState.cronError?.let {  
            snackbarHostState.showSnackbar(it)  
            viewModel.clearCronError()  
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
					onLogin = viewModel::login,  
					savedAccounts = uiState.savedAccounts,  
					onSelectSavedAccount = viewModel::selectSavedAccount,  
					onDeleteSavedAccount = viewModel::deleteSavedAccount  
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
                    },  
                    // 定时任务相关  
                    showCronSection = uiState.showCronSection,  
                    cronConfigs = uiState.cronConfigs,  
                    isLoadingCron = uiState.isLoadingCron,  
                    isSavingCron = uiState.isSavingCron,  
                    onToggleCronSection = viewModel::toggleCronSection,  
                    onRefreshCron = viewModel::loadCronConfig,  
                    onToggleCron = viewModel::toggleCron,  
                    onUpdateCronTime = viewModel::updateCronTime,  
                    onToggleClanbattleRun = viewModel::toggleClanbattleRun,  
                    onUpdateModuleExcludeType = viewModel::updateModuleExcludeType  
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
    onLogin: () -> Unit,  
    savedAccounts: List<SavedDailyAccount> = emptyList(),  
    onSelectSavedAccount: (SavedDailyAccount) -> Unit = {},  
    onDeleteSavedAccount: (SavedDailyAccount) -> Unit = {}  
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
  
        // 已保存账号列表  
        if (savedAccounts.isNotEmpty()) {  
            Text(  
                text = "已保存账号",  
                style = MaterialTheme.typography.labelMedium,  
                color = MaterialTheme.colorScheme.onSurfaceVariant,  
                modifier = Modifier  
                    .fillMaxWidth()  
                    .padding(bottom = 4.dp)  
            )  
            savedAccounts.forEach { account ->  
                Card(  
                    modifier = Modifier  
                        .fillMaxWidth()  
                        .padding(vertical = 2.dp)  
                        .clickable { onSelectSavedAccount(account) },  
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),  
                    colors = CardDefaults.cardColors(  
                        containerColor = if (account.qq == qq)  
                            MaterialTheme.colorScheme.primaryContainer  
                        else  
                            MaterialTheme.colorScheme.surfaceVariant  
                    )  
                ) {  
                    Row(  
                        modifier = Modifier  
                            .fillMaxWidth()  
                            .padding(horizontal = 12.dp, vertical = 8.dp),  
                        verticalAlignment = Alignment.CenterVertically  
                    ) {  
                        Icon(  
                            imageVector = Icons.Default.Person,  
                            contentDescription = null,  
                            modifier = Modifier.size(18.dp),  
                            tint = MaterialTheme.colorScheme.primary  
                        )  
                        Spacer(modifier = Modifier.width(8.dp))  
                        Text(  
                            text = account.qq,  
                            style = MaterialTheme.typography.bodyMedium,  
                            modifier = Modifier.weight(1f)  
                        )  
                        IconButton(  
                            onClick = { onDeleteSavedAccount(account) },  
                            modifier = Modifier.size(28.dp)  
                        ) {  
                            Icon(  
                                imageVector = Icons.Default.Delete,  
                                contentDescription = "删除",  
                                modifier = Modifier.size(16.dp),  
                                tint = MaterialTheme.colorScheme.error  
                            )  
                        }  
                    }  
                }  
            }  
            Spacer(modifier = Modifier.height(16.dp))  
        }
		
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
    onCommandClick: (CommandItem) -> Unit,  
    // 定时任务相关  
    showCronSection: Boolean,  
    cronConfigs: List<CronConfig>,  
    isLoadingCron: Boolean,  
    isSavingCron: Boolean,  
    onToggleCronSection: () -> Unit,  
    onRefreshCron: () -> Unit,  
    onToggleCron: (Int, Boolean) -> Unit,  
    onUpdateCronTime: (Int, String) -> Unit,  
    onToggleClanbattleRun: (Int, Boolean) -> Unit,  
    onUpdateModuleExcludeType: (Int, List<String>) -> Unit  
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
            // ---- 定时任务区域 ----  
            item {  
                Spacer(modifier = Modifier.height(8.dp))  
                CronSectionHeader(  
                    expanded = showCronSection,  
                    isLoading = isLoadingCron,  
                    isSaving = isSavingCron,  
                    onToggle = onToggleCronSection,  
                    onRefresh = onRefreshCron  
                )  
            }  
  
            item {  
                AnimatedVisibility(  
                    visible = showCronSection,  
                    enter = expandVertically(),  
                    exit = shrinkVertically()  
                ) {  
                    CronSettingsSection(  
                        cronConfigs = cronConfigs,  
                        isLoading = isLoadingCron,  
                        onToggleCron = onToggleCron,  
                        onUpdateTime = onUpdateCronTime,  
                        onToggleClanbattleRun = onToggleClanbattleRun,  
                        onUpdateModuleExcludeType = onUpdateModuleExcludeType  
                    )  
                }  
            }  
  
            item {  
                Spacer(modifier = Modifier.height(4.dp))  
                HorizontalDivider()  
                Spacer(modifier = Modifier.height(8.dp))  
            }  
  
            // ---- 指令列表 ----  
            items(DAILY_COMMANDS) { cmd ->  
                CommandCard(cmd = cmd, onClick = { onCommandClick(cmd) })  
            }  
            item { Spacer(modifier = Modifier.height(16.dp)) }  
        }  
    }  
}  
  
// ==================== 定时任务区域头部 ====================  
  
@Composable  
private fun CronSectionHeader(  
    expanded: Boolean,  
    isLoading: Boolean,  
    isSaving: Boolean,  
    onToggle: () -> Unit,  
    onRefresh: () -> Unit  
) {  
    Card(  
        modifier = Modifier  
            .fillMaxWidth()  
            .clickable(onClick = onToggle),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),  
        colors = CardDefaults.cardColors(  
            containerColor = MaterialTheme.colorScheme.secondaryContainer  
        )  
    ) {  
        Row(  
            modifier = Modifier  
                .fillMaxWidth()  
                .padding(horizontal = 16.dp, vertical = 12.dp),  
            verticalAlignment = Alignment.CenterVertically  
        ) {  
            Icon(  
                imageVector = Icons.Default.Schedule,  
                contentDescription = null,  
                tint = MaterialTheme.colorScheme.onSecondaryContainer,  
                modifier = Modifier.size(22.dp)  
            )  
            Spacer(modifier = Modifier.width(10.dp))  
            Text(  
                text = "定时任务设置",  
                style = MaterialTheme.typography.titleSmall,  
                fontWeight = FontWeight.Bold,  
                color = MaterialTheme.colorScheme.onSecondaryContainer,  
                modifier = Modifier.weight(1f)  
            )  
            if (isLoading || isSaving) {  
                CircularProgressIndicator(  
                    modifier = Modifier.size(18.dp),  
                    strokeWidth = 2.dp,  
                    color = MaterialTheme.colorScheme.onSecondaryContainer  
                )  
                Spacer(modifier = Modifier.width(8.dp))  
            }  
            if (expanded) {  
                IconButton(  
                    onClick = onRefresh,  
                    modifier = Modifier.size(32.dp)  
                ) {  
                    Icon(  
                        imageVector = Icons.Default.Refresh,  
                        contentDescription = "刷新",  
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,  
                        modifier = Modifier.size(18.dp)  
                    )  
                }  
            }  
            Icon(  
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,  
                contentDescription = if (expanded) "收起" else "展开",  
                tint = MaterialTheme.colorScheme.onSecondaryContainer  
            )  
        }  
    }  
}  
  
// ==================== 定时任务设置内容 ====================  
  
@Composable  
private fun CronSettingsSection(  
    cronConfigs: List<CronConfig>,  
    isLoading: Boolean,  
    onToggleCron: (Int, Boolean) -> Unit,  
    onUpdateTime: (Int, String) -> Unit,  
    onToggleClanbattleRun: (Int, Boolean) -> Unit,  
    onUpdateModuleExcludeType: (Int, List<String>) -> Unit  
) {  
    Column(  
        modifier = Modifier  
            .fillMaxWidth()  
            .padding(top = 8.dp),  
        verticalArrangement = Arrangement.spacedBy(6.dp)  
    ) {  
        if (isLoading && cronConfigs.isEmpty()) {  
            Box(  
                modifier = Modifier  
                    .fillMaxWidth()  
                    .padding(vertical = 24.dp),  
                contentAlignment = Alignment.Center  
            ) {  
                Column(horizontalAlignment = Alignment.CenterHorizontally) {  
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)  
                    Spacer(modifier = Modifier.height(8.dp))  
                    Text(  
                        text = "加载定时配置...",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                }  
            }  
        } else if (cronConfigs.isEmpty()) {  
            Text(  
                text = "暂无定时任务配置",  
                style = MaterialTheme.typography.bodyMedium,  
                color = MaterialTheme.colorScheme.onSurfaceVariant,  
                modifier = Modifier  
                    .fillMaxWidth()  
                    .padding(vertical = 16.dp),  
                textAlign = TextAlign.Center  
            )  
        } else {  
            cronConfigs.forEach { cron ->  
                CronItemCard(  
                    cron = cron,  
                    onToggle = { enabled -> onToggleCron(cron.index, enabled) },  
                    onUpdateTime = { time -> onUpdateTime(cron.index, time) },  
                    onToggleClanbattleRun = { run -> onToggleClanbattleRun(cron.index, run) },  
                    onUpdateModuleExcludeType = { types -> onUpdateModuleExcludeType(cron.index, types) }  
                )  
            }  
        }  
    }  
}  
  
// ==================== 单个定时任务卡片 ====================  
  
@OptIn(ExperimentalLayoutApi::class)  
@Composable  
private fun CronItemCard(  
    cron: CronConfig,  
    onToggle: (Boolean) -> Unit,  
    onUpdateTime: (String) -> Unit,  
    onToggleClanbattleRun: (Boolean) -> Unit,  
    onUpdateModuleExcludeType: (List<String>) -> Unit  
) {  
    var expanded by remember { mutableStateOf(false) }  
    var showTimeDialog by remember { mutableStateOf(false) }  
  
    // 时间编辑弹窗  
    if (showTimeDialog) {  
        TimeEditDialog(  
            currentTime = cron.time,  
            onConfirm = { newTime ->  
                showTimeDialog = false  
                onUpdateTime(newTime)  
            },  
            onDismiss = { showTimeDialog = false }  
        )  
    }  
  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),  
        colors = CardDefaults.cardColors(  
            containerColor = if (cron.enabled)  
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)  
            else  
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)  
        )  
    ) {  
        Column(modifier = Modifier.padding(12.dp)) {  
            // 主行：开关 + 名称 + 时间 + 展开按钮  
            Row(  
                modifier = Modifier.fillMaxWidth(),  
                verticalAlignment = Alignment.CenterVertically  
            ) {  
                Switch(  
                    checked = cron.enabled,  
                    onCheckedChange = onToggle,  
                    modifier = Modifier.size(width = 46.dp, height = 24.dp)  
                )  
                Spacer(modifier = Modifier.width(10.dp))  
                Column(modifier = Modifier.weight(1f)) {  
                    Text(  
                        text = "定时任务${cron.index}",  
                        style = MaterialTheme.typography.bodyMedium,  
                        fontWeight = FontWeight.Bold  
                    )  
                    if (cron.enabled) {  
                        Text(  
                            text = "执行时间: ${cron.time}",  
                            style = MaterialTheme.typography.bodySmall,  
                            color = MaterialTheme.colorScheme.primary  
                        )  
                    }  
                }  
                // 编辑时间按钮  
                IconButton(  
                    onClick = { showTimeDialog = true },  
                    modifier = Modifier.size(32.dp)  
                ) {  
                    Icon(  
                        imageVector = Icons.Default.AccessTime,  
                        contentDescription = "编辑时间",  
                        tint = MaterialTheme.colorScheme.primary,  
                        modifier = Modifier.size(18.dp)  
                    )  
                }  
                // 展开/收起详细设置  
                IconButton(  
                    onClick = { expanded = !expanded },  
                    modifier = Modifier.size(32.dp)  
                ) {  
                    Icon(  
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,  
                        contentDescription = if (expanded) "收起" else "更多设置",  
                        modifier = Modifier.size(18.dp)  
                    )  
                }  
            }  
  
            // 展开的详细设置  
            AnimatedVisibility(  
                visible = expanded,  
                enter = expandVertically(),  
                exit = shrinkVertically()  
            ) {  
                Column(  
                    modifier = Modifier.padding(top = 8.dp)  
                ) {  
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))  
  
                    // 会战期间执行  
                    Row(  
                        modifier = Modifier.fillMaxWidth(),  
                        verticalAlignment = Alignment.CenterVertically  
                    ) {  
                        Text(  
                            text = "会战期间执行",  
                            style = MaterialTheme.typography.bodyMedium,  
                            modifier = Modifier.weight(1f)  
                        )  
                        Switch(  
                            checked = cron.clanbattleRun,  
                            onCheckedChange = onToggleClanbattleRun,  
                            modifier = Modifier.size(width = 46.dp, height = 24.dp)  
                        )  
                    }  
  
                    Spacer(modifier = Modifier.height(8.dp))  
  
                    // 不执行日常模块  
                    Text(  
                        text = "不执行日常模块",  
                        style = MaterialTheme.typography.bodyMedium  
                    )  
                    Spacer(modifier = Modifier.height(4.dp))  
                    FlowRow(  
                        horizontalArrangement = Arrangement.spacedBy(8.dp)  
                    ) {  
                        MODULE_EXCLUDE_CANDIDATES.forEach { candidate ->  
                            val selected = cron.moduleExcludeType.contains(candidate)  
                            FilterChip(  
                                selected = selected,  
                                onClick = {  
                                    val newList = if (selected) {  
                                        cron.moduleExcludeType - candidate  
                                    } else {  
                                        cron.moduleExcludeType + candidate  
                                    }  
                                    onUpdateModuleExcludeType(newList)  
                                },  
                                label = { Text(candidate) }  
                            )  
                        }  
                    }  
                }  
            }  
        }  
    }  
}  
  
// ==================== 时间编辑弹窗 ====================  
  
@Composable  
private fun TimeEditDialog(  
    currentTime: String,  
    onConfirm: (String) -> Unit,  
    onDismiss: () -> Unit  
) {  
    val parts = currentTime.split(":")  
    var hour by remember { mutableStateOf(parts.getOrElse(0) { "00" }) }  
    var minute by remember { mutableStateOf(parts.getOrElse(1) { "00" }) }  
  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text("设置执行时间") },  
        text = {  
            Column {  
                Text(  
                    text = "请输入24小时制时间",  
                    style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
                Spacer(modifier = Modifier.height(16.dp))  
                Row(  
                    modifier = Modifier.fillMaxWidth(),  
                    horizontalArrangement = Arrangement.Center,  
                    verticalAlignment = Alignment.CenterVertically  
                ) {  
                    OutlinedTextField(  
                        value = hour,  
                        onValueChange = { v ->  
                            if (v.length <= 2 && v.all { it.isDigit() }) hour = v  
                        },  
                        modifier = Modifier.width(80.dp),  
                        label = { Text("时") },  
                        singleLine = true,  
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  
                        textStyle = MaterialTheme.typography.headlineSmall.copy(  
                            textAlign = TextAlign.Center  
                        )  
                    )  
                    Text(  
                        text = ":",  
                        style = MaterialTheme.typography.headlineSmall,  
                        modifier = Modifier.padding(horizontal = 8.dp)  
                    )  
                    OutlinedTextField(  
                        value = minute,  
                        onValueChange = { v ->  
                            if (v.length <= 2 && v.all { it.isDigit() }) minute = v  
                        },  
                        modifier = Modifier.width(80.dp),  
                        label = { Text("分") },  
                        singleLine = true,  
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  
                        textStyle = MaterialTheme.typography.headlineSmall.copy(  
                            textAlign = TextAlign.Center  
                        )  
                    )  
                }  
            }  
        },  
        confirmButton = {  
            TextButton(  
                onClick = {  
                    val h = (hour.toIntOrNull() ?: 0).coerceIn(0, 23)  
                    val m = (minute.toIntOrNull() ?: 0).coerceIn(0, 59)  
                    onConfirm("%02d:%02d".format(h, m))  
                }  
            ) {  
                Text("确定")  
            }  
        },  
        dismissButton = {  
            TextButton(onClick = onDismiss) {  
                Text("取消")  
            }  
        }  
    )  
}  
  
// ==================== 指令卡片 ====================  
  
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