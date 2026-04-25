package com.pcrjjc.app.ui.manualbattle  
  
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
import androidx.compose.ui.text.font.FontWeight  
import androidx.compose.ui.text.input.KeyboardType  
import androidx.compose.foundation.text.KeyboardOptions  
import androidx.compose.ui.text.style.TextOverflow  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
import com.pcrjjc.app.data.local.entity.ManualBattleState  
import com.pcrjjc.app.data.local.entity.ManualBossState  
import com.pcrjjc.app.data.local.entity.ChallengingMember  
import com.pcrjjc.app.data.local.entity.ManualSubscribeRecord  
import com.pcrjjc.app.domain.ManualBattleEngine  
import com.pcrjjc.app.data.local.entity.BossConfig
  
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)  
@Composable  
fun ManualBattleScreen(  
    viewModel: ManualBattleViewModel = hiltViewModel(),  
    onNavigateBack: () -> Unit = {}  
) {  
    val uiState by viewModel.uiState.collectAsState()  
    val snackbarHostState = remember { SnackbarHostState() }  
    val battleState = uiState.battleState  
  
    // ---- 对话框状态 ----  
    var showCreateGuildDialog by remember { mutableStateOf(false) }  
    var showChallengeDialog by remember { mutableStateOf(false) }  
    var challengeDialogBoss by remember { mutableIntStateOf(1) }  
    var showApplyDialog by remember { mutableStateOf(false) }  
    var applyDialogBoss by remember { mutableIntStateOf(1) }  
    var showTreeDialog by remember { mutableStateOf(false) }  
    var treeDialogBoss by remember { mutableIntStateOf(0) }  
    var showSubscribeDialog by remember { mutableStateOf(false) }  
    var showCancelSubscribeDialog by remember { mutableStateOf(false) }  
    var showSLDialog by remember { mutableStateOf(false) }  
    var showReportHurtDialog by remember { mutableStateOf(false) }  
    var showCombineDialog by remember { mutableStateOf(false) }  
    var showModifyDialog by remember { mutableStateOf(false) }  
    var showResetDialog by remember { mutableStateOf(false) }  
    var showResultDialog by remember { mutableStateOf(false) }  
    var showQueryTreeDialog by remember { mutableStateOf(false) }  
    var showSubscribeTableDialog by remember { mutableStateOf(false) }  
    var showRecordDialog by remember { mutableStateOf(false) }  
    var showScoreDialog by remember { mutableStateOf(false) }  
    var showFetchBossDialog by remember { mutableStateOf(false) }  
    var showMemberListDialog by remember { mutableStateOf(false) }  
  
    // 错误/结果提示  
    LaunchedEffect(uiState.error) {  
        uiState.error?.let {  
            snackbarHostState.showSnackbar(it)  
            viewModel.clearError()  
        }  
    }  
    LaunchedEffect(uiState.resultMessage) {  
        uiState.resultMessage?.let {  
            showResultDialog = true  
        }  
    }  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = {  
                    Column {  
                        Text("手动报刀", maxLines = 1, overflow = TextOverflow.Ellipsis)  
                        if (battleState.isCreated) {  
                            Text(  
                                text = "${battleState.gameServer}服 | ${battleState.bossCycle}周目 | ${battleState.currentLevelName}阶段",  
                                style = MaterialTheme.typography.bodySmall,  
                                color = MaterialTheme.colorScheme.onSurfaceVariant  
                            )  
                        }  
                    }  
                },  
                navigationIcon = {  
                    IconButton(onClick = onNavigateBack) {  
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")  
                    }  
                },  
                actions = {  
                    IconButton(onClick = { viewModel.bossStatusSummary(); showResultDialog = true }) {  
                        Icon(Icons.Default.Info, contentDescription = "状态总览")  
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
            // ==================== 公会设置 ====================  
            if (!battleState.isCreated) {  
                GuildSetupCard(  
                    onCreateGuild = { showCreateGuildDialog = true },  
                    onJoinGuild = { viewModel.joinGuild() },  
                    isMember = false  
                )  
            } else if (!battleState.isMember(uiState.playerQq)) {  
                GuildSetupCard(  
                    onCreateGuild = {},  
                    onJoinGuild = { viewModel.joinGuild() },  
                    isMember = false,  
                    guildExists = true  
                )  
            }  
  
            // ==================== 5个 Boss 卡片 ====================  
            if (battleState.isCreated) {  
                val currentLevel = BossConfig.levelByCycle(battleState.bossCycle, battleState.gameServer)  
				val nextLevelVal = BossConfig.levelByCycle(battleState.bossCycle + 1, battleState.gameServer)  
				val canNext = currentLevel == nextLevelVal
				
				battleState.bosses.forEachIndexed { index, boss ->  
					val effectiveNextHp = if (boss.nextCycleHp == -1L) {  
						if (boss.isNext) boss.maxHp else 0L  
					} else {  
						boss.nextCycleHp  
					}  
					val displayHp: Long  
					val displayMaxHp: Long  
					val displayCycle: Int  
					if (boss.currentHp <= 0L && effectiveNextHp > 0L && canNext) {  
						val nxtLevel = BossConfig.levelByCycle(battleState.bossCycle + 1, battleState.gameServer)  
						displayMaxHp = BossConfig.getFullHp(battleState.gameServer, nxtLevel, index)  
						displayHp = effectiveNextHp  
						displayCycle = battleState.bossCycle + 1  
					} else if (boss.currentHp <= 0L) {  
						displayHp = 0  
						displayMaxHp = boss.maxHp  
						displayCycle = battleState.bossCycle + 1  
					} else {  
						displayHp = boss.currentHp  
						displayMaxHp = boss.maxHp  
						displayCycle = battleState.bossCycle  
					}   
                    ManualBossCard(  
						boss = boss,  
						bossOrder = index + 1,  
						displayHp = displayHp,  
						displayMaxHp = displayMaxHp,  
						displayCycle = displayCycle,  
						challengers = battleState.getChallengersForBoss(index + 1),  
						trees = battleState.getTreesForBoss(index + 1),  
						subscribes = battleState.getSubscribesForBoss(index + 1),  
                        hasApplied = battleState.hasApplied(uiState.playerQq) &&  
                                battleState.getAppliedBossNum(uiState.playerQq) == index + 1,  
                        isOnTree = battleState.isOnTree(uiState.playerQq),  
                        hasSubscribed = battleState.hasSubscribed(uiState.playerQq, index + 1),  
                        onApply = {  
                            applyDialogBoss = index + 1  
                            showApplyDialog = true  
                        },  
                        onChallenge = {  
                            challengeDialogBoss = index + 1  
                            showChallengeDialog = true  
                        },  
                        onTree = {  
                            treeDialogBoss = index + 1  
                            showTreeDialog = true  
                        },  
                        onSubscribe = {  
                            viewModel.subscribe(index + 1)  
                        },  
                        onCancelSubscribe = {  
                            viewModel.cancelSubscribe(index + 1)  
                        }  
                    )  
                }  
  
                // ==================== 操作按钮 ====================  
                ManualActionButtonsCard(  
                    isMember = battleState.isMember(uiState.playerQq),  
                    hasApplied = battleState.hasApplied(uiState.playerQq),  
                    isOnTree = battleState.isOnTree(uiState.playerQq),  
                    isFetchingBoss = uiState.isFetchingBossData,  
                    onUndo = { viewModel.undo() },  
                    onCancelApply = { viewModel.cancelApply() },  
                    onTakeOffTree = { viewModel.takeOffTree() },  
                    onQueryTree = { showQueryTreeDialog = true },  
                    onSubscribeTable = { viewModel.subscribeTable(); showSubscribeTableDialog = true },  
                    onRecordSL = { showSLDialog = true },  
                    onCheckSL = { viewModel.checkSL() },  
                    onReportHurt = { showReportHurtDialog = true },  
                    onCombineBlade = { showCombineDialog = true },  
                    onChallengeRecord = { viewModel.challengeRecord(); showRecordDialog = true },  
                    onScoreTable = { viewModel.scoreTable(); showScoreDialog = true },  
                    onMyDetail = { viewModel.memberTodayDetail() },  
                    onModify = { showModifyDialog = true },  
                    onReset = { showResetDialog = true },  
                    onFetchBoss = { showFetchBossDialog = true },  
                    onMemberList = { showMemberListDialog = true },  
                    onNotFight = { viewModel.notFight() }  
                )  
            }  
  
            Spacer(modifier = Modifier.height(16.dp))  
        }  
    }  
  
    // ==================== 所有对话框 ====================  
  
    // 创建公会  
    if (showCreateGuildDialog) {  
        CreateGuildDialog(  
            onDismiss = { showCreateGuildDialog = false },  
            onCreate = { server ->  
                viewModel.createGuild(server)  
                showCreateGuildDialog = false  
            }  
        )  
    }  
  
    // 申请出刀  
    if (showApplyDialog) {  
        ApplyDialog(  
            bossNum = applyDialogBoss,  
            members = battleState.members,  
            onDismiss = { showApplyDialog = false },  
            onApply = { isContinue, behalfQq, behalfName ->  
                viewModel.applyForChallenge(applyDialogBoss, isContinue, behalfQq, behalfName)  
                showApplyDialog = false  
            }  
        )  
    }  
  
    // 报刀 (修复: bossHp -> bossCurrentHp, 移除 challengers)  
    if (showChallengeDialog) {  
        ChallengeDialog(  
            bossNum = challengeDialogBoss,  
            bossCurrentHp = battleState.bosses.getOrNull(challengeDialogBoss - 1)?.currentHp ?: 0,  
            members = battleState.members,  
            onDismiss = { showChallengeDialog = false },  
            onChallenge = { defeat, damage, isContinue, behalfQq, behalfName, previousDay ->  
                viewModel.challenge(defeat, damage, challengeDialogBoss, isContinue, behalfQq, behalfName, previousDay)  
                showChallengeDialog = false  
            }  
        )  
    }  
  
    // 挂树 (修复: onTree -> onPutOnTree, 添加 onTakeOffTree 和 isOnTree)  
    if (showTreeDialog) {  
        TreeDialog(  
            bossNum = treeDialogBoss,  
            onDismiss = { showTreeDialog = false },  
            onPutOnTree = { message ->  
                viewModel.putOnTree(treeDialogBoss, message)  
                showTreeDialog = false  
            },  
            onTakeOffTree = {  
                viewModel.takeOffTree()  
                showTreeDialog = false  
            },  
            isOnTree = battleState.isOnTree(uiState.playerQq)  
        )  
    }  
  
    // 查树 (修复: 定义已改为匹配此调用)  
    if (showQueryTreeDialog) {  
        QueryTreeDialog(  
            battleState = battleState,  
            onDismiss = { showQueryTreeDialog = false },  
            onQuery = { bossNum ->  
                viewModel.queryTree(bossNum)  
            }  
        )  
    }  
  
    // 预约boss  
    if (showSubscribeDialog) {  
        SubscribeDialog(  
            onDismiss = { showSubscribeDialog = false },  
            onSubscribe = { bossNum, note ->  
                viewModel.subscribe(bossNum, note)  
                showSubscribeDialog = false  
            }  
        )  
    }  
  
    // SL (修复: 定义已改为匹配此调用)  
    if (showSLDialog) {  
        SLDialog(  
            hasSL = battleState.hasSLToday(  
                uiState.playerQq,  
                ManualBattleEngine.getPcrDate(battleState.gameServer)  
            ),  
            onDismiss = { showSLDialog = false },  
            onRecordSL = {  
                viewModel.recordSL()  
                showSLDialog = false  
            },  
            onCancelSL = {  
                viewModel.cancelSL()  
                showSLDialog = false  
            }  
        )  
    }  
  
    // 报伤害  
    if (showReportHurtDialog) {  
        ReportHurtDialog(  
            onDismiss = { showReportHurtDialog = false },  
            onReport = { seconds, damage ->  
                viewModel.reportHurt(seconds, damage)  
                showReportHurtDialog = false  
            },  
            onCancel = {  
                viewModel.cancelReportHurt()  
                showReportHurtDialog = false  
            }  
        )  
    }  
  
    // 合刀计算  
    if (showCombineDialog) {  
        CombineBladeDialog(  
            onDismiss = { showCombineDialog = false },  
            onCalculate = { d1, d2, hp ->  
                viewModel.combineBlade(d1, d2, hp)  
                showCombineDialog = false  
            }  
        )  
    }  
  
    // 修改boss状态  
    if (showModifyDialog) {  
        ModifyBossDialog(  
            currentCycle = battleState.bossCycle,  
            bosses = battleState.bosses,  
            onDismiss = { showModifyDialog = false },  
            onModify = { cycle, bossData ->  
                viewModel.modify(cycle, bossData)  
                showModifyDialog = false  
            }  
        )  
    }  
  
    // 重置进度  
    if (showResetDialog) {  
        AlertDialog(  
            onDismissRequest = { showResetDialog = false },  
            title = { Text("重置进度") },  
            text = { Text("确定要重置所有进度吗？所有出刀记录、预约、申请都将被清空。此操作不可撤销。") },  
            confirmButton = {  
                TextButton(onClick = {  
                    viewModel.resetProgress()  
                    showResetDialog = false  
                }) { Text("确定重置", color = MaterialTheme.colorScheme.error) }  
            },  
            dismissButton = {  
                TextButton(onClick = { showResetDialog = false }) { Text("取消") }  
            }  
        )  
    }  
  
    // 自动获取boss数据  
    if (showFetchBossDialog) {  
        AlertDialog(  
            onDismissRequest = { showFetchBossDialog = false },  
            title = { Text("自动获取boss数据") },  
            text = { Text("将从网络获取当期cn/jp服boss血量数据（除台服）。确定？") },  
            confirmButton = {  
                TextButton(onClick = {  
                    viewModel.fetchBossData()  
                    showFetchBossDialog = false  
                }) { Text("获取") }  
            },  
            dismissButton = {  
                TextButton(onClick = { showFetchBossDialog = false }) { Text("取消") }  
            }  
        )  
    }  
  
    // 成员列表  
    if (showMemberListDialog) {  
        MemberListDialog(  
            members = battleState.members,  
            onDismiss = { showMemberListDialog = false },  
            onRemove = { qq -> viewModel.removeMember(qq) },  
            onViewDetail = { qq -> viewModel.memberTodayDetail(qq) }  
        )  
    }  
  
    // 结果消息弹窗  
    if (showResultDialog && uiState.resultMessage != null) {  
        AlertDialog(  
            onDismissRequest = {  
                showResultDialog = false  
                viewModel.clearResultMessage()  
            },  
            title = { Text("操作结果") },  
            text = {  
                Column(  
                    modifier = Modifier  
                        .fillMaxWidth()  
                        .heightIn(max = 400.dp)  
                        .verticalScroll(rememberScrollState())  
                ) {  
                    Text(  
                        text = uiState.resultMessage ?: "",  
                        style = MaterialTheme.typography.bodyMedium  
                    )  
                }  
            },  
            confirmButton = {  
                TextButton(onClick = {  
                    showResultDialog = false  
                    viewModel.clearResultMessage()  
                }) { Text("关闭") }  
            }  
        )  
    }  
  
    // 预约表弹窗  
    if (showSubscribeTableDialog && uiState.resultMessage != null) {  
        AlertDialog(  
            onDismissRequest = {  
                showSubscribeTableDialog = false  
                viewModel.clearResultMessage()  
            },  
            title = { Text("预约表") },  
            text = {  
                Column(  
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)  
                        .verticalScroll(rememberScrollState())  
                ) { Text(uiState.resultMessage ?: "") }  
            },  
            confirmButton = {  
                TextButton(onClick = {  
                    showSubscribeTableDialog = false  
                    viewModel.clearResultMessage()  
                }) { Text("关闭") }  
            }  
        )  
    }  
  
    // 出刀记录弹窗  
    if (showRecordDialog && uiState.resultMessage != null) {  
        AlertDialog(  
            onDismissRequest = {  
                showRecordDialog = false  
                viewModel.clearResultMessage()  
            },  
            title = { Text("出刀记录") },  
            text = {  
                Column(  
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)  
                        .verticalScroll(rememberScrollState())  
                ) { Text(uiState.resultMessage ?: "") }  
            },  
            confirmButton = {  
                TextButton(onClick = {  
                    showRecordDialog = false  
                    viewModel.clearResultMessage()  
                }) { Text("关闭") }  
            }  
        )  
    }  
  
    // 业绩表弹窗  
    if (showScoreDialog && uiState.resultMessage != null) {  
        AlertDialog(  
            onDismissRequest = {  
                showScoreDialog = false  
                viewModel.clearResultMessage()  
            },  
            title = { Text("业绩表") },  
            text = {  
                Column(  
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)  
                        .verticalScroll(rememberScrollState())  
                ) { Text(uiState.resultMessage ?: "") }  
            },  
            confirmButton = {  
                TextButton(onClick = {  
                    showScoreDialog = false  
                    viewModel.clearResultMessage()  
                }) { Text("关闭") }  
            }  
        )  
    }  
}  
  
