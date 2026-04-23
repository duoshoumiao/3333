package com.pcrjjc.app.ui.room  
  
import android.content.Context  
import android.content.Intent  
import android.provider.Settings  
import androidx.compose.foundation.layout.*  
import androidx.compose.foundation.rememberScrollState  
import androidx.compose.foundation.verticalScroll  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
import androidx.compose.material.icons.filled.*  
import androidx.compose.material3.*  
import androidx.compose.runtime.*  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.platform.LocalContext  
import androidx.compose.ui.text.font.FontWeight  
import androidx.compose.ui.text.style.TextOverflow  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
import com.pcrjjc.app.data.local.entity.Account  
import com.pcrjjc.app.data.local.entity.BossState  
import com.pcrjjc.app.service.ClanBattleFloatingService  
import com.pcrjjc.app.util.formatBigNum  
import com.pcrjjc.app.util.formatPercent  
  
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)  
@Composable  
fun ClanBattleScreen(  
    viewModel: ClanBattleViewModel = hiltViewModel(),  
    onNavigateBack: () -> Unit = {}  
) {  
    val uiState by viewModel.uiState.collectAsState()  
    val context = LocalContext.current  
    val snackbarHostState = remember { SnackbarHostState() }  
  
    var showAccountPicker by remember { mutableStateOf(false) }  
    var showMyReportDialog by remember { mutableStateOf(false) }  
    var myReportName by remember { mutableStateOf("") }  
    var showReportDialog by remember { mutableStateOf(false) }  
  
    // 错误/Toast 提示  
    LaunchedEffect(uiState.error) {  
        uiState.error?.let {  
            snackbarHostState.showSnackbar(it)  
            viewModel.clearError()  
        }  
    }  
    LaunchedEffect(uiState.toastMessage) {  
        uiState.toastMessage?.let {  
            snackbarHostState.showSnackbar(it)  
            viewModel.clearToast()  
        }  
    }  
    // 战报弹窗  
    LaunchedEffect(uiState.reportText) {  
        if (uiState.reportText.isNotBlank()) {  
            showReportDialog = true  
        }  
    }  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = {  
                    Column {  
                        Text("Boss状态", maxLines = 1, overflow = TextOverflow.Ellipsis)  
                        Text(  
                            text = uiState.roomName.ifBlank { "房间" },  
                            style = MaterialTheme.typography.bodySmall,  
                            color = MaterialTheme.colorScheme.onSurfaceVariant  
                        )  
                    }  
                },  
                navigationIcon = {  
                    IconButton(onClick = onNavigateBack) {  
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")  
                    }  
                },  
                actions = {  
                    // 浮窗按钮  
                    IconButton(onClick = { toggleFloatingWindow(context, viewModel) }) {  
                        Icon(  
                            Icons.Default.PictureInPicture,  
                            contentDescription = "浮窗"  
                        )  
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
                .verticalScroll(rememberScrollState())  
                .padding(horizontal = 12.dp, vertical = 8.dp),  
            verticalArrangement = Arrangement.spacedBy(10.dp)  
        ) {  
            // ==================== 状态信息卡片 ====================  
            StatusInfoCard(uiState)  
  
            // ==================== 初始化中 ====================  
            if (uiState.isInitializing) {  
                Card(  
                    modifier = Modifier.fillMaxWidth(),  
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
                ) {  
                    Row(  
                        modifier = Modifier  
                            .fillMaxWidth()  
                            .padding(16.dp),  
                        horizontalArrangement = Arrangement.Center,  
                        verticalAlignment = Alignment.CenterVertically  
                    ) {  
                        CircularProgressIndicator(  
                            modifier = Modifier.size(20.dp),  
                            strokeWidth = 2.dp  
                        )  
                        Spacer(modifier = Modifier.width(12.dp))  
                        Text("正在登录并初始化会战数据...")  
                    }  
                }  
            }  
  
            // ==================== 5个 Boss 卡片 ====================  
            uiState.battleState.bosses.forEachIndexed { index, boss ->  
                if (boss.maxHp > 0) {  
                    BossCard(  
                        boss = boss,  
                        bossOrder = index + 1,  
                        applies = uiState.battleState.getAppliesForBoss(index + 1),  
                        trees = uiState.battleState.getTreesForBoss(index + 1),  
                        subscribes = uiState.battleState.getSubscribesForBoss(index + 1),  
                        hasApplied = uiState.battleState.hasApplied(uiState.playerQq, index + 1),  
                        hasTree = uiState.battleState.hasTree(uiState.playerQq, index + 1),  
                        hasSubscribed = uiState.battleState.hasSubscribed(uiState.playerQq, index + 1),  
                        onToggleApply = { viewModel.toggleApply(index + 1) },  
                        onToggleTree = { viewModel.toggleTree(index + 1) },  
                        onToggleSubscribe = { viewModel.toggleSubscribe(index + 1) }  
                    )  
                }  
            }  
  
            // ==================== 底部操作按钮 ====================  
            ActionButtonsCard(  
                isMonitoring = uiState.isMonitoring,  
                isLoadingReport = uiState.isLoadingReport,  
                onToggleMonitor = {  
                    if (uiState.isMonitoring) {  
                        viewModel.stopMonitor()  
                    } else {  
                        showAccountPicker = true  
                    }  
                },  
                onCurrentReport = { viewModel.fetchCurrentReport() },  
                onMyReport = { showMyReportDialog = true },  
                onTodayReport = { viewModel.fetchTodayReport() },  
                onYesterdayReport = { viewModel.fetchYesterdayReport() },  
                onSL = { viewModel.recordSL() }  
            )  
  
            Spacer(modifier = Modifier.height(16.dp))  
        }  
    }  
  
    // ==================== 账号选择弹窗 ====================  
    if (showAccountPicker) {  
        AccountPickerDialog(  
            accounts = uiState.masterAccounts,  
            onSelect = { account ->  
                showAccountPicker = false  
                viewModel.startMonitor(account)  
            },  
            onDismiss = { showAccountPicker = false }  
        )  
    }  
  
    // ==================== 我的战报 - 输入游戏名弹窗 ====================  
    if (showMyReportDialog) {  
        AlertDialog(  
            onDismissRequest = { showMyReportDialog = false },  
            title = { Text("我的战报") },  
            text = {  
                OutlinedTextField(  
                    value = myReportName,  
                    onValueChange = { myReportName = it },  
                    modifier = Modifier.fillMaxWidth(),  
                    label = { Text("游戏名称") },  
                    singleLine = true  
                )  
            },  
            confirmButton = {  
                TextButton(onClick = {  
                    showMyReportDialog = false  
                    viewModel.fetchMyReport(myReportName.trim())  
                }) {  
                    Text("查询")  
                }  
            },  
            dismissButton = {  
                TextButton(onClick = { showMyReportDialog = false }) {  
                    Text("取消")  
                }  
            }  
        )  
    }  
  
    // ==================== 战报结果弹窗 ====================  
    if (showReportDialog && uiState.reportText.isNotBlank()) {  
        AlertDialog(  
            onDismissRequest = {  
                showReportDialog = false  
                viewModel.clearReport()  
            },  
            title = { Text("战报") },  
            text = {  
                Column(  
                    modifier = Modifier  
                        .fillMaxWidth()  
                        .heightIn(max = 400.dp)  
                        .verticalScroll(rememberScrollState())  
                ) {  
                    Text(  
                        text = uiState.reportText,  
                        style = MaterialTheme.typography.bodySmall  
                    )  
                }  
            },  
            confirmButton = {  
                TextButton(onClick = {  
                    showReportDialog = false  
                    viewModel.clearReport()  
                }) {  
                    Text("关闭")  
                }  
            }  
        )  
    }  
}  
  
