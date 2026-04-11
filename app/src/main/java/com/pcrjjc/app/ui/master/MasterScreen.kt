package com.pcrjjc.app.ui.master  
  
import androidx.compose.foundation.layout.Arrangement  
import androidx.compose.foundation.layout.Column  
import androidx.compose.foundation.layout.Row  
import androidx.compose.foundation.layout.Spacer  
import androidx.compose.foundation.layout.fillMaxSize  
import androidx.compose.foundation.layout.fillMaxWidth  
import androidx.compose.foundation.layout.height  
import androidx.compose.foundation.layout.padding  
import androidx.compose.foundation.layout.width  
import androidx.compose.foundation.lazy.LazyColumn  
import androidx.compose.foundation.lazy.items  
import androidx.compose.foundation.text.KeyboardOptions  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
import androidx.compose.material.icons.filled.Add  
import androidx.compose.material.icons.filled.Delete  
import androidx.compose.material3.AlertDialog  
import androidx.compose.material3.Button  
import androidx.compose.material3.ButtonDefaults  
import androidx.compose.material3.Card  
import androidx.compose.material3.CardDefaults  
import androidx.compose.material3.CircularProgressIndicator  
import androidx.compose.material3.ExperimentalMaterial3Api  
import androidx.compose.material3.FilterChip  
import androidx.compose.material3.FloatingActionButton  
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
import androidx.compose.material3.TextButton  
import androidx.compose.material3.TopAppBar  
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
import androidx.compose.ui.text.input.KeyboardType  
import androidx.compose.ui.text.input.PasswordVisualTransformation  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
import com.pcrjjc.app.data.local.entity.Account  
import com.pcrjjc.app.domain.QueryEngine  
import com.pcrjjc.app.util.Platform  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun MasterScreen(  
    viewModel: MasterViewModel = hiltViewModel(),  
    onNavigateBack: () -> Unit  
) {  
    val uiState by viewModel.uiState.collectAsState()  
    val masterAccounts by viewModel.masterAccounts.collectAsState()  
    val snackbarHostState = remember { SnackbarHostState() }  
    var showAddDialog by remember { mutableStateOf(false) }  
  
    LaunchedEffect(uiState.errorMessage) {  
        uiState.errorMessage?.let {  
            snackbarHostState.showSnackbar(it)  
            viewModel.clearError()  
        }  
    }  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = { Text("我的账号") },  
                navigationIcon = {  
                    IconButton(onClick = onNavigateBack) {  
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")  
                    }  
                }  
            )  
        },  
        snackbarHost = { SnackbarHost(snackbarHostState) },  
        floatingActionButton = {  
            FloatingActionButton(onClick = { showAddDialog = true }) {  
                Icon(Icons.Default.Add, contentDescription = "添加账号")  
            }  
        }  
    ) { paddingValues ->  
        LazyColumn(  
            modifier = Modifier  
                .fillMaxSize()  
                .padding(paddingValues)  
                .padding(horizontal = 16.dp),  
            verticalArrangement = Arrangement.spacedBy(12.dp)  
        ) {  
            item { Spacer(modifier = Modifier.height(4.dp)) }  
  
            // ==================== 账号列表 ====================  
            item {  
                Text(  
                    "账号（仅用于透视，不参与轮询监控）",  
                    style = MaterialTheme.typography.titleMedium  
                )  
            }  
  
            if (masterAccounts.isEmpty()) {  
                item {  
                    Text(  
                        text = "暂无账号，请点击右下角添加",  
                        style = MaterialTheme.typography.bodyMedium,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                }  
            } else {  
                items(masterAccounts) { account ->  
                    MasterAccountCard(  
                        account = account,  
                        onDelete = { viewModel.deleteMasterAccount(account) }  
                    )  
                }  
            }  
  
            // ==================== 竞技场透视 ====================  
            item { Spacer(modifier = Modifier.height(8.dp)) }  
  
            item {  
                Text("竞技场透视", style = MaterialTheme.typography.titleMedium)  
            }  
  
            // 平台选择  
            item {  
                Text("选择服务器", style = MaterialTheme.typography.labelMedium)  
                Spacer(modifier = Modifier.height(4.dp))  
                Row(  
                    modifier = Modifier.fillMaxWidth(),  
                    horizontalArrangement = Arrangement.spacedBy(8.dp)  
                ) {  
                    Platform.entries.forEach { platform ->  
                        FilterChip(  
                            selected = uiState.selectedPlatform == platform,  
                            onClick = { viewModel.updatePlatform(platform) },  
                            label = { Text(platform.displayName) }  
                        )  
                    }  
                }  
            }  
  
            // 透视类型选择  
            item {  
                Text("透视类型", style = MaterialTheme.typography.labelMedium)  
                Spacer(modifier = Modifier.height(4.dp))  
                Row(  
                    modifier = Modifier.fillMaxWidth(),  
                    horizontalArrangement = Arrangement.spacedBy(8.dp)  
                ) {  
                    ArenaType.entries.forEach { type ->  
                        FilterChip(  
                            selected = uiState.selectedType == type,  
                            onClick = { viewModel.updateType(type) },  
                            label = { Text(type.displayName) }  
                        )  
                    }  
                }  
            }  
  
            // 查询按钮  
            item {  
                Button(  
                    onClick = { viewModel.queryRanking() },  
                    modifier = Modifier.fillMaxWidth(),  
                    enabled = !uiState.isLoading  
                ) {  
                    if (uiState.isLoading) {  
                        CircularProgressIndicator(  
                            modifier = Modifier  
                                .height(20.dp)  
                                .width(20.dp),  
                            strokeWidth = 2.dp,  
                            color = MaterialTheme.colorScheme.onPrimary  
                        )  
                        Spacer(modifier = Modifier.width(8.dp))  
                        Text("查询中...")  
                    } else {  
                        Text("开始透视")  
                    }  
                }  
            }  
  
            // ==================== J场 ====================  
            item {  
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))  
                Row(  
                    modifier = Modifier.fillMaxWidth(),  
                    horizontalArrangement = Arrangement.SpaceBetween,  
                    verticalAlignment = Alignment.CenterVertically  
                ) {  
                    Text(  
                        text = "J场（JJC）",  
                        style = MaterialTheme.typography.titleMedium,  
                        fontWeight = FontWeight.Bold,  
                        color = MaterialTheme.colorScheme.primary  
                    )  
                    Text(  
                        text = "${uiState.jjcPlayers.size} 人",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                }  
            }  
  
            if (uiState.jjcPlayers.isEmpty()) {  
                item {  
                    Text(  
                        text = "暂无数据，请选择 JJC透视 后点击开始透视",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                }  
            } else {  
                items(uiState.jjcPlayers, key = { "jjc_${it.viewerId}" }) { player ->  
                    PlayerCard(  
                        player = player,  
                        isBound = uiState.boundPcrIds.contains(player.viewerId),  
                        isBinding = uiState.bindingId == player.viewerId,  
                        justBound = uiState.bindSuccessIds.contains(player.viewerId),  
                        onBind = { viewModel.bindPlayer(player, ArenaType.JJC) } 
                    )  
                }  
            }  
  
            // ==================== P场 ====================  
            item {  
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))  
                Row(  
                    modifier = Modifier.fillMaxWidth(),  
                    horizontalArrangement = Arrangement.SpaceBetween,  
                    verticalAlignment = Alignment.CenterVertically  
                ) {  
                    Text(  
                        text = "P场（PJJC）",  
                        style = MaterialTheme.typography.titleMedium,  
                        fontWeight = FontWeight.Bold,  
                        color = MaterialTheme.colorScheme.tertiary  
                    )  
                    Text(  
                        text = "${uiState.pjjcPlayers.size} 人",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                }  
            }  
  
            if (uiState.pjjcPlayers.isEmpty()) {  
                item {  
                    Text(  
                        text = "暂无数据，请选择 PJJC透视 后点击开始透视",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                }  
            } else {  
                items(uiState.pjjcPlayers, key = { "pjjc_${it.viewerId}" }) { player ->  
                    PlayerCard(  
                        player = player,  
                        isBound = uiState.boundPcrIds.contains(player.viewerId),  
                        isBinding = uiState.bindingId == player.viewerId,  
                        justBound = uiState.bindSuccessIds.contains(player.viewerId),  
                        onBind = { viewModel.bindPlayer(player, ArenaType.PJJC) }
                    )  
                }  
            }  
  
            item { Spacer(modifier = Modifier.height(80.dp)) }  
        }  
  
        // 添加账号对话框  
        if (showAddDialog) {  
            AddMasterAccountDialog(  
                viewModel = viewModel,  
                onDismiss = { showAddDialog = false }  
            )  
        }  
    }  
}  
  