// ==================== 操作按钮卡片 (新增) ====================  
  
@OptIn(ExperimentalLayoutApi::class)  
@Composable  
private fun ManualActionButtonsCard(  
    isMember: Boolean,  
    hasApplied: Boolean,  
    isOnTree: Boolean,  
    isFetchingBoss: Boolean,  
    onUndo: () -> Unit,  
    onCancelApply: () -> Unit,  
    onTakeOffTree: () -> Unit,  
    onQueryTree: () -> Unit,  
    onSubscribeTable: () -> Unit,  
    onRecordSL: () -> Unit,  
    onCheckSL: () -> Unit,  
    onReportHurt: () -> Unit,  
    onCombineBlade: () -> Unit,  
    onChallengeRecord: () -> Unit,  
    onScoreTable: () -> Unit,  
    onMyDetail: () -> Unit,  
    onModify: () -> Unit,  
    onReset: () -> Unit,  
    onFetchBoss: () -> Unit,  
    onMemberList: () -> Unit,  
    onNotFight: () -> Unit  
) {  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),  
        colors = CardDefaults.cardColors(  
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)  
        )  
    ) {  
        Column(  
            modifier = Modifier.padding(12.dp),  
            verticalArrangement = Arrangement.spacedBy(8.dp)  
        ) {  
            Text("操作", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)  
            FlowRow(  
                horizontalArrangement = Arrangement.spacedBy(6.dp),  
                verticalArrangement = Arrangement.spacedBy(6.dp)  
            ) {  
                ActionButton("撤销", onClick = onUndo)  
                if (hasApplied) {  
                    ActionButton("取消申请", onClick = onCancelApply)  
                }  
                if (isOnTree) {  
                    ActionButton("下树", onClick = onTakeOffTree)  
                }  
                ActionButton("查树", onClick = onQueryTree)  
                ActionButton("预约表", onClick = onSubscribeTable)  
                ActionButton("SL", onClick = onRecordSL)  
                ActionButton("查SL", onClick = onCheckSL)  
                ActionButton("报伤害", onClick = onReportHurt)  
                ActionButton("合刀", onClick = onCombineBlade)  
                ActionButton("出刀记录", onClick = onChallengeRecord)  
                ActionButton("业绩表", onClick = onScoreTable)  
                ActionButton("我的详情", onClick = onMyDetail)  
                ActionButton("修改boss", onClick = onModify)  
                ActionButton("重置", onClick = onReset)  
                ActionButton("获取boss", onClick = onFetchBoss, enabled = !isFetchingBoss)  
                ActionButton("成员列表", onClick = onMemberList)  
                ActionButton("不出刀", onClick = onNotFight)  
            }  
        }  
    }  
}  
  