// ==================== 状态信息卡片 ====================  
  
@Composable  
private fun StatusInfoCard(uiState: ClanBattleUiState) {  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),  
        colors = CardDefaults.cardColors(  
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)  
        )  
    ) {  
        Column(  
            modifier = Modifier.padding(12.dp),  
            verticalArrangement = Arrangement.spacedBy(4.dp)  
        ) {  
            Row(  
                modifier = Modifier.fillMaxWidth(),  
                horizontalArrangement = Arrangement.SpaceBetween  
            ) {  
                Text(  
                    "当前排名：${if (uiState.battleState.rank > 0) uiState.battleState.rank.toString() else "--"}",  
                    style = MaterialTheme.typography.bodyMedium,  
                    fontWeight = FontWeight.Bold  
                )  
                Text(  
                    "监控状态：${if (uiState.battleState.isMonitoring) "开启" else "关闭"}"
                    style = MaterialTheme.typography.bodyMedium,  
                    color = if (uiState.battleState.isMonitoring)
                        MaterialTheme.colorScheme.primary  
                    else  
                        MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            }  
            if (uiState.battleState.monitorPlayerName.isNotBlank()) {  
                Text(  
                    "监控人为：${uiState.battleState.monitorPlayerName}",  
                    style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            }  
            if (uiState.battleState.periodName.isNotBlank()) {  
                Text(  
                    "当前进度：${uiState.battleState.periodName}",  
                    style = MaterialTheme.typography.bodyMedium,  
                    fontWeight = FontWeight.Bold  
                )  
            }  
        }  
    }  
}

// ==================== Boss 卡片 ====================  
  
@Composable  
private fun BossCard(  
    boss: BossState,  
    bossOrder: Int,  
    applies: List<com.pcrjjc.app.data.local.entity.ApplyRecord>,  
    trees: List<com.pcrjjc.app.data.local.entity.TreeRecord>,  
    subscribes: List<com.pcrjjc.app.data.local.entity.SubscribeRecord>,  
    hasApplied: Boolean,  
    hasTree: Boolean,  
    hasSubscribed: Boolean,  
    onToggleApply: () -> Unit,  
    onToggleTree: () -> Unit,  
    onToggleSubscribe: () -> Unit  
) {  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
    ) {  
        Row(  
            modifier = Modifier  
                .fillMaxWidth()  
                .padding(12.dp),  
            verticalAlignment = Alignment.Top  
        ) {  
            // 左侧：Boss 信息 + 血条 + 申请/挂树/预约信息  
            Column(  
                modifier = Modifier.weight(1f),  
                verticalArrangement = Arrangement.spacedBy(4.dp)  
            ) {  
                // Boss 标题  
                Text(  
                    text = "${boss.lapNum}周目${bossOrder}王",  
                    style = MaterialTheme.typography.titleSmall,  
                    fontWeight = FontWeight.Bold  
                )  
  
                // 血条  
                LinearProgressIndicator(  
                    progress = { boss.hpPercent.toFloat().coerceIn(0f, 1f) },  
                    modifier = Modifier  
                        .fillMaxWidth()  
                        .height(10.dp),  
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,  
                )  
  
                // HP 文字  
                Text(  
                    text = "HP: ${formatBigNum(boss.currentHp)}/${formatBigNum(boss.maxHp)} ${formatPercent(boss.hpPercent)}",  
                    style = MaterialTheme.typography.bodySmall  
                )  
  
                // 当前挑战人数  
                if (boss.fighterNum > 0) {  
                    Text(  
                        text = "当前有${boss.fighterNum}人挑战",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.tertiary  
                    )  
                }  
  
                // 申请出刀信息  
                if (applies.isNotEmpty()) {  
                    Text(  
                        text = "${applies.joinToString("·") { it.playerName }} ${applies.size}人申请出刀",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.primary  
                    )  
                }  
  
                // 挂树信息  
                if (trees.isNotEmpty()) {  
                    Text(  
                        text = "${trees.joinToString("·") { it.playerName }} ${trees.size}人挂树中",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.error  
                    )  
                }  
  
                // 预约信息  
                if (subscribes.isNotEmpty()) {  
                    for (sub in subscribes) {  
                        Text(  
                            text = "${sub.playerName} 预约了下一周",  
                            style = MaterialTheme.typography.bodySmall,  
                            color = MaterialTheme.colorScheme.secondary  
                        )  
                    }  
                }  
            }  
  
            Spacer(modifier = Modifier.width(8.dp))  
  
            // 右侧：3个操作按钮（竖排）  
            Column(  
                verticalArrangement = Arrangement.spacedBy(4.dp),  
                horizontalAlignment = Alignment.CenterHorizontally  
            ) {  
                // 申请出刀  
                FilledTonalButton(  
                    onClick = onToggleApply,  
                    modifier = Modifier.width(80.dp),  
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),  
                    colors = if (hasApplied) ButtonDefaults.filledTonalButtonColors(  
                        containerColor = MaterialTheme.colorScheme.primary,  
                        contentColor = MaterialTheme.colorScheme.onPrimary  
                    ) else ButtonDefaults.filledTonalButtonColors()  
                ) {  
                    Text(  
                        text = if (hasApplied) "取消出刀" else "申请出刀",  
                        style = MaterialTheme.typography.labelSmall,  
                        maxLines = 1  
                    )  
                }  
  
                // 挂树  
                FilledTonalButton(  
                    onClick = onToggleTree,  
                    modifier = Modifier.width(80.dp),  
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),  
                    colors = if (hasTree) ButtonDefaults.filledTonalButtonColors(  
                        containerColor = MaterialTheme.colorScheme.error,  
                        contentColor = MaterialTheme.colorScheme.onError  
                    ) else ButtonDefaults.filledTonalButtonColors()  
                ) {  
                    Text(  
                        text = if (hasTree) "取消挂树" else "挂树",  
                        style = MaterialTheme.typography.labelSmall,  
                        maxLines = 1  
                    )  
                }  
  
                // 预约下一周目  
                FilledTonalButton(  
                    onClick = onToggleSubscribe,  
                    modifier = Modifier.width(80.dp),  
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),  
                    colors = if (hasSubscribed) ButtonDefaults.filledTonalButtonColors(  
                        containerColor = MaterialTheme.colorScheme.secondary,  
                        contentColor = MaterialTheme.colorScheme.onSecondary  
                    ) else ButtonDefaults.filledTonalButtonColors()  
                ) {  
                    Text(  
                        text = if (hasSubscribed) "取消预约" else "预约",  
                        style = MaterialTheme.typography.labelSmall,  
                        maxLines = 1  
                    )  
                }  
            }  
        }  
    }  
}  
  
