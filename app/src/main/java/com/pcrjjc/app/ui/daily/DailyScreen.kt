package com.pcrjjc.app.ui.daily  

import androidx.compose.material3.OutlinedButton  
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
import androidx.compose.material.icons.filled.Search  
import androidx.compose.material.icons.filled.Clear
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
import com.pcrjjc.app.ui.components.ImageTopAppBar
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
import androidx.compose.material.icons.filled.Add    
import androidx.compose.material.icons.filled.Edit    
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Checklist  
import androidx.compose.foundation.horizontalScroll  
import androidx.compose.foundation.layout.heightIn  
import androidx.compose.ui.text.style.TextOverflow
import android.graphics.BitmapFactory  
import android.util.Base64  
import androidx.compose.foundation.Image  
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.shadow  
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.filled.Build  
import androidx.compose.material.icons.filled.CalendarMonth  
import androidx.compose.material.icons.filled.Groups  
import androidx.compose.material.icons.filled.Warning  
import androidx.compose.ui.graphics.Color  
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTransformGestures  
import androidx.compose.ui.graphics.graphicsLayer  
import androidx.compose.ui.input.pointer.pointerInput  
import androidx.compose.ui.window.Dialog  
import androidx.compose.ui.window.DialogProperties  
import androidx.compose.material.icons.filled.Close  
import androidx.compose.runtime.mutableFloatStateOf
  
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
	
	// 显示日常配置错误  
    LaunchedEffect(uiState.dailyError) {  
        uiState.dailyError?.let {  
            snackbarHostState.showSnackbar(it)  
            viewModel.clearDailyError()  
        }  
    }
  
    // 指令输入弹窗状态  
    var showCommandDialog by remember { mutableStateOf(false) }  
    var commandDialogText by remember { mutableStateOf("") }  
	
	// 显示工具模块错误  
    LaunchedEffect(uiState.toolError) {  
        uiState.toolError?.let {  
            snackbarHostState.showSnackbar(it)  
            viewModel.clearSectionError("tool")  
        }  
    }  
  
    // 显示角色模块错误  
    LaunchedEffect(uiState.unitError) {  
        uiState.unitError?.let {  
            snackbarHostState.showSnackbar(it)  
            viewModel.clearSectionError("unit")  
        }  
    }  
  
    // 显示规划模块错误  
    LaunchedEffect(uiState.planningError) {  
        uiState.planningError?.let {  
            snackbarHostState.showSnackbar(it)  
            viewModel.clearSectionError("planning")  
        }  
    }  
  
    // 显示公会模块错误  
    LaunchedEffect(uiState.clanError) {  
        uiState.clanError?.let {  
            snackbarHostState.showSnackbar(it)  
            viewModel.clearSectionError("clan")  
        }  
    }  
  
    // 显示危险模块错误  
    LaunchedEffect(uiState.dangerError) {  
        uiState.dangerError?.let {  
            snackbarHostState.showSnackbar(it)  
            viewModel.clearSectionError("danger")  
        }  
    }
  
    // 执行结果弹窗（支持后端渲染的图片，图片支持双指缩放）  
    if (uiState.showResultDialog) {  
        val imageBase64 = uiState.resultImageBase64  
        val bitmap = remember(imageBase64) {  
            if (imageBase64 != null) {  
                try {  
                    val bytes = Base64.decode(imageBase64 as String, Base64.DEFAULT)  
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)  
                } catch (e: Exception) { null }  
            } else null  
        }  
  
        if (bitmap != null) {  
            Dialog(  
                onDismissRequest = { viewModel.dismissResult() },  
                properties = DialogProperties(usePlatformDefaultWidth = false)  
            ) {  
                Box(  
                    modifier = Modifier  
                        .fillMaxSize()  
                        .background(Color.Black.copy(alpha = 0.9f))  
                ) {  
                    var scale by remember { mutableFloatStateOf(1f) }  
                    var offsetX by remember { mutableFloatStateOf(0f) }  
                    var offsetY by remember { mutableFloatStateOf(0f) }  
  
                    Image(  
                        bitmap = bitmap.asImageBitmap(),  
                        contentDescription = "执行结果",  
                        modifier = Modifier  
                            .fillMaxWidth()  
                            .align(Alignment.Center)  
                            .graphicsLayer(  
                                scaleX = scale,  
                                scaleY = scale,  
                                translationX = offsetX,  
                                translationY = offsetY  
                            )  
                            .pointerInput(Unit) {  
                                detectTransformGestures { _, pan, zoom, _ ->  
                                    scale = (scale * zoom).coerceIn(1f, 5f)  
                                    if (scale > 1f) {  
                                        offsetX += pan.x  
                                        offsetY += pan.y  
                                    } else {  
                                        offsetX = 0f  
                                        offsetY = 0f  
                                    }  
                                }  
                            }  
                    )  
  
                    IconButton(  
                        onClick = { viewModel.dismissResult() },  
                        modifier = Modifier  
                            .align(Alignment.TopEnd)  
                            .padding(16.dp)  
                    ) {  
                        Icon(  
                            imageVector = Icons.Default.Close,  
                            contentDescription = "关闭",  
                            tint = Color.White  
                        )  
                    }  
                }  
            }  
        } else {  
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
  
    // 创建账号弹窗  
    if (uiState.showCreateAccountDialog) {  
        CreateAccountDialog(  
            alias = uiState.createAccountAlias,  
            username = uiState.createAccountUsername,  
            password = uiState.createAccountPassword,  
            channel = uiState.createAccountChannel,  
            isCreating = uiState.isCreatingAccount,  
            error = uiState.createAccountError,  
            onAliasChange = viewModel::onCreateAliasChanged,  
            onUsernameChange = viewModel::onCreateUsernameChanged,  
            onPasswordChange = viewModel::onCreatePasswordChanged,  
            onChannelChange = viewModel::onCreateChannelChanged,  
            onConfirm = viewModel::createAccount,  
            onDismiss = viewModel::dismissCreateDialog  
        )  
    }  
  
    // 编辑账号弹窗  
    if (uiState.showEditAccountDialog) {  
        EditAccountDialog(  
            alias = uiState.editAccountAlias,  
            username = uiState.editAccountUsername,  
            password = uiState.editAccountPassword,  
            channel = uiState.editAccountChannel,  
            isLoading = uiState.isEditingAccount,  
            error = uiState.editAccountError,  
            onUsernameChange = viewModel::onEditUsernameChanged,  
            onPasswordChange = viewModel::onEditPasswordChanged,  
            onChannelChange = viewModel::onEditChannelChanged,  
            onConfirm = viewModel::saveAccountInfo,  
            onDismiss = viewModel::dismissEditDialog  
        )  
    }  
  
    // 删除确认弹窗  
    if (uiState.showDeleteConfirmDialog) {  
        AlertDialog(  
            onDismissRequest = { viewModel.dismissDeleteConfirm() },  
            title = { Text("确认删除") },  
            text = { Text("确定要删除账号「${uiState.deleteTargetAlias}」吗？此操作不可撤销。") },  
            confirmButton = {  
                TextButton(  
                    onClick = { viewModel.deleteAccount() },  
                    enabled = !uiState.isDeletingAccount  
                ) {  
                    Text("删除", color = MaterialTheme.colorScheme.error)  
                }  
            },  
            dismissButton = {  
                TextButton(onClick = { viewModel.dismissDeleteConfirm() }) {  
                    Text("取消")  
                }  
            }  
        )  
    }  
  
    // ---- 修改密码弹窗 ----  
    if (uiState.showChangePasswordDialog) {  
        AlertDialog(  
            onDismissRequest = { viewModel.dismissChangePasswordDialog() },  
            title = { Text("修改登录密码") },  
            text = {  
                OutlinedTextField(  
                    value = uiState.newPasswordInput,  
                    onValueChange = viewModel::onNewPasswordInputChanged,  
                    label = { Text("新密码") },  
                    singleLine = true,  
                    visualTransformation = PasswordVisualTransformation(),  
                    modifier = Modifier.fillMaxWidth()  
                )  
            },  
            confirmButton = {  
                TextButton(  
                    onClick = { viewModel.changePassword() },  
                    enabled = !uiState.isChangingPassword  
                ) {  
                    if (uiState.isChangingPassword) {  
                        CircularProgressIndicator(  
                            modifier = Modifier.size(16.dp),  
                            strokeWidth = 2.dp  
                        )  
                        Spacer(modifier = Modifier.width(8.dp))  
                    }  
                    Text("确认修改")  
                }  
            },  
            dismissButton = {  
                TextButton(onClick = { viewModel.dismissChangePasswordDialog() }) {  
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
            ImageTopAppBar(  
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
				}  
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
                    onSelectAccount = viewModel::selectAccount,    
                    onCreateAccount = viewModel::showCreateDialog,    
                    onChangePassword = viewModel::showChangePasswordDialog, 
					onEditAccount = viewModel::showEditDialog,    
                    onDeleteAccount = viewModel::showDeleteConfirm    
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
                    onUpdateModuleExcludeType = viewModel::updateModuleExcludeType,  
                    // 日常模块相关  
                    showDailySection = uiState.showDailySection,  
                    dailyModules = uiState.dailyModules,  
                    isLoadingDaily = uiState.isLoadingDaily,  
                    isSavingDaily = uiState.isSavingDaily,  
                    expandedModuleKey = uiState.expandedModuleKey,  
                    onToggleDailySection = viewModel::toggleDailySection,  
                    onRefreshDaily = viewModel::loadDailyConfig,  
                    onToggleDailyModule = viewModel::toggleDailyModule,  
                    onUpdateDailyConfig = viewModel::updateDailyConfig,  
                    onUpdateDailyConfigList = viewModel::updateDailyConfigList,  
                    onExpandDailyModule = viewModel::expandDailyModule,
                    // 工具模块相关  
                    showToolSection = uiState.showToolSection,  
                    toolModules = uiState.toolModules,  
                    isLoadingTool = uiState.isLoadingTool,  
                    isSavingTool = uiState.isSavingTool,  
                    expandedToolModuleKey = uiState.expandedToolModuleKey,  
                    onToggleToolSection = { viewModel.toggleSection("tool") },  
                    onRefreshTool = { viewModel.loadSectionConfig("tool") },  
                    onToggleToolModule = { key, enabled -> viewModel.toggleSectionModule("tool", key, enabled) },  
                    onUpdateToolConfig = { key, value -> viewModel.updateSectionConfig("tool", key, value) },  
                    onUpdateToolConfigList = { key, values -> viewModel.updateSectionConfigList("tool", key, values) },  
                    onExpandToolModule = { key -> viewModel.expandSectionModule("tool", key) },  
                    // 角色模块相关  
                    showUnitSection = uiState.showUnitSection,  
                    unitModules = uiState.unitModules,  
                    isLoadingUnit = uiState.isLoadingUnit,  
                    isSavingUnit = uiState.isSavingUnit,  
                    expandedUnitModuleKey = uiState.expandedUnitModuleKey,  
                    onToggleUnitSection = { viewModel.toggleSection("unit") },  
                    onRefreshUnit = { viewModel.loadSectionConfig("unit") },  
                    onToggleUnitModule = { key, enabled -> viewModel.toggleSectionModule("unit", key, enabled) },  
                    onUpdateUnitConfig = { key, value -> viewModel.updateSectionConfig("unit", key, value) },  
                    onUpdateUnitConfigList = { key, values -> viewModel.updateSectionConfigList("unit", key, values) },  
                    onExpandUnitModule = { key -> viewModel.expandSectionModule("unit", key) },  
                    // 规划模块相关  
                    showPlanningSection = uiState.showPlanningSection,  
                    planningModules = uiState.planningModules,  
                    isLoadingPlanning = uiState.isLoadingPlanning,  
                    isSavingPlanning = uiState.isSavingPlanning,  
                    expandedPlanningModuleKey = uiState.expandedPlanningModuleKey,  
                    onTogglePlanningSection = { viewModel.toggleSection("planning") },  
                    onRefreshPlanning = { viewModel.loadSectionConfig("planning") },  
                    onTogglePlanningModule = { key, enabled -> viewModel.toggleSectionModule("planning", key, enabled) },  
                    onUpdatePlanningConfig = { key, value -> viewModel.updateSectionConfig("planning", key, value) },  
                    onUpdatePlanningConfigList = { key, values -> viewModel.updateSectionConfigList("planning", key, values) },  
                    onExpandPlanningModule = { key -> viewModel.expandSectionModule("planning", key) },  
                    // 公会模块相关  
                    showClanSection = uiState.showClanSection,  
                    clanModules = uiState.clanModules,  
                    isLoadingClan = uiState.isLoadingClan,  
                    isSavingClan = uiState.isSavingClan,  
                    expandedClanModuleKey = uiState.expandedClanModuleKey,  
                    onToggleClanSection = { viewModel.toggleSection("clan") },  
                    onRefreshClan = { viewModel.loadSectionConfig("clan") },  
                    onToggleClanModule = { key, enabled -> viewModel.toggleSectionModule("clan", key, enabled) },  
                    onUpdateClanConfig = { key, value -> viewModel.updateSectionConfig("clan", key, value) },  
                    onUpdateClanConfigList = { key, values -> viewModel.updateSectionConfigList("clan", key, values) },  
                    onExpandClanModule = { key -> viewModel.expandSectionModule("clan", key) },  
                    // 危险模块相关  
                    showDangerSection = uiState.showDangerSection,  
                    dangerModules = uiState.dangerModules,  
                    isLoadingDanger = uiState.isLoadingDanger,  
                    isSavingDanger = uiState.isSavingDanger,  
                    expandedDangerModuleKey = uiState.expandedDangerModuleKey,  
                    onToggleDangerSection = { viewModel.toggleSection("danger") },  
                    onRefreshDanger = { viewModel.loadSectionConfig("danger") },  
                    onToggleDangerModule = { key, enabled -> viewModel.toggleSectionModule("danger", key, enabled) },  
                    onUpdateDangerConfig = { key, value -> viewModel.updateSectionConfig("danger", key, value) },  
                    onUpdateDangerConfigList = { key, values -> viewModel.updateSectionConfigList("danger", key, values) },  
                    onExpandDangerModule = { key -> viewModel.expandSectionModule("danger", key) },  
                    // 执行单个模块  
                    executingModuleKey = uiState.executingModuleKey,  
                    onExecuteModule = viewModel::executeModule					
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
    onSelectAccount: (String) -> Unit,  
    onChangePassword: () -> Unit,  
    onCreateAccount: () -> Unit,  
    onEditAccount: (String) -> Unit,  
    onDeleteAccount: (String) -> Unit  
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
    } else {  
        Column(modifier = Modifier.fillMaxSize()) {  
            // 顶部创建账号按钮  
            Row(  
                modifier = Modifier  
                    .fillMaxWidth()  
                    .padding(horizontal = 16.dp, vertical = 8.dp),  
                horizontalArrangement = Arrangement.End  
            ) {  
                Button(onClick = onCreateAccount) {  
                    Icon(Icons.Default.Add, contentDescription = null)  
                    Spacer(modifier = Modifier.width(4.dp))  
                    Text("创建账号")  
                }  
            }  
  
            if (accounts.isEmpty()) {  
                Box(  
                    modifier = Modifier  
                        .fillMaxSize()  
                        .weight(1f),  
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
                        .weight(1f)  
                        .padding(horizontal = 16.dp),  
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
                                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),  
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
                                IconButton(onClick = { onEditAccount(account) }) {  
                                    Icon(  
                                        imageVector = Icons.Default.Edit,  
                                        contentDescription = "编辑",  
                                        tint = MaterialTheme.colorScheme.primary  
                                    )  
                                }  
                                IconButton(onClick = { onDeleteAccount(account) }) {  
                                    Icon(  
                                        imageVector = Icons.Default.Delete,  
                                        contentDescription = "删除",  
                                        tint = MaterialTheme.colorScheme.error  
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
  
            // 修改登录密码按钮（在 Column 内、if/else 之后）  
            Spacer(modifier = Modifier.height(16.dp))  
            OutlinedButton(  
                onClick = onChangePassword,  
                modifier = Modifier  
                    .fillMaxWidth()  
                    .padding(horizontal = 16.dp)  
            ) {  
                Text("修改登录密码")  
            }  
        }  
    }  
}

// ==================== 创建账号弹窗 ====================  
  
@Composable  
private fun CreateAccountDialog(  
    alias: String,  
    username: String,  
    password: String,  
    channel: String,  
    isCreating: Boolean,  
    error: String?,  
    onAliasChange: (String) -> Unit,  
    onUsernameChange: (String) -> Unit,  
    onPasswordChange: (String) -> Unit,  
    onChannelChange: (String) -> Unit,  
    onConfirm: () -> Unit,  
    onDismiss: () -> Unit  
) {  
    AlertDialog(  
        onDismissRequest = { if (!isCreating) onDismiss() },  
        title = { Text("创建账号") },  
        text = {  
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {  
                OutlinedTextField(  
                    value = alias,  
                    onValueChange = onAliasChange,  
                    label = { Text("账号昵称（必填）") },  
                    singleLine = true,  
                    modifier = Modifier.fillMaxWidth()  
                )  
                OutlinedTextField(  
                    value = username,  
                    onValueChange = onUsernameChange,  
                    label = { Text("游戏账号（选填）") },  
                    singleLine = true,  
                    modifier = Modifier.fillMaxWidth()  
                )  
                OutlinedTextField(  
                    value = password,  
                    onValueChange = onPasswordChange,  
                    label = { Text("游戏密码（选填）") },  
                    singleLine = true,  
                    visualTransformation = PasswordVisualTransformation(),  
                    modifier = Modifier.fillMaxWidth()  
                )  
                Text("服区选择", style = MaterialTheme.typography.labelMedium)  
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {  
                    DailyViewModel.CHANNEL_OPTIONS.forEach { option ->  
                        FilterChip(  
                            selected = channel == option,  
                            onClick = { onChannelChange(option) },  
                            label = { Text(option) }  
                        )  
                    }  
                }  
                if (error != null) {  
                    Text(  
                        text = error,  
                        color = MaterialTheme.colorScheme.error,  
                        style = MaterialTheme.typography.bodySmall  
                    )  
                }  
            }  
        },  
        confirmButton = {  
            TextButton(onClick = onConfirm, enabled = !isCreating) {  
                if (isCreating) {  
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)  
                } else {  
                    Text("创建")  
                }  
            }  
        },  
        dismissButton = {  
            TextButton(onClick = onDismiss, enabled = !isCreating) {  
                Text("取消")  
            }  
        }  
    )  
}  
  
// ==================== 编辑账号弹窗 ====================  
  
@Composable  
private fun EditAccountDialog(  
    alias: String,  
    username: String,  
    password: String,  
    channel: String,  
    isLoading: Boolean,  
    error: String?,  
    onUsernameChange: (String) -> Unit,  
    onPasswordChange: (String) -> Unit,  
    onChannelChange: (String) -> Unit,  
    onConfirm: () -> Unit,  
    onDismiss: () -> Unit  
) {  
    AlertDialog(  
        onDismissRequest = { if (!isLoading) onDismiss() },  
        title = { Text("编辑账号 - $alias") },  
        text = {  
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {  
                if (isLoading) {  
                    Box(  
                        modifier = Modifier.fillMaxWidth(),  
                        contentAlignment = Alignment.Center  
                    ) {  
                        CircularProgressIndicator()  
                    }  
                } else {  
                    OutlinedTextField(  
                        value = username,  
                        onValueChange = onUsernameChange,  
                        label = { Text("游戏账号") },  
                        singleLine = true,  
                        modifier = Modifier.fillMaxWidth()  
                    )  
                    OutlinedTextField(  
                        value = password,  
                        onValueChange = onPasswordChange,  
                        label = { Text("游戏密码（留空则不修改）") },  
                        singleLine = true,  
                        visualTransformation = PasswordVisualTransformation(),  
                        modifier = Modifier.fillMaxWidth()  
                    )  
                    Text("服区选择", style = MaterialTheme.typography.labelMedium)  
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {  
                        DailyViewModel.CHANNEL_OPTIONS.forEach { option ->  
                            FilterChip(  
                                selected = channel == option,  
                                onClick = { onChannelChange(option) },  
                                label = { Text(option) }  
                            )  
                        }  
                    }  
                    if (error != null) {  
                        Text(  
                            text = error,  
                            color = MaterialTheme.colorScheme.error,  
                            style = MaterialTheme.typography.bodySmall  
                        )  
                    }  
                }  
            }  
        },  
        confirmButton = {  
            TextButton(onClick = onConfirm, enabled = !isLoading) {  
                Text("保存")  
            }  
        },  
        dismissButton = {  
            TextButton(onClick = onDismiss, enabled = !isLoading) {  
                Text("取消")  
            }  
        }  
    )  
}
  
// ==================== 指令列表界面 ====================  
  
@OptIn(ExperimentalFoundationApi::class)  
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
    onUpdateModuleExcludeType: (Int, List<String>) -> Unit,  
    // 日常模块相关  
    showDailySection: Boolean,  
    dailyModules: List<DailyModuleItem>,  
    isLoadingDaily: Boolean,  
    isSavingDaily: Boolean,  
    expandedModuleKey: String?,  
    onToggleDailySection: () -> Unit,  
    onRefreshDaily: () -> Unit,  
    onToggleDailyModule: (String, Boolean) -> Unit,  
    onUpdateDailyConfig: (String, Any) -> Unit,  
    onUpdateDailyConfigList: (String, List<Any?>) -> Unit,  
    onExpandDailyModule: (String?) -> Unit, 
    // 工具模块相关  
    showToolSection: Boolean,  
    toolModules: List<DailyModuleItem>,  
    isLoadingTool: Boolean,  
    isSavingTool: Boolean,  
    expandedToolModuleKey: String?,  
    onToggleToolSection: () -> Unit,  
    onRefreshTool: () -> Unit,  
    onToggleToolModule: (String, Boolean) -> Unit,  
    onUpdateToolConfig: (String, Any) -> Unit,  
    onUpdateToolConfigList: (String, List<Any?>) -> Unit,  
    onExpandToolModule: (String?) -> Unit,  
    // 角色模块相关  
    showUnitSection: Boolean,  
    unitModules: List<DailyModuleItem>,  
    isLoadingUnit: Boolean,  
    isSavingUnit: Boolean,  
    expandedUnitModuleKey: String?,  
    onToggleUnitSection: () -> Unit,  
    onRefreshUnit: () -> Unit,  
    onToggleUnitModule: (String, Boolean) -> Unit,  
    onUpdateUnitConfig: (String, Any) -> Unit,  
    onUpdateUnitConfigList: (String, List<Any?>) -> Unit,  
    onExpandUnitModule: (String?) -> Unit,  
    // 规划模块相关  
    showPlanningSection: Boolean,  
    planningModules: List<DailyModuleItem>,  
    isLoadingPlanning: Boolean,  
    isSavingPlanning: Boolean,  
    expandedPlanningModuleKey: String?,  
    onTogglePlanningSection: () -> Unit,  
    onRefreshPlanning: () -> Unit,  
    onTogglePlanningModule: (String, Boolean) -> Unit,  
    onUpdatePlanningConfig: (String, Any) -> Unit,  
    onUpdatePlanningConfigList: (String, List<Any?>) -> Unit,  
    onExpandPlanningModule: (String?) -> Unit,  
    // 公会模块相关  
    showClanSection: Boolean,  
    clanModules: List<DailyModuleItem>,  
    isLoadingClan: Boolean,  
    isSavingClan: Boolean,  
    expandedClanModuleKey: String?,  
    onToggleClanSection: () -> Unit,  
    onRefreshClan: () -> Unit,  
    onToggleClanModule: (String, Boolean) -> Unit,  
    onUpdateClanConfig: (String, Any) -> Unit,  
    onUpdateClanConfigList: (String, List<Any?>) -> Unit,  
    onExpandClanModule: (String?) -> Unit,  
    // 危险模块相关  
    showDangerSection: Boolean,  
    dangerModules: List<DailyModuleItem>,  
    isLoadingDanger: Boolean,  
    isSavingDanger: Boolean,  
    expandedDangerModuleKey: String?,  
    onToggleDangerSection: () -> Unit,  
    onRefreshDanger: () -> Unit,  
    onToggleDangerModule: (String, Boolean) -> Unit,  
    onUpdateDangerConfig: (String, Any) -> Unit,  
    onUpdateDangerConfigList: (String, List<Any?>) -> Unit,  
    onExpandDangerModule: (String?) -> Unit,  
    // 执行单个模块  
    executingModuleKey: String?,  
    onExecuteModule: (String) -> Unit	
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
			if (showCronSection) {  
				stickyHeader {  
					Column(  
						modifier = Modifier  
							.zIndex(1f)  
							.shadow(elevation = 4.dp)  
							.background(MaterialTheme.colorScheme.surface)  
					) {  
						Spacer(modifier = Modifier.height(8.dp))  
						CronSectionHeader(  
							expanded = showCronSection,  
							isLoading = isLoadingCron,  
							isSaving = isSavingCron,  
							onToggle = onToggleCronSection,  
							onRefresh = onRefreshCron  
						)  
					}  
				}  
			} else {  
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
  
            // ---- 日常模块区域 ----  
			if (showDailySection) {  
				stickyHeader {  
					Column(  
						modifier = Modifier  
							.zIndex(1f)  
							.shadow(elevation = 4.dp)  
							.background(MaterialTheme.colorScheme.surface)  
					) {  
						DailySectionHeader(  
							expanded = showDailySection,  
							isLoading = isLoadingDaily,  
							isSaving = isSavingDaily,  
							onToggle = onToggleDailySection,  
							onRefresh = onRefreshDaily  
						)  
					}  
				}  
			} else {  
				item {  
					DailySectionHeader(  
						expanded = showDailySection,  
						isLoading = isLoadingDaily,  
						isSaving = isSavingDaily,  
						onToggle = onToggleDailySection,  
						onRefresh = onRefreshDaily  
					)  
				}  
			}   
  
            item {  
                AnimatedVisibility(  
                    visible = showDailySection,  
                    enter = expandVertically(),  
                    exit = shrinkVertically()  
                ) {  
                    DailySettingsSection(  
                        modules = dailyModules,  
                        isLoading = isLoadingDaily,  
                        expandedModuleKey = expandedModuleKey,  
                        onToggleModule = onToggleDailyModule,  
                        onExpandModule = onExpandDailyModule,  
                        onUpdateConfig = onUpdateDailyConfig,  
                        onUpdateConfigList = onUpdateDailyConfigList  
                    )  
                }  
            }  
  
            item {  
                Spacer(modifier = Modifier.height(4.dp))  
                HorizontalDivider()  
                Spacer(modifier = Modifier.height(8.dp))  
            }  
  
            item {  
                Spacer(modifier = Modifier.height(4.dp))  
                HorizontalDivider()  
                Spacer(modifier = Modifier.height(8.dp))  
            }  
  
            // ---- 工具模块区域 ----  
            if (showToolSection) {  
                stickyHeader {  
                    Column(  
                        modifier = Modifier  
                            .zIndex(1f)  
                            .shadow(elevation = 4.dp)  
                            .background(MaterialTheme.colorScheme.surface)  
                    ) {  
                        ModuleSectionHeader(  
                            title = "工具",  
                            icon = Icons.Default.Build,  
                            expanded = showToolSection,  
                            isLoading = isLoadingTool,  
                            isSaving = isSavingTool,  
                            onToggle = onToggleToolSection,  
                            onRefresh = onRefreshTool  
                        )  
                    }  
                }  
            } else {  
                item {  
                    ModuleSectionHeader(  
                        title = "工具",  
                        icon = Icons.Default.Build,  
                        expanded = showToolSection,  
                        isLoading = isLoadingTool,  
                        isSaving = isSavingTool,  
                        onToggle = onToggleToolSection,  
                        onRefresh = onRefreshTool  
                    )  
                }  
            }  
  
            item {  
                AnimatedVisibility(  
                    visible = showToolSection,  
                    enter = expandVertically(),  
                    exit = shrinkVertically()  
                ) {  
                    ExecutableSettingsSection(  
                        modules = toolModules,  
                        isLoading = isLoadingTool,  
                        expandedModuleKey = expandedToolModuleKey,  
                        executingModuleKey = executingModuleKey,  
                        onToggleModule = onToggleToolModule,  
                        onExpandModule = onExpandToolModule,  
                        onExecuteModule = onExecuteModule,  
                        onUpdateConfig = onUpdateToolConfig,  
                        onUpdateConfigList = onUpdateToolConfigList  
                    )  
                }  
            }  
  
            item {  
                Spacer(modifier = Modifier.height(4.dp))  
                HorizontalDivider()  
                Spacer(modifier = Modifier.height(8.dp))  
            }  
  
            // ---- 角色模块区域 ----  
            if (showUnitSection) {  
                stickyHeader {  
                    Column(  
                        modifier = Modifier  
                            .zIndex(1f)  
                            .shadow(elevation = 4.dp)  
                            .background(MaterialTheme.colorScheme.surface)  
                    ) {  
                        ModuleSectionHeader(  
                            title = "角色",  
                            icon = Icons.Default.Person,  
                            expanded = showUnitSection,  
                            isLoading = isLoadingUnit,  
                            isSaving = isSavingUnit,  
                            onToggle = onToggleUnitSection,  
                            onRefresh = onRefreshUnit  
                        )  
                    }  
                }  
            } else {  
                item {  
                    ModuleSectionHeader(  
                        title = "角色",  
                        icon = Icons.Default.Person,  
                        expanded = showUnitSection,  
                        isLoading = isLoadingUnit,  
                        isSaving = isSavingUnit,  
                        onToggle = onToggleUnitSection,  
                        onRefresh = onRefreshUnit  
                    )  
                }  
            }  
  
            item {  
                AnimatedVisibility(  
                    visible = showUnitSection,  
                    enter = expandVertically(),  
                    exit = shrinkVertically()  
                ) {  
                    ExecutableSettingsSection(  
                        modules = unitModules,  
                        isLoading = isLoadingUnit,  
                        expandedModuleKey = expandedUnitModuleKey,  
                        executingModuleKey = executingModuleKey,  
                        onToggleModule = onToggleUnitModule,  
                        onExpandModule = onExpandUnitModule,  
                        onExecuteModule = onExecuteModule,  
                        onUpdateConfig = onUpdateUnitConfig,  
                        onUpdateConfigList = onUpdateUnitConfigList  
                    )  
                }  
            }  
  
            item {  
                Spacer(modifier = Modifier.height(4.dp))  
                HorizontalDivider()  
                Spacer(modifier = Modifier.height(8.dp))  
            }  
  
            // ---- 规划模块区域 ----  
            if (showPlanningSection) {  
                stickyHeader {  
                    Column(  
                        modifier = Modifier  
                            .zIndex(1f)  
                            .shadow(elevation = 4.dp)  
                            .background(MaterialTheme.colorScheme.surface)  
                    ) {  
                        ModuleSectionHeader(  
                            title = "规划",  
                            icon = Icons.Default.CalendarMonth,  
                            expanded = showPlanningSection,  
                            isLoading = isLoadingPlanning,  
                            isSaving = isSavingPlanning,  
                            onToggle = onTogglePlanningSection,  
                            onRefresh = onRefreshPlanning  
                        )  
                    }  
                }  
            } else {  
                item {  
                    ModuleSectionHeader(  
                        title = "规划",  
                        icon = Icons.Default.CalendarMonth,  
                        expanded = showPlanningSection,  
                        isLoading = isLoadingPlanning,  
                        isSaving = isSavingPlanning,  
                        onToggle = onTogglePlanningSection,  
                        onRefresh = onRefreshPlanning  
                    )  
                }  
            }  
  
            item {  
                AnimatedVisibility(  
                    visible = showPlanningSection,  
                    enter = expandVertically(),  
                    exit = shrinkVertically()  
                ) {  
                    ExecutableSettingsSection(  
                        modules = planningModules,  
                        isLoading = isLoadingPlanning,  
                        expandedModuleKey = expandedPlanningModuleKey,  
                        executingModuleKey = executingModuleKey,  
                        onToggleModule = onTogglePlanningModule,  
                        onExpandModule = onExpandPlanningModule,  
                        onExecuteModule = onExecuteModule,  
                        onUpdateConfig = onUpdatePlanningConfig,  
                        onUpdateConfigList = onUpdatePlanningConfigList  
                    )  
                }  
            }  
  
            item {  
                Spacer(modifier = Modifier.height(4.dp))  
                HorizontalDivider()  
                Spacer(modifier = Modifier.height(8.dp))  
            }  
  
            // ---- 公会模块区域 ----  
            if (showClanSection) {  
                stickyHeader {  
                    Column(  
                        modifier = Modifier  
                            .zIndex(1f)  
                            .shadow(elevation = 4.dp)  
                            .background(MaterialTheme.colorScheme.surface)  
                    ) {  
                        ModuleSectionHeader(  
                            title = "公会",  
                            icon = Icons.Default.Groups,  
                            expanded = showClanSection,  
                            isLoading = isLoadingClan,  
                            isSaving = isSavingClan,  
                            onToggle = onToggleClanSection,  
                            onRefresh = onRefreshClan  
                        )  
                    }  
                }  
            } else {  
                item {  
                    ModuleSectionHeader(  
                        title = "公会",  
                        icon = Icons.Default.Groups,  
                        expanded = showClanSection,  
                        isLoading = isLoadingClan,  
                        isSaving = isSavingClan,  
                        onToggle = onToggleClanSection,  
                        onRefresh = onRefreshClan  
                    )  
                }  
            }  
  
            item {  
                AnimatedVisibility(  
                    visible = showClanSection,  
                    enter = expandVertically(),  
                    exit = shrinkVertically()  
                ) {  
                    ExecutableSettingsSection(  
                        modules = clanModules,  
                        isLoading = isLoadingClan,  
                        expandedModuleKey = expandedClanModuleKey,  
                        executingModuleKey = executingModuleKey,  
                        onToggleModule = onToggleClanModule,  
                        onExpandModule = onExpandClanModule,  
                        onExecuteModule = onExecuteModule,  
                        onUpdateConfig = onUpdateClanConfig,  
                        onUpdateConfigList = onUpdateClanConfigList  
                    )  
                }  
            }  
  
            item {  
                Spacer(modifier = Modifier.height(4.dp))  
                HorizontalDivider()  
                Spacer(modifier = Modifier.height(8.dp))  
            }  
  
            // ---- 危险模块区域 ----  
            if (showDangerSection) {  
                stickyHeader {  
                    Column(  
                        modifier = Modifier  
                            .zIndex(1f)  
                            .shadow(elevation = 4.dp)  
                            .background(MaterialTheme.colorScheme.surface)  
                    ) {  
                        ModuleSectionHeader(  
                            title = "危险",  
                            icon = Icons.Default.Warning,  
                            containerColor = MaterialTheme.colorScheme.errorContainer,  
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,  
                            expanded = showDangerSection,  
                            isLoading = isLoadingDanger,  
                            isSaving = isSavingDanger,  
                            onToggle = onToggleDangerSection,  
                            onRefresh = onRefreshDanger  
                        )  
                    }  
                }  
            } else {  
                item {  
                    ModuleSectionHeader(  
                        title = "危险",  
                        icon = Icons.Default.Warning,  
                        containerColor = MaterialTheme.colorScheme.errorContainer,  
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,  
                        expanded = showDangerSection,  
                        isLoading = isLoadingDanger,  
                        isSaving = isSavingDanger,  
                        onToggle = onToggleDangerSection,  
                        onRefresh = onRefreshDanger  
                    )  
                }  
            }  
  
            item {  
                AnimatedVisibility(  
                    visible = showDangerSection,  
                    enter = expandVertically(),  
                    exit = shrinkVertically()  
                ) {  
                    ExecutableSettingsSection(  
                        modules = dangerModules,  
                        isLoading = isLoadingDanger,  
                        expandedModuleKey = expandedDangerModuleKey,  
                        executingModuleKey = executingModuleKey,  
                        onToggleModule = onToggleDangerModule,  
                        onExpandModule = onExpandDangerModule,  
                        onExecuteModule = onExecuteModule,  
                        onUpdateConfig = onUpdateDangerConfig,  
                        onUpdateConfigList = onUpdateDangerConfigList  
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

// ==================== 日常模块区域头部 ====================  
  
@Composable  
private fun DailySectionHeader(  
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
            containerColor = MaterialTheme.colorScheme.tertiaryContainer  
        )  
    ) {  
        Row(  
            modifier = Modifier  
                .fillMaxWidth()  
                .padding(horizontal = 16.dp, vertical = 12.dp),  
            verticalAlignment = Alignment.CenterVertically  
        ) {  
            Icon(  
                imageVector = Icons.Default.Checklist,  
                contentDescription = null,  
                tint = MaterialTheme.colorScheme.onTertiaryContainer,  
                modifier = Modifier.size(22.dp)  
            )  
            Spacer(modifier = Modifier.width(10.dp))  
            Text(  
                text = "日常模块设置",  
                style = MaterialTheme.typography.titleSmall,  
                fontWeight = FontWeight.Bold,  
                color = MaterialTheme.colorScheme.onTertiaryContainer,  
                modifier = Modifier.weight(1f)  
            )  
            if (isLoading || isSaving) {  
                CircularProgressIndicator(  
                    modifier = Modifier.size(18.dp),  
                    strokeWidth = 2.dp,  
                    color = MaterialTheme.colorScheme.onTertiaryContainer  
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
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,  
                        modifier = Modifier.size(18.dp)  
                    )  
                }  
            }  
            Icon(  
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,  
                contentDescription = if (expanded) "收起" else "展开",  
                tint = MaterialTheme.colorScheme.onTertiaryContainer  
            )  
        }  
    }  
}  
  
// ==================== 日常模块设置内容 ====================  
  
@Composable  
private fun DailySettingsSection(  
    modules: List<DailyModuleItem>,  
    isLoading: Boolean,  
    expandedModuleKey: String?,  
    onToggleModule: (String, Boolean) -> Unit,  
    onExpandModule: (String?) -> Unit,  
    onUpdateConfig: (String, Any) -> Unit,  
    onUpdateConfigList: (String, List<Any?>) -> Unit  
) {  
    Column(  
        modifier = Modifier  
            .fillMaxWidth()  
            .padding(top = 8.dp),  
        verticalArrangement = Arrangement.spacedBy(6.dp)  
    ) {  
        if (isLoading && modules.isEmpty()) {  
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
                        text = "加载日常配置...",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                }  
            }  
        } else if (modules.isEmpty()) {  
            Text(  
                text = "暂无日常模块配置",  
                style = MaterialTheme.typography.bodyMedium,  
                color = MaterialTheme.colorScheme.onSurfaceVariant,  
                modifier = Modifier  
                    .fillMaxWidth()  
                    .padding(vertical = 16.dp),  
                textAlign = TextAlign.Center  
            )  
        } else {  
            modules.forEach { module ->  
                DailyModuleCard(  
                    module = module,  
                    isExpanded = expandedModuleKey == module.key,  
                    onToggle = { enabled -> onToggleModule(module.key, enabled) },  
                    onExpand = { onExpandModule(module.key) },  
                    onUpdateConfig = onUpdateConfig,  
                    onUpdateConfigList = onUpdateConfigList  
                )  
            }  
        }  
    }  
}  
  
// ==================== 单个日常模块卡片 ====================  
  
@OptIn(ExperimentalLayoutApi::class)  
@Composable  
private fun DailyModuleCard(  
    module: DailyModuleItem,  
    isExpanded: Boolean,  
    onToggle: (Boolean) -> Unit,  
    onExpand: () -> Unit,  
    onUpdateConfig: (String, Any) -> Unit,  
    onUpdateConfigList: (String, List<Any?>) -> Unit  
) {  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),  
        colors = CardDefaults.cardColors(  
            containerColor = if (module.enabled)  
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)  
            else  
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)  
        )  
    ) {  
        Column(modifier = Modifier.padding(12.dp)) {  
            // 主行：开关 + 名称 + 展开按钮  
            Row(  
                modifier = Modifier.fillMaxWidth(),  
                verticalAlignment = Alignment.CenterVertically  
            ) {  
                Switch(  
                    checked = module.enabled,  
                    onCheckedChange = onToggle,  
                    modifier = Modifier.size(width = 46.dp, height = 24.dp)  
                )  
                Spacer(modifier = Modifier.width(10.dp))  
                Column(modifier = Modifier.weight(1f)) {  
                    Text(  
                        text = module.name,  
                        style = MaterialTheme.typography.bodyMedium,  
                        fontWeight = FontWeight.Bold,  
                        maxLines = 1,  
                        overflow = TextOverflow.Ellipsis  
                    )  
                    if (module.description.isNotBlank()) {  
                        Text(  
                            text = module.description,  
                            style = MaterialTheme.typography.bodySmall,  
                            color = MaterialTheme.colorScheme.onSurfaceVariant,  
                            maxLines = 1,  
                            overflow = TextOverflow.Ellipsis  
                        )  
                    }  
                }  
                if (module.configs.isNotEmpty()) {  
                    IconButton(  
                        onClick = onExpand,  
                        modifier = Modifier.size(32.dp)  
                    ) {  
                        Icon(  
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,  
                            contentDescription = if (isExpanded) "收起" else "展开配置",  
                            modifier = Modifier.size(18.dp)  
                        )  
                    }  
                }  
            }  
  
            // 展开的子配置  
            AnimatedVisibility(  
                visible = isExpanded && module.configs.isNotEmpty(),  
                enter = expandVertically(),  
                exit = shrinkVertically()  
            ) {  
                Column(modifier = Modifier.padding(top = 8.dp)) {  
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))  
                    module.configs.forEach { cfg ->  
                        DailyConfigItemView(  
                            config = cfg,  
                            onUpdateConfig = onUpdateConfig,  
                            onUpdateConfigList = onUpdateConfigList  
                        )  
                        Spacer(modifier = Modifier.height(6.dp))  
                    }  
                }  
            }  
        }  
    }  
}  
  