@Composable  
private fun ActionButton(  
    text: String,  
    onClick: () -> Unit,  
    enabled: Boolean = true  
) {  
    FilledTonalButton(  
        onClick = onClick,  
        enabled = enabled,  
        modifier = Modifier.height(32.dp),  
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)  
    ) {  
        Text(text, style = MaterialTheme.typography.labelSmall)  
    }  
}  
  
// ==================== 公会设置卡片 ====================  
  
@Composable  
private fun GuildSetupCard(  
    onCreateGuild: () -> Unit,  
    onJoinGuild: () -> Unit,  
    isMember: Boolean,  
    guildExists: Boolean = false  
) {  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),  
        colors = CardDefaults.cardColors(  
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)  
        )  
    ) {  
        Column(  
            modifier = Modifier.padding(16.dp),  
            verticalArrangement = Arrangement.spacedBy(8.dp),  
            horizontalAlignment = Alignment.CenterHorizontally  
        ) {  
            if (!guildExists) {  
                Text("尚未创建公会", style = MaterialTheme.typography.titleMedium)  
                Text("请先创建公会或等待房主创建", style = MaterialTheme.typography.bodySmall)  
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {  
                    Button(onClick = onCreateGuild) { Text("创建公会") }  
                }  
            } else {  
                Text("公会已创建，您尚未加入", style = MaterialTheme.typography.titleMedium)  
                Button(onClick = onJoinGuild) { Text("加入公会") }  
            }  
        }  
    }  
}  
  