// ==================== 底部操作按钮卡片 ====================  
  
@OptIn(ExperimentalLayoutApi::class)  
@Composable  
private fun ActionButtonsCard(  
    isMonitoring: Boolean,  
    isLoadingReport: Boolean,  
    onToggleMonitor: () -> Unit,  
    onCurrentReport: () -> Unit,  
    onMyReport: () -> Unit,  
    onTodayReport: () -> Unit,  
    onYesterdayReport: () -> Unit,  
    onSL: () -> Unit  
) {  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
    ) {  
        Column(  
            modifier = Modifier.padding(12.dp),  
            verticalArrangement = Arrangement.spacedBy(8.dp)  
        ) {  
            Text(  
                text = "操作",  
                style = MaterialTheme.typography.titleSmall,  
                fontWeight = FontWeight.Bold  
            )  
  
            FlowRow(  
                modifier = Modifier.fillMaxWidth(),  
                horizontalArrangement = Arrangement.spacedBy(8.dp),  
                verticalArrangement = Arrangement.spacedBy(8.dp)  
            ) {  
                // 出刀监控  
                Button(  
                    onClick = onToggleMonitor,  
                    colors = if (isMonitoring) ButtonDefaults.buttonColors(  
                        containerColor = MaterialTheme.colorScheme.error  
                    ) else ButtonDefaults.buttonColors()  
                ) {  
                    Icon(  
                        imageVector = if (isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,  
                        contentDescription = null,  
                        modifier = Modifier.size(18.dp)  
                    )  
                    Spacer(modifier = Modifier.width(4.dp))  
                    Text(if (isMonitoring) "停止监控" else "出刀监控")  
                }  
  
                // 当前战报  
                OutlinedButton(  
                    onClick = onCurrentReport,  
                    enabled = !isLoadingReport  
                ) {  
                    Text("当前战报")  
                }  
  
                // 我的战报  
                OutlinedButton(  
                    onClick = onMyReport,  
                    enabled = !isLoadingReport  
                ) {  
                    Text("我的战报")  
                }  
  
                // 今日出刀  
                OutlinedButton(  
                    onClick = onTodayReport,  
                    enabled = !isLoadingReport  
                ) {  
                    Text("今日出刀")  
                }  
  
                // 昨日出刀  
                OutlinedButton(  
                    onClick = onYesterdayReport,  
                    enabled = !isLoadingReport  
                ) {  
                    Text("昨日出刀")  
                }  
  
                // SL  
                OutlinedButton(onClick = onSL) {  
                    Text("SL")  
                }  
            }  
  
            // 加载指示器  
            if (isLoadingReport) {  
                Row(  
                    modifier = Modifier.fillMaxWidth(),  
                    horizontalArrangement = Arrangement.Center,  
                    verticalAlignment = Alignment.CenterVertically  
                ) {  
                    CircularProgressIndicator(  
                        modifier = Modifier.size(16.dp),  
                        strokeWidth = 2.dp  
                    )  
                    Spacer(modifier = Modifier.width(8.dp))  
                    Text("正在获取战报...", style = MaterialTheme.typography.bodySmall)  
                }  
            }  
        }  
    }  
}  
  