// ==================== 账号卡片 ====================  
  
@Composable  
private fun MasterAccountCard(  
    account: Account,  
    onDelete: () -> Unit  
) {  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
    ) {  
        Row(  
            modifier = Modifier  
                .fillMaxWidth()  
                .padding(16.dp),  
            horizontalArrangement = Arrangement.SpaceBetween,  
            verticalAlignment = Alignment.CenterVertically  
        ) {  
            Column(modifier = Modifier.weight(1f)) {  
                Text(  
                    text = account.account,  
                    style = MaterialTheme.typography.titleMedium  
                )  
                Text(  
                    text = Platform.fromId(account.platform).displayName,  
                    style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
                if (account.viewerId.isNotEmpty()) {  
                    Text(  
                        text = "ViewerID: ${account.viewerId.take(6)}...",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                }  
            }  
            IconButton(onClick = onDelete) {  
                Icon(  
                    Icons.Default.Delete,  
                    contentDescription = "删除",  
                    tint = MaterialTheme.colorScheme.error  
                )  
            }  
        }  
    }  
}  
  
// ==================== 玩家卡片（透视结果） ====================  
  
@Composable  
private fun PlayerCard(  
    player: QueryEngine.ArenaRankingPlayer,  
    isBound: Boolean,  
    isBinding: Boolean,  
    justBound: Boolean,  
    onBind: () -> Unit  
) {  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
    ) {  
        Row(  
            modifier = Modifier  
                .fillMaxWidth()  
                .padding(horizontal = 16.dp, vertical = 12.dp),  
            horizontalArrangement = Arrangement.SpaceBetween,  
            verticalAlignment = Alignment.CenterVertically  
        ) {  
            Column(modifier = Modifier.weight(1f)) {  
                Text(  
                    text = "${String.format("%02d", player.rank)}: ${player.userName}",  
                    style = MaterialTheme.typography.titleMedium  
                )  
                Spacer(modifier = Modifier.height(2.dp))  
                Text(  
                    text = "UID: ${player.viewerId}",  
                    style = MaterialTheme.typography.bodyMedium,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
                Text(  
                    text = "Lv.${player.teamLevel}",  
                    style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            }  
  
            Spacer(modifier = Modifier.width(8.dp))  
  
            if (isBound) {  
                OutlinedButton(  
                    onClick = {},  
                    enabled = false  
                ) {  
                    Text(if (justBound) "已绑定 ✓" else "已绑定")  
                }  
            } else {  
                Button(  
                    onClick = onBind,  
                    enabled = !isBinding,  
                    colors = ButtonDefaults.buttonColors(  
                        containerColor = MaterialTheme.colorScheme.primary  
                    )  
                ) {  
                    if (isBinding) {  
                        CircularProgressIndicator(  
                            modifier = Modifier  
                                .height(16.dp)  
                                .width(16.dp),  
                            strokeWidth = 2.dp,  
                            color = MaterialTheme.colorScheme.onPrimary  
                        )  
                    } else {  
                        Text("绑定")  
                    }  
                }  
            }  
        }  
    }  
}  
  
// ==================== 添加账号对话框 ====================  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
private fun AddMasterAccountDialog(  
    viewModel: MasterViewModel,  
    onDismiss: () -> Unit  
) {  
    val uiState by viewModel.uiState.collectAsState()  
  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text("添加账号") },  
        text = {  
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {  
                Text(  
                    text = "账号仅用于竞技场透视，不参与轮询监控",  
                    style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
  
                // 平台选择  
                Text("服务器", style = MaterialTheme.typography.labelMedium)  
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {  
                    Platform.entries.forEach { platform ->  
                        FilterChip(  
                            selected = uiState.selectedPlatform == platform,  
                            onClick = { viewModel.updatePlatform(platform) },  
                            label = { Text(platform.displayName) }  
                        )  
                    }  
                }  
  
                OutlinedTextField(  
                    value = uiState.addAccount,  
                    onValueChange = { viewModel.updateAddAccount(it) },  
                    label = { Text("账号") },  
                    modifier = Modifier.fillMaxWidth(),  
                    singleLine = true  
                )  
  
                OutlinedTextField(  
                    value = uiState.addPassword,  
                    onValueChange = { viewModel.updateAddPassword(it) },  
                    label = { Text("密码") },  
                    modifier = Modifier.fillMaxWidth(),  
                    singleLine = true,  
                    visualTransformation = PasswordVisualTransformation(),  
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)  
                )  
  
                if (uiState.selectedPlatform == Platform.QU_SERVER) {  
                    OutlinedTextField(  
                        value = uiState.addViewerId,  
                        onValueChange = { viewModel.updateAddViewerId(it) },  
                        label = { Text("ViewerID (渠道服需要)") },  
                        modifier = Modifier.fillMaxWidth(),  
                        singleLine = true  
                    )  
                }  
  
                if (uiState.addError != null) {  
                    Text(  
                        text = uiState.addError!!,  
                        color = MaterialTheme.colorScheme.error,  
                        style = MaterialTheme.typography.bodySmall  
                    )  
                }  
            }  
        },  
        confirmButton = {  
            TextButton(  
                onClick = {  
                    viewModel.addMasterAccount()  
                    onDismiss()  
                },  
                enabled = !uiState.isAddingAccount  
            ) {  
                Text(if (uiState.isAddingAccount) "添加中..." else "添加")  
            }  
        },  
        dismissButton = {  
            TextButton(onClick = onDismiss) {  
                Text("取消")  
            }  
        }  
    )  
}