// ==================== Boss 卡片 ====================  
  
@Composable  
private fun ManualBossCard(  
    boss: ManualBossState,  
    bossOrder: Int, 
    displayHp: Long,           // ← 新增  
    displayMaxHp: Long,        // ← 新增  
    displayCycle: Int,         // ← 新增    
    challengers: List<ChallengingMember>,  
    trees: List<ChallengingMember>,  
    subscribes: List<ManualSubscribeRecord>,  
    hasApplied: Boolean,  
    isOnTree: Boolean,  
    hasSubscribed: Boolean,  
    onApply: () -> Unit,  
    onChallenge: () -> Unit,  
    onTree: () -> Unit,  
    onSubscribe: () -> Unit,  
    onCancelSubscribe: () -> Unit  
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
            // 左侧：Boss 信息  
            Column(  
                modifier = Modifier.weight(1f),  
                verticalArrangement = Arrangement.spacedBy(4.dp)  
            ) {  
                Text(  
					text = "${displayCycle}周目${bossOrder}王",  
					style = MaterialTheme.typography.titleSmall,  
					fontWeight = FontWeight.Bold  
				)  
  
                val displayPercent = if (displayMaxHp > 0) displayHp.toFloat() / displayMaxHp.toFloat() else 0f  
				LinearProgressIndicator(  
					progress = { displayPercent.coerceIn(0f, 1f) },  
					modifier = Modifier  
						.fillMaxWidth()  
						.height(10.dp),  
					trackColor = MaterialTheme.colorScheme.surfaceVariant,  
				)  
  
                Text(  
					text = "HP: ${ManualBattleEngine.formatDamage(displayHp)}/${ManualBattleEngine.formatDamage(displayMaxHp)} (${(displayPercent * 100).toInt()}%)",  
					style = MaterialTheme.typography.bodySmall  
				)  
  
                // 挑战中的成员  
                if (challengers.isNotEmpty()) {  
                    Text(  
                        text = "挑战中：${challengers.joinToString("·") { c ->  
                            buildString {  
                                append(c.playerName)  
                                if (c.isContinue) append("(补)")  
                                if (c.isOnTree) append("(树)")  
                                if (c.reportedDamage > 0) append("@${c.seconds}s,${c.reportedDamage}w")  
                            }  
                        }}",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.primary  
                    )  
                }  
  
                // 挂树  
                if (trees.isNotEmpty()) {  
                    Text(  
                        text = "挂树：${trees.joinToString("·") { it.playerName }}",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.error  
                    )  
                }  
  
                // 预约  
                if (subscribes.isNotEmpty()) {  
                    Text(  
                        text = "预约：${subscribes.joinToString("·") { s ->  
                            "${s.playerName}${if (s.note.isNotBlank()) ":${s.note}" else ""}"  
                        }}",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.tertiary  
                    )  
                }  
            }  
  
            // 右侧：操作按钮  
            Column(  
                horizontalAlignment = Alignment.End,  
                verticalArrangement = Arrangement.spacedBy(4.dp)  
            ) {  
                // 申请出刀 / 报刀  
                if (!hasApplied) {  
                    FilledTonalButton(  
                        onClick = onApply,  
                        modifier = Modifier.height(32.dp),  
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)  
                    ) { Text("申请", style = MaterialTheme.typography.labelSmall) }  
                } else {  
                    Button(  
                        onClick = onChallenge,  
                        modifier = Modifier.height(32.dp),  
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)  
                    ) { Text("报刀", style = MaterialTheme.typography.labelSmall) }  
                }  
  
                // 挂树  
                if (hasApplied) {  
                    OutlinedButton(  
                        onClick = onTree,  
                        modifier = Modifier.height(32.dp),  
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)  
                    ) {  
                        Text(  
                            if (isOnTree) "下树" else "挂树",  
                            style = MaterialTheme.typography.labelSmall  
                        )  
                    }  
                }  
  
                // 预约  
                if (!hasSubscribed) {  
                    OutlinedButton(  
                        onClick = onSubscribe,  
                        modifier = Modifier.height(32.dp),  
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)  
                    ) { Text("预约", style = MaterialTheme.typography.labelSmall) }  
                } else {  
                    OutlinedButton(  
                        onClick = onCancelSubscribe,  
                        modifier = Modifier.height(32.dp),  
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),  
                        colors = ButtonDefaults.outlinedButtonColors(  
                            contentColor = MaterialTheme.colorScheme.error  
                        )  
                    ) { Text("取消预约", style = MaterialTheme.typography.labelSmall) }  
                }  
            }  
        }  
    }  
}  
  