// ==================== 账号选择弹窗 ====================  
  
@Composable  
private fun AccountPickerDialog(  
    accounts: List<Account>,  
    onSelect: (Account) -> Unit,  
    onDismiss: () -> Unit  
) {  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text("选择监控账号") },  
        text = {  
            if (accounts.isEmpty()) {  
                Text("没有可用的账号。请先在「我的账号」中添加账号。")  
            } else {  
                Column(  
                    modifier = Modifier  
                        .fillMaxWidth()  
                        .heightIn(max = 300.dp)  
                        .verticalScroll(rememberScrollState()),  
                    verticalArrangement = Arrangement.spacedBy(4.dp)  
                ) {  
                    Text(  
                        text = "将使用以下「我的账号」进行会战监控：",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                    Spacer(modifier = Modifier.height(4.dp))  
                    accounts.forEach { account ->  
                        OutlinedCard(  
                            onClick = { onSelect(account) },  
                            modifier = Modifier.fillMaxWidth()  
                        ) {  
                            Row(  
                                modifier = Modifier  
                                    .fillMaxWidth()  
                                    .padding(12.dp),  
                                verticalAlignment = Alignment.CenterVertically  
                            ) {  
                                Icon(  
                                    Icons.Default.Person,  
                                    contentDescription = null,  
                                    modifier = Modifier.size(20.dp),  
                                    tint = MaterialTheme.colorScheme.primary  
                                )  
                                Spacer(modifier = Modifier.width(8.dp))  
                                Column {  
                                    Text(  
                                        text = account.account,  
                                        style = MaterialTheme.typography.bodyMedium,  
                                        fontWeight = FontWeight.Medium  
                                    )  
                                    Text(  
                                        text = "平台: ${if (account.platform == 2) "B服" else "官服"}",  
                                        style = MaterialTheme.typography.bodySmall,  
                                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                                    )  
                                }  
                            }  
                        }  
                    }  
                }  
            }  
        },  
        confirmButton = {},  
        dismissButton = {  
            TextButton(onClick = onDismiss) {  
                Text("取消")  
            }  
        }  
    )  
}  
  
// ==================== 浮窗辅助函数 ====================  
  
private fun toggleFloatingWindow(context: Context, viewModel: ClanBattleViewModel) {  
    if (!Settings.canDrawOverlays(context)) {  
        // 跳转到悬浮窗权限设置  
        val intent = Intent(  
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,  
            android.net.Uri.parse("package:${context.packageName}")  
        )  
        context.startActivity(intent)  
        return  
    }  
  
    if (ClanBattleFloatingService.isRunning) {  
        // 关闭浮窗  
        context.stopService(Intent(context, ClanBattleFloatingService::class.java))  
    } else {  
        // 开启浮窗，传递当前状态文本  
        val intent = Intent(context, ClanBattleFloatingService::class.java).apply {  
            putExtra("floating_text", viewModel.getFloatingText())  
            putExtra("room_id", viewModel.uiState.value.roomId)  
        }  
        context.startService(intent)  
    }  
}