// ==================== 单个配置项渲染 ====================

private const val MAX_VISIBLE_CANDIDATES = 15  // 最多显示的候选值数量，超过后需要展开

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyConfigItemView(
    config: DailyConfigEntry,
    onUpdateConfig: (String, Any) -> Unit,
    onUpdateConfigList: (String, List<Any?>) -> Unit
) {
    var showAllCandidates by remember { mutableStateOf(false) }
	var searchQuery by remember { mutableStateOf("") }
    val filteredCandidates = if (searchQuery.isNotBlank()) {  
		config.candidates.filter { cand ->  
			cand.display.contains(searchQuery, ignoreCase = true) ||  
			cand.tags.any { it.contains(searchQuery, ignoreCase = true) }  
		}  
	} else {  
		config.candidates  
	}  
	val visibleCandidates = if (searchQuery.isBlank() && filteredCandidates.size > MAX_VISIBLE_CANDIDATES && !showAllCandidates) {  
		filteredCandidates.take(MAX_VISIBLE_CANDIDATES)  
	} else {  
		filteredCandidates  
	}
    val hasMoreCandidates = searchQuery.isBlank() && filteredCandidates.size > MAX_VISIBLE_CANDIDATES && !showAllCandidates

    Column(modifier = Modifier.fillMaxWidth()) {
        when (config.configType) {
            "bool" -> {
                Row(  
                    modifier = Modifier.fillMaxWidth(),  
                    verticalAlignment = Alignment.CenterVertically  
                ) {  
                    Text(  
                        text = config.desc,  
                        style = MaterialTheme.typography.bodyMedium,  
                        modifier = Modifier.weight(1f)  
                    )  
                    Switch(  
                        checked = config.currentValue as? Boolean ?: config.default as? Boolean ?: false,  
                        onCheckedChange = { onUpdateConfig(config.key, it) },  
                        modifier = Modifier.size(width = 46.dp, height = 24.dp)  
                    )  
                }  
            }  
  
            "text" -> {  
                Text(  
                    text = config.desc,  
                    style = MaterialTheme.typography.bodyMedium  
                )  
                Spacer(modifier = Modifier.height(4.dp))  
                var textValue by remember(config.currentValue) {  
                    mutableStateOf((config.currentValue ?: config.default ?: "").toString())  
                }  
                OutlinedTextField(  
                    value = textValue,  
                    onValueChange = { textValue = it },  
                    modifier = Modifier.fillMaxWidth(),  
                    singleLine = true,  
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),  
                    keyboardActions = KeyboardActions(  
                        onDone = { onUpdateConfig(config.key, textValue) }  
                    )  
                )  
            }  
  
"int" -> {
                Text(
                    text = config.desc,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (config.candidates.isNotEmpty()) {
                    if (config.candidates.size > MAX_VISIBLE_CANDIDATES) {  
                        OutlinedTextField(  
                            value = searchQuery,  
                            onValueChange = { searchQuery = it },  
                            modifier = Modifier.fillMaxWidth(),  
                            singleLine = true,  
                            placeholder = { Text("搜索...") },  
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },  
                            trailingIcon = {  
                                if (searchQuery.isNotEmpty()) {  
                                    IconButton(onClick = { searchQuery = "" }) {  
                                        Icon(Icons.Default.Clear, contentDescription = "清除")  
                                    }  
                                }  
                            }  
                        )  
                        Spacer(modifier = Modifier.height(4.dp))  
                    }
					FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        visibleCandidates.forEach { cand ->
                            val currentVal = config.currentValue?.toString()
                            val candVal = cand.value?.toString()
                            FilterChip(
                                selected = currentVal == candVal,
                                onClick = {
                                    val v = when (cand.value) {
                                        is Number -> cand.value
                                        is String -> cand.value.toIntOrNull() ?: cand.value
                                        else -> cand.value ?: 0
                                    }
                                    onUpdateConfig(config.key, v)
                                },
                                label = { Text(cand.display, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                    if (hasMoreCandidates) {
                        TextButton(onClick = { showAllCandidates = true }) {
                            Text("展开更多 (${config.candidates.size - MAX_VISIBLE_CANDIDATES} more)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    var intText by remember(config.currentValue) {  
                        mutableStateOf((config.currentValue ?: config.default ?: "").toString())  
                    }  
                    OutlinedTextField(  
                        value = intText,  
                        onValueChange = { v -> if (v.all { it.isDigit() || it == '-' }) intText = v },  
                        modifier = Modifier.fillMaxWidth(),  
                        singleLine = true,  
                        keyboardOptions = KeyboardOptions(  
                            keyboardType = KeyboardType.Number,  
                            imeAction = ImeAction.Done  
                        ),  
                        keyboardActions = KeyboardActions(  
                            onDone = { intText.toIntOrNull()?.let { onUpdateConfig(config.key, it) } }  
                        )  
                    )  
                }  
            }  
  
"single" -> {
                Text(
                    text = config.desc,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
				if (config.candidates.size > MAX_VISIBLE_CANDIDATES) {  
					OutlinedTextField(  
						value = searchQuery,  
						onValueChange = { searchQuery = it },  
						modifier = Modifier.fillMaxWidth(),  
						singleLine = true,  
						placeholder = { Text("搜索角色...") },  
						leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },  
						trailingIcon = {  
							if (searchQuery.isNotEmpty()) {  
								IconButton(onClick = { searchQuery = "" }) {  
									Icon(Icons.Default.Clear, contentDescription = "清除")  
								}  
							}  
						}  
					)  
					Spacer(modifier = Modifier.height(4.dp))  
				}
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    visibleCandidates.forEach { cand ->
                        val currentVal = config.currentValue?.toString()
                        val candVal = cand.value?.toString()
                        FilterChip(
                            selected = currentVal == candVal,
                            onClick = { cand.value?.let { onUpdateConfig(config.key, it) } },
                            label = { Text(cand.display, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
                if (hasMoreCandidates) {
                    TextButton(onClick = { showAllCandidates = true }) {
                        Text("展开更多 (${config.candidates.size - MAX_VISIBLE_CANDIDATES} more)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
  
"multi", "multi_search" -> {
                Text(
                    text = config.desc,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
				if (config.candidates.size > MAX_VISIBLE_CANDIDATES) {  
					OutlinedTextField(  
						value = searchQuery,  
						onValueChange = { searchQuery = it },  
						modifier = Modifier.fillMaxWidth(),  
						singleLine = true,  
						placeholder = { Text("搜索角色...") },  
						leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },  
						trailingIcon = {  
							if (searchQuery.isNotEmpty()) {  
								IconButton(onClick = { searchQuery = "" }) {  
									Icon(Icons.Default.Clear, contentDescription = "清除")  
								}  
							}  
						}  
					)  
					Spacer(modifier = Modifier.height(4.dp))  
				}
                // currentValue 是 JSONArray 或 List
                val selectedValues = remember(config.currentValue) {
                    when (val cv = config.currentValue) {
                        is List<*> -> cv.map { it.toString() }.toSet()
                        is org.json.JSONArray -> {
                            (0 until cv.length()).map { cv.get(it).toString() }.toSet()
                        }
                        else -> emptySet()
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    visibleCandidates.forEach { cand ->
                        val candStr = cand.value?.toString() ?: ""
                        val selected = selectedValues.contains(candStr)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val newSet = if (selected) {
                                    selectedValues - candStr
                                } else {
                                    selectedValues + candStr
                                }
                                // 保持原始类型：尝试转回数字
                                val newList = newSet.map { s ->
                                    s.toIntOrNull() ?: s.toDoubleOrNull() ?: s
                                }
                                onUpdateConfigList(config.key, newList)
                            },
                            label = { Text(cand.display, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
                if (hasMoreCandidates) {
                    TextButton(onClick = { showAllCandidates = true }) {
                        Text("展开更多 (${config.candidates.size - MAX_VISIBLE_CANDIDATES} more)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
  
            "time" -> {  
                Row(  
                    modifier = Modifier.fillMaxWidth(),  
                    verticalAlignment = Alignment.CenterVertically  
                ) {  
                    Text(  
                        text = config.desc,  
                        style = MaterialTheme.typography.bodyMedium,  
                        modifier = Modifier.weight(1f)  
                    )  
                    var showTimeDlg by remember { mutableStateOf(false) }  
                    val currentTime = (config.currentValue ?: config.default ?: "00:00").toString()  
                    TextButton(onClick = { showTimeDlg = true }) {  
                        Icon(  
                            imageVector = Icons.Default.AccessTime,  
                            contentDescription = null,  
                            modifier = Modifier.size(16.dp)  
                        )  
                        Spacer(modifier = Modifier.width(4.dp))  
                        Text(currentTime)  
                    }  
                    if (showTimeDlg) {  
                        TimeEditDialog(  
                            currentTime = currentTime,  
                            onConfirm = { newTime ->  
                                showTimeDlg = false  
                                onUpdateConfig(config.key, newTime)  
                            },  
                            onDismiss = { showTimeDlg = false }  
                        )  
                    }  
                }  
            }  
  
            else -> {  
                Text(  
                    text = "${config.desc}: ${config.currentValue ?: config.default ?: ""}",  
                    style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            }  
        }  
    }  
}

// ==================== 通用模块区域标题 ====================  
  
@Composable  
private fun ModuleSectionHeader(  
    title: String,  
    icon: ImageVector,  
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,  
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,  
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
        colors = CardDefaults.cardColors(containerColor = containerColor)  
    ) {  
        Row(  
            modifier = Modifier  
                .fillMaxWidth()  
                .padding(horizontal = 16.dp, vertical = 12.dp),  
            verticalAlignment = Alignment.CenterVertically  
        ) {  
            Icon(  
                imageVector = icon,  
                contentDescription = null,  
                tint = contentColor,  
                modifier = Modifier.size(22.dp)  
            )  
            Spacer(modifier = Modifier.width(10.dp))  
            Text(  
                text = "${title}模块设置",  
                style = MaterialTheme.typography.titleSmall,  
                fontWeight = FontWeight.Bold,  
                color = contentColor,  
                modifier = Modifier.weight(1f)  
            )  
            if (isLoading || isSaving) {  
                CircularProgressIndicator(  
                    modifier = Modifier.size(18.dp),  
                    strokeWidth = 2.dp,  
                    color = contentColor  
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
                        tint = contentColor,  
                        modifier = Modifier.size(18.dp)  
                    )  
                }  
            }  
            Icon(  
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,  
                contentDescription = if (expanded) "收起" else "展开",  
                tint = contentColor  
            )  
        }  
    }  
} 
  
// ==================== 可执行模块列表 ====================  
  
@Composable  
private fun ExecutableSettingsSection(  
    modules: List<DailyModuleItem>,  
    isLoading: Boolean,  
    expandedModuleKey: String?,  
    executingModuleKey: String?,  
    onToggleModule: (String, Boolean) -> Unit,  
    onExpandModule: (String?) -> Unit,  
    onExecuteModule: (String) -> Unit,  
    onUpdateConfig: (String, Any) -> Unit,  
    onUpdateConfigList: (String, List<Any?>) -> Unit  
) {  
    Column(  
        modifier = Modifier  
            .fillMaxWidth()  
            .padding(top = 8.dp),  
        verticalArrangement = Arrangement.spacedBy(6.dp)  
    ) {  
        if (isLoading && modules.isEmpty()) {  
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
                        text = "加载模块配置...",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                }  
            }  
        } else if (modules.isEmpty()) {  
            Text(  
                text = "暂无模块配置",  
                style = MaterialTheme.typography.bodyMedium,  
                color = MaterialTheme.colorScheme.onSurfaceVariant,  
                modifier = Modifier  
                    .fillMaxWidth()  
                    .padding(vertical = 16.dp),  
                textAlign = TextAlign.Center  
            )  
        } else {  
            modules.forEach { module ->  
                ExecutableModuleCard(  
                    module = module,  
                    isExpanded = expandedModuleKey == module.key,  
                    isExecuting = executingModuleKey == module.key,  
                    onToggle = { enabled -> onToggleModule(module.key, enabled) },  
                    onExpand = { onExpandModule(module.key) },  
                    onExecute = { onExecuteModule(module.key) },  
                    onUpdateConfig = onUpdateConfig,  
                    onUpdateConfigList = onUpdateConfigList  
                )  
            }  
        }  
    }  
}  
  
// ==================== 带执行按钮的模块卡片 ====================  
  
@OptIn(ExperimentalLayoutApi::class)  
@Composable  
private fun ExecutableModuleCard(  
    module: DailyModuleItem,  
    isExpanded: Boolean,  
    isExecuting: Boolean,  
    onToggle: (Boolean) -> Unit,  
    onExpand: () -> Unit,  
    onExecute: () -> Unit,  
    onUpdateConfig: (String, Any) -> Unit,  
    onUpdateConfigList: (String, List<Any?>) -> Unit  
) {  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),  
        colors = CardDefaults.cardColors(  
            containerColor = if (module.enabled)  
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)  
            else  
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)  
        )  
    ) {  
        Column(modifier = Modifier.padding(12.dp)) {  
            // 主行：开关 + 名称 + 执行按钮 + 展开按钮  
            Row(  
                modifier = Modifier.fillMaxWidth(),  
                verticalAlignment = Alignment.CenterVertically  
            ) {  
                Switch(  
                    checked = module.enabled,  
                    onCheckedChange = onToggle,  
                    modifier = Modifier.size(width = 46.dp, height = 24.dp)  
                )  
                Spacer(modifier = Modifier.width(10.dp))  
                Column(modifier = Modifier.weight(1f)) {  
                    Text(  
                        text = module.name,  
                        style = MaterialTheme.typography.bodyMedium,  
                        fontWeight = FontWeight.Bold,  
                        maxLines = 1,  
                        overflow = TextOverflow.Ellipsis  
                    )  
                    if (module.description.isNotBlank()) {  
                        Text(  
                            text = module.description,  
                            style = MaterialTheme.typography.bodySmall,  
                            color = MaterialTheme.colorScheme.onSurfaceVariant,  
                            maxLines = 1,  
                            overflow = TextOverflow.Ellipsis  
                        )  
                    }  
                }  
                // 执行按钮  
                IconButton(  
                    onClick = onExecute,  
                    enabled = !isExecuting,  
                    modifier = Modifier.size(32.dp)  
                ) {  
                    if (isExecuting) {  
                        CircularProgressIndicator(  
                            modifier = Modifier.size(18.dp),  
                            strokeWidth = 2.dp  
                        )  
                    } else {  
                        Icon(  
                            imageVector = Icons.Default.PlayArrow,  
                            contentDescription = "执行",  
                            tint = MaterialTheme.colorScheme.primary,  
                            modifier = Modifier.size(20.dp)  
                        )  
                    }  
                }  
                // 展开按钮  
                if (module.configs.isNotEmpty()) {  
                    IconButton(  
                        onClick = onExpand,  
                        modifier = Modifier.size(32.dp)  
                    ) {  
                        Icon(  
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,  
                            contentDescription = if (isExpanded) "收起" else "展开配置",  
                            modifier = Modifier.size(18.dp)  
                        )  
                    }  
                }  
            }  
  
            // 展开的子配置  
            AnimatedVisibility(  
                visible = isExpanded && module.configs.isNotEmpty(),  
                enter = expandVertically(),  
                exit = shrinkVertically()  
            ) {  
                Column(modifier = Modifier.padding(top = 8.dp)) {  
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))  
                    module.configs.forEach { cfg ->  
                        DailyConfigItemView(  
                            config = cfg,  
                            onUpdateConfig = onUpdateConfig,  
                            onUpdateConfigList = onUpdateConfigList  
                        )  
                        Spacer(modifier = Modifier.height(6.dp))  
                    }  
                }  
            }  
        }  
    }  
}