// ==================== 创建公会对话框 ====================  
  
@Composable  
private fun CreateGuildDialog(  
    onDismiss: () -> Unit,  
    onCreate: (String) -> Unit  
) {  
    var selectedServer by remember { mutableStateOf("cn") }  
    val servers = listOf("cn" to "国服", "jp" to "日服", "tw" to "台服")  
  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text("创建公会") },  
        text = {  
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {  
                Text("选择游戏服务器：")  
                servers.forEach { (code, name) ->  
                    Row(  
                        verticalAlignment = Alignment.CenterVertically,  
                        modifier = Modifier.fillMaxWidth()  
                    ) {  
                        RadioButton(  
                            selected = selectedServer == code,  
                            onClick = { selectedServer = code }  
                        )  
                        Text(text = "$name ($code)")  
                    }  
                }  
            }  
        },  
        confirmButton = {  
            TextButton(onClick = { onCreate(selectedServer) }) { Text("创建") }  
        },  
        dismissButton = {  
            TextButton(onClick = onDismiss) { Text("取消") }  
        }  
    )  
}  
  
// ==================== 申请出刀对话框 ====================  
  
@Composable  
private fun ApplyDialog(  
    bossNum: Int,  
    members: List<com.pcrjjc.app.data.local.entity.GuildMember>,  
    onDismiss: () -> Unit,  
    onApply: (isContinue: Boolean, behalfQq: String?, behalfName: String?) -> Unit  
) {  
    var isContinue by remember { mutableStateOf(false) }  
    var useBehalfMode by remember { mutableStateOf(false) }  
    var selectedMemberQq by remember { mutableStateOf<String?>(null) }  
    var showMemberDropdown by remember { mutableStateOf(false) }  
  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text("申请出刀 - ${bossNum}王") },  
        text = {  
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {  
                Row(  
                    verticalAlignment = Alignment.CenterVertically,  
                    horizontalArrangement = Arrangement.SpaceBetween,  
                    modifier = Modifier.fillMaxWidth()  
                ) {  
                    Text("补偿刀")  
                    Switch(checked = isContinue, onCheckedChange = { isContinue = it })  
                }  
                Row(  
                    verticalAlignment = Alignment.CenterVertically,  
                    horizontalArrangement = Arrangement.SpaceBetween,  
                    modifier = Modifier.fillMaxWidth()  
                ) {  
                    Text("代刀")  
                    Switch(checked = useBehalfMode, onCheckedChange = { useBehalfMode = it })  
                }  
                if (useBehalfMode) {  
                    Box {  
                        OutlinedButton(  
                            onClick = { showMemberDropdown = true },  
                            modifier = Modifier.fillMaxWidth()  
                        ) {  
                            Text(  
                                members.firstOrNull { it.playerQq == selectedMemberQq }?.playerName  
                                    ?: "选择成员"  
                            )  
                        }  
                        DropdownMenu(  
                            expanded = showMemberDropdown,  
                            onDismissRequest = { showMemberDropdown = false }  
                        ) {  
                            members.forEach { m ->  
                                DropdownMenuItem(  
                                    text = { Text(m.playerName) },  
                                    onClick = {  
                                        selectedMemberQq = m.playerQq  
                                        showMemberDropdown = false  
                                    }  
                                )  
                            }  
                        }  
                    }  
                }  
            }  
        },  
        confirmButton = {  
            TextButton(onClick = {  
                val behalf = if (useBehalfMode) selectedMemberQq else null  
                val behalfName = if (useBehalfMode) members.firstOrNull { it.playerQq == selectedMemberQq }?.playerName else null  
                onApply(isContinue, behalf, behalfName)  
            }) { Text("确定") }  
        },  
        dismissButton = {  
            TextButton(onClick = onDismiss) { Text("取消") }  
        }  
    )  
}  
  
// ==================== 报刀对话框 ====================  
  
