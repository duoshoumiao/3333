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
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
import androidx.compose.material3.Button  
import androidx.compose.material3.ButtonDefaults  
import androidx.compose.material3.Card  
import androidx.compose.material3.CardDefaults  
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
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
import com.pcrjjc.app.util.Platform
import com.pcrjjc.app.domain.QueryEngine  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun MasterScreen(  
    viewModel: MasterViewModel = hiltViewModel(),  
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
                title = { Text("主人号") },  
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
                .padding(horizontal = 16.dp),  
            verticalArrangement = Arrangement.spacedBy(12.dp)  
        ) {  
            Spacer(modifier = Modifier.height(4.dp))  
  
            // 平台选择  
            Text("选择服务器", style = MaterialTheme.typography.titleMedium)  
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
  
            // 透视类型选择  
            Text("透视类型", style = MaterialTheme.typography.titleMedium)  
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
  
            // 查询按钮  
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
  
            // 结果列表  
            if (uiState.players.isNotEmpty()) {  
                Text(  
                    text = "共 ${uiState.players.size} 名玩家",  
                    style = MaterialTheme.typography.bodyMedium,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            }  
  
            LazyColumn(  
                modifier = Modifier.fillMaxSize(),  
                verticalArrangement = Arrangement.spacedBy(8.dp)  
            ) {  
                items(uiState.players) { player ->  
                    PlayerCard(  
                        player = player,  
                        isBound = uiState.boundPcrIds.contains(player.viewerId),  
                        isBinding = uiState.bindingId == player.viewerId,  
                        justBound = uiState.bindSuccessIds.contains(player.viewerId),  
                        onBind = { viewModel.bindPlayer(player) }  
                    )  
                }  
                if (uiState.players.isNotEmpty()) {  
                    item { Spacer(modifier = Modifier.height(16.dp)) }  
                }  
            }  
        }  
    }  
}  
  
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