@Composable  
private fun ChallengeDialog(  
    bossNum: Int,  
    bossCurrentHp: Long,  
    members: List<com.pcrjjc.app.data.local.entity.GuildMember>,  
    onDismiss: () -> Unit,  
    onChallenge: (defeat: Boolean, damage: Long, isContinue: Boolean,  
                  behalfQq: String?, behalfName: String?, previousDay: Boolean) -> Unit  
) {  
    var defeat by remember { mutableStateOf(false) }  
    var damageText by remember { mutableStateOf("") }  
    var damageUnit by remember { mutableStateOf("万") }  
    var isContinue by remember { mutableStateOf(false) }  
    var previousDay by remember { mutableStateOf(false) }  
    var useBehalfMode by remember { mutableStateOf(false) }  
    var selectedMemberQq by remember { mutableStateOf<String?>(null) }  
    var showMemberDropdown by remember { mutableStateOf(false) }  
    var showUnitDropdown by remember { mutableStateOf(false) }  
  
    val unitMultiplier = when (damageUnit) {  
        "万" -> 10000L  
        "千" -> 1000L  
        else -> 1L  
    }  
  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text("报刀 - ${bossNum}王") },  
        text = {  
            Column(  
                verticalArrangement = Arrangement.spacedBy(10.dp),  
                modifier = Modifier.verticalScroll(rememberScrollState())  
            ) {  
                Text(  
                    "当前血量：${ManualBattleEngine.formatDamage(bossCurrentHp)}",  
                    style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
  
                // 是否击败boss  
                Row(  
                    verticalAlignment = Alignment.CenterVertically,  
                    horizontalArrangement = Arrangement.SpaceBetween,  
                    modifier = Modifier.fillMaxWidth()  
                ) {  
                    Text("击败Boss（尾刀）")  
                    Switch(checked = defeat, onCheckedChange = { defeat = it })  
                }  
  
                // 伤害值（尾刀时不需要输入）  
                if (!defeat) {  
                    Row(  
                        verticalAlignment = Alignment.CenterVertically,  
                        horizontalArrangement = Arrangement.spacedBy(8.dp)  
                    ) {  
                        OutlinedTextField(  
                            value = damageText,  
                            onValueChange = { damageText = it.filter { c -> c.isDigit() } },  
                            label = { Text("伤害值") },  
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  
                            modifier = Modifier.weight(1f),  
                            singleLine = true  
                        )  
                        Box {  
                            OutlinedButton(onClick = { showUnitDropdown = true }) {  
                                Text(damageUnit)  
                            }  
                            DropdownMenu(  
                                expanded = showUnitDropdown,  
                                onDismissRequest = { showUnitDropdown = false }  
                            ) {  
                                listOf("万", "千", "个").forEach { unit ->  
                                    DropdownMenuItem(  
                                        text = { Text(unit) },  
                                        onClick = {  
                                            damageUnit = unit  
                                            showUnitDropdown = false  
                                        }  
                                    )  
                                }  
                            }  
                        }  
                    }  
                }  
  
                // 补偿刀  
                Row(  
                    verticalAlignment = Alignment.CenterVertically,  
                    horizontalArrangement = Arrangement.SpaceBetween,  
                    modifier = Modifier.fillMaxWidth()  
                ) {  
                    Text("补偿刀")  
                    Switch(checked = isContinue, onCheckedChange = { isContinue = it })  
                }  
  
                // 昨日  
                Row(  
                    verticalAlignment = Alignment.CenterVertically,  
                    horizontalArrangement = Arrangement.SpaceBetween,  
                    modifier = Modifier.fillMaxWidth()  
                ) {  
                    Text("记录为昨日")  
                    Switch(checked = previousDay, onCheckedChange = { previousDay = it })  
                }  
  
                // 代刀  
                Row(  
                    verticalAlignment = Alignment.CenterVertically,  
                    horizontalArrangement = Arrangement.SpaceBetween,  
                    modifier = Modifier.fillMaxWidth()  
                ) {  
                    Text("代刀")  
                    Switch(checked = useBehalfMode, onCheckedChange = { useBehalfMode = it })  
                }  
                if (useBehalfMode) {  
                    Box {  
                        OutlinedButton(  
                            onClick = { showMemberDropdown = true },  
                            modifier = Modifier.fillMaxWidth()  
                        ) {  
                            Text(  
                                members.firstOrNull { it.playerQq == selectedMemberQq }?.playerName  
                                    ?: "选择成员"  
                            )  
                        }  
                        DropdownMenu(  
                            expanded = showMemberDropdown,  
                            onDismissRequest = { showMemberDropdown = false }  
                        ) {  
                            members.forEach { m ->  
                                DropdownMenuItem(  
                                    text = { Text(m.playerName) },  
                                    onClick = {  
                                        selectedMemberQq = m.playerQq  
                                        showMemberDropdown = false  
                                    }  
                                )  
                            }  
                        }  
                    }  
                }  
            }  
        },  
        confirmButton = {  
            TextButton(onClick = {  
                val damage = if (defeat) 0L else (damageText.toLongOrNull() ?: 0L) * unitMultiplier  
                val behalf = if (useBehalfMode) selectedMemberQq else null  
                val behalfName = if (useBehalfMode) members.firstOrNull { it.playerQq == selectedMemberQq }?.playerName else null  
                onChallenge(defeat, damage, isContinue, behalf, behalfName, previousDay)  
            }) { Text("确定报刀") }  
        },  
        dismissButton = {  
            TextButton(onClick = onDismiss) { Text("取消") }  
        }  
    )  
}  
  
// ==================== 挂树对话框 ====================  
  
@Composable  
private fun TreeDialog(  
    bossNum: Int,  
    onDismiss: () -> Unit,  
    onPutOnTree: (message: String?) -> Unit,  
    onTakeOffTree: () -> Unit,  
    isOnTree: Boolean  
) {  
    var treeMessage by remember { mutableStateOf("") }  
  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text(if (isOnTree) "下树" else "挂树 - ${if (bossNum > 0) "${bossNum}王" else "当前boss"}") },  
        text = {  
            if (isOnTree) {  
                Text("确定要下树吗？")  
            } else {  
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {  
                    Text("挂树后，boss被击败时会收到提醒")  
                    OutlinedTextField(  
                        value = treeMessage,  
                        onValueChange = { treeMessage = it },  
                        label = { Text("留言（可选）") },  
                        modifier = Modifier.fillMaxWidth(),  
                        singleLine = true  
                    )  
                }  
            }  
        },  
        confirmButton = {  
            TextButton(onClick = {  
                if (isOnTree) onTakeOffTree()  
                else onPutOnTree(treeMessage.ifBlank { null })  
            }) { Text(if (isOnTree) "确定下树" else "确定挂树") }  
        },  
        dismissButton = {  
            TextButton(onClick = onDismiss) { Text("取消") }  
        }  
    )  
}  
  
// ==================== 预约对话框 ====================  
  
@Composable  
private fun SubscribeDialog(  
    onDismiss: () -> Unit,  
    onSubscribe: (bossNum: Int, note: String) -> Unit  
) {  
    var selectedBoss by remember { mutableIntStateOf(1) }  
    var note by remember { mutableStateOf("") }  
  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text("预约Boss") },  
        text = {  
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {  
                Text("选择要预约的Boss：")  
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {  
                    (1..5).forEach { num ->  
                        FilterChip(  
                            selected = selectedBoss == num,  
                            onClick = { selectedBoss = num },  
                            label = { Text("${num}王") }  
                        )  
                    }  
                }  
                OutlinedTextField(  
                    value = note,  
                    onValueChange = { note = it },  
                    label = { Text("备注（可选）") },  
                    modifier = Modifier.fillMaxWidth(),  
                    singleLine = true  
                )  
            }  
        },  
        confirmButton = {  
            TextButton(onClick = { onSubscribe(selectedBoss, note) }) { Text("预约") }  
        },  
        dismissButton = {  
            TextButton(onClick = onDismiss) { Text("取消") }  
        }  
    )  
}  
  
// ==================== 取消预约对话框 ====================  
  
@Composable  
private fun CancelSubscribeDialog(  
    subscribes: List<ManualSubscribeRecord>,  
    playerQq: String,  
    onDismiss: () -> Unit,  
    onCancel: (bossNum: Int) -> Unit  
) {  
    val mySubscribes = subscribes.filter { it.playerQq == playerQq }  
  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text("取消预约") },  
        text = {  
            if (mySubscribes.isEmpty()) {  
                Text("您没有任何预约")  
            } else {  
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {  
                    Text("选择要取消的预约：")  
                    mySubscribes.forEach { sub ->  
                        OutlinedButton(  
                            onClick = { onCancel(sub.bossNum) },  
                            modifier = Modifier.fillMaxWidth()  
                        ) {  
                            Text("取消 ${sub.bossNum}王 预约${if (sub.note.isNotBlank()) "（${sub.note}）" else ""}")  
                        }  
                    }  
                }  
            }  
        },  
        confirmButton = {},  
        dismissButton = {  
            TextButton(onClick = onDismiss) { Text("关闭") }  
        }  
    )  
}  
  
// ==================== SL 对话框 (修复: hasSLToday -> hasSL, 移除 onCheckSL) ====================  
  
@Composable  
private fun SLDialog(  
    hasSL: Boolean,  
    onDismiss: () -> Unit,  
    onRecordSL: () -> Unit,  
    onCancelSL: () -> Unit  
) {  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text("SL 管理") },  
        text = {  
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {  
                Text(if (hasSL) "今日已使用SL" else "今日未使用SL")  
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {  
                    if (!hasSL) {  
                        Button(onClick = onRecordSL) { Text("记录SL") }  
                    } else {  
                        Button(onClick = onCancelSL) { Text("取消SL") }  
                    }  
                }  
            }  
        },  
        confirmButton = {},  
        dismissButton = {  
            TextButton(onClick = onDismiss) { Text("关闭") }  
        }  
    )  
}  
  
// ==================== 报伤害对话框 ====================  
  
@Composable  
private fun ReportHurtDialog(  
    onDismiss: () -> Unit,  
    onReport: (seconds: Int, damage: Long) -> Unit,  
    onCancel: () -> Unit  
) {  
    var secondsText by remember { mutableStateOf("") }  
    var damageText by remember { mutableStateOf("") }  
  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text("报伤害（暂停报伤害）") },  
        text = {  
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {  
                Text("在暂停时报告当前伤害，方便合刀计算", style = MaterialTheme.typography.bodySmall)  
                OutlinedTextField(  
                    value = secondsText,  
                    onValueChange = { secondsText = it.filter { c -> c.isDigit() } },  
                    label = { Text("剩余秒数") },  
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  
                    modifier = Modifier.fillMaxWidth(),  
                    singleLine = true  
                )  
                OutlinedTextField(  
                    value = damageText,  
                    onValueChange = { damageText = it.filter { c -> c.isDigit() } },  
                    label = { Text("伤害（万）") },  
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  
                    modifier = Modifier.fillMaxWidth(),  
                    singleLine = true  
                )  
            }  
        },  
        confirmButton = {  
            TextButton(onClick = {  
                val s = secondsText.toIntOrNull() ?: 1  
                val d = damageText.toLongOrNull() ?: 0  
                onReport(s, d)  
            }) { Text("报告") }  
        },  
        dismissButton = {  
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {  
                TextButton(onClick = onCancel) { Text("清除报伤") }  
                TextButton(onClick = onDismiss) { Text("取消") }  
            }  
        }  
    )  
}  
  
// ==================== 合刀计算对话框 ====================  
  
@Composable  
private fun CombineBladeDialog(  
    onDismiss: () -> Unit,  
    onCalculate: (damage1: Long, damage2: Long, bossHp: Long) -> Unit  
) {  
    var damage1Text by remember { mutableStateOf("") }  
    var damage2Text by remember { mutableStateOf("") }  
    var bossHpText by remember { mutableStateOf("") }  
  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text("合刀计算") },  
        text = {  
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {  
                Text("输入两刀伤害和boss剩余血量（单位：万）", style = MaterialTheme.typography.bodySmall)  
                OutlinedTextField(  
                    value = damage1Text,  
                    onValueChange = { damage1Text = it.filter { c -> c.isDigit() } },  
                    label = { Text("伤害1（万）") },  
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  
                    modifier = Modifier.fillMaxWidth(),  
                    singleLine = true  
                )  
                OutlinedTextField(  
                    value = damage2Text,  
                    onValueChange = { damage2Text = it.filter { c -> c.isDigit() } },  
                    label = { Text("伤害2（万）") },  
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  
                    modifier = Modifier.fillMaxWidth(),  
                    singleLine = true  
                )  
                OutlinedTextField(  
                    value = bossHpText,  
                    onValueChange = { bossHpText = it.filter { c -> c.isDigit() } },  
                    label = { Text("Boss剩余血量（万）") },  
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  
                    modifier = Modifier.fillMaxWidth(),  
                    singleLine = true  
                )  
            }  
        },  
        confirmButton = {  
            TextButton(onClick = {  
                val d1 = (damage1Text.toLongOrNull() ?: 0) * 10000  
                val d2 = (damage2Text.toLongOrNull() ?: 0) * 10000  
                val hp = (bossHpText.toLongOrNull() ?: 0) * 10000  
                onCalculate(d1, d2, hp)  
            }) { Text("计算") }  
        },  
        dismissButton = {  
            TextButton(onClick = onDismiss) { Text("取消") }  
        }  
    )  
}  
  
// ==================== 修改Boss状态对话框 ====================  
  
@Composable  
private fun ModifyBossDialog(  
    currentCycle: Int,  
    bosses: List<ManualBossState>,  
    onDismiss: () -> Unit,  
    onModify: (cycle: Int, bossData: List<Pair<Int, Long>>) -> Unit  
) {  
    var cycleText by remember { mutableStateOf(currentCycle.toString()) }  
    val hpTexts = remember {  
        mutableStateListOf(*bosses.map { (it.currentHp / 10000).toString() }.toTypedArray())  
    }  
  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text("修改Boss状态") },  
        text = {  
            Column(  
                verticalArrangement = Arrangement.spacedBy(8.dp),  
                modifier = Modifier.verticalScroll(rememberScrollState())  
            ) {  
                Text("⚠ 管理员功能，请谨慎操作", color = MaterialTheme.colorScheme.error,  
                    style = MaterialTheme.typography.bodySmall)  
                OutlinedTextField(  
                    value = cycleText,  
                    onValueChange = { cycleText = it.filter { c -> c.isDigit() } },  
                    label = { Text("周目") },  
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  
                    modifier = Modifier.fillMaxWidth(),  
                    singleLine = true  
                )  
                (0 until 5).forEach { i ->  
                    OutlinedTextField(  
                        value = hpTexts[i],  
                        onValueChange = { hpTexts[i] = it.filter { c -> c.isDigit() } },  
                        label = { Text("${i + 1}王血量（万）") },  
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  
                        modifier = Modifier.fillMaxWidth(),  
                        singleLine = true  
                    )  
                }  
            }  
        },  
        confirmButton = {  
            TextButton(onClick = {  
                val cycle = cycleText.toIntOrNull() ?: currentCycle  
                val bossData = (0 until 5).map { i ->  
                    (i + 1) to (hpTexts[i].toLongOrNull() ?: 0) * 10000  
                }  
                onModify(cycle, bossData)  
            }) { Text("确定修改") }  
        },  
        dismissButton = {  
            TextButton(onClick = onDismiss) { Text("取消") }  
        }  
    )  
}  
  
// ==================== 成员列表对话框 ====================  
  
@Composable  
private fun MemberListDialog(  
    members: List<com.pcrjjc.app.data.local.entity.GuildMember>,  
    onDismiss: () -> Unit,  
    onRemove: (String) -> Unit,  
    onViewDetail: (String) -> Unit  
) {  
    var confirmRemoveQq by remember { mutableStateOf<String?>(null) }  
  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text("公会成员（${members.size}人）") },  
        text = {  
            Column(  
                modifier = Modifier  
                    .fillMaxWidth()  
                    .heightIn(max = 400.dp)  
                    .verticalScroll(rememberScrollState()),  
                verticalArrangement = Arrangement.spacedBy(4.dp)  
            ) {  
                if (members.isEmpty()) {  
                    Text("暂无成员")  
                } else {  
                    members.forEach { m ->  
                        Row(  
                            modifier = Modifier.fillMaxWidth(),  
                            verticalAlignment = Alignment.CenterVertically,  
                            horizontalArrangement = Arrangement.SpaceBetween  
                        ) {  
                            Text(  
                                text = m.playerName,  
                                modifier = Modifier.weight(1f),  
                                maxLines = 1,  
                                overflow = TextOverflow.Ellipsis  
                            )  
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {  
                                TextButton(  
                                    onClick = { onViewDetail(m.playerQq) },  
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)  
                                ) { Text("详情", style = MaterialTheme.typography.labelSmall) }  
                                TextButton(  
                                    onClick = { confirmRemoveQq = m.playerQq },  
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)  
                                ) {  
                                    Text(  
                                        "移除",  
                                        style = MaterialTheme.typography.labelSmall,  
                                        color = MaterialTheme.colorScheme.error  
                                    )  
                                }  
                            }  
                        }  
                        HorizontalDivider()  
                    }  
                }  
            }  
        },  
        confirmButton = {},  
        dismissButton = {  
            TextButton(onClick = onDismiss) { Text("关闭") }  
        }  
    )  
  
    // 移除确认  
    if (confirmRemoveQq != null) {  
        val name = members.firstOrNull { it.playerQq == confirmRemoveQq }?.playerName ?: confirmRemoveQq  
        AlertDialog(  
            onDismissRequest = { confirmRemoveQq = null },  
            title = { Text("确认移除") },  
            text = { Text("确定要将 $name 移出公会吗？") },  
            confirmButton = {  
                TextButton(onClick = {  
                    onRemove(confirmRemoveQq!!)  
                    confirmRemoveQq = null  
                }) { Text("移除", color = MaterialTheme.colorScheme.error) }  
            },  
            dismissButton = {  
                TextButton(onClick = { confirmRemoveQq = null }) { Text("取消") }  
            }  
        )  
    }  
}  
  
// ==================== 查树对话框 (重写以匹配调用处) ====================  
  
@OptIn(ExperimentalLayoutApi::class)  
@Composable  
private fun QueryTreeDialog(  
    battleState: ManualBattleState,  
    onDismiss: () -> Unit,  
    onQuery: (bossNum: Int) -> Unit  
) {  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text("查树") },  
        text = {  
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {  
                Text("点击查询对应Boss挂树情况：")  
                FlowRow(  
                    horizontalArrangement = Arrangement.spacedBy(8.dp),  
                    verticalArrangement = Arrangement.spacedBy(8.dp)  
                ) {  
                    (1..5).forEach { num ->  
                        OutlinedButton(onClick = { onQuery(num) }) {  
                            Text("${num}王")  
                        }  
                    }  
                }  
                // 显示当前挂树信息  
                var hasTree = false  
                (1..5).forEach { bossNum ->  
                    val trees = battleState.getTreesForBoss(bossNum)  
                    if (trees.isNotEmpty()) {  
                        hasTree = true  
                        Text(  
                            "${bossNum}王挂树：${trees.joinToString("、") { it.playerName }}",  
                            style = MaterialTheme.typography.bodySmall,  
                            color = MaterialTheme.colorScheme.error  
                        )  
                    }  
                }  
                if (!hasTree) {  
                    Text("当前无人挂树", style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant)  
                }  
            }  
        },  
        confirmButton = {},  
        dismissButton = {  
            TextButton(onClick = onDismiss) { Text("关闭") }  
        }  
    )  
}