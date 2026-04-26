package com.pcrjjc.app.ui.clanranking  
  
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
import androidx.compose.foundation.text.KeyboardActions  
import androidx.compose.foundation.text.KeyboardOptions  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
import androidx.compose.material.icons.filled.Download  
import androidx.compose.material.icons.filled.Search  
import androidx.compose.material3.Button  
import androidx.compose.material3.Card  
import androidx.compose.material3.CardDefaults  
import androidx.compose.material3.CircularProgressIndicator  
import androidx.compose.material3.ExperimentalMaterial3Api  
import androidx.compose.material3.FilterChip  
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
import androidx.compose.runtime.remember  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.text.font.FontWeight  
import androidx.compose.ui.text.input.ImeAction  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun ClanRankingScreen(  
    viewModel: ClanRankingViewModel = hiltViewModel(),  
    onNavigateBack: () -> Unit  
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
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = { Text("公会排名") },  
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
  
            // ==================== 更新数据按钮 ====================  
            OutlinedButton(  
                onClick = { viewModel.downloadFromGitHub() },  
                modifier = Modifier.fillMaxWidth(),  
                enabled = !uiState.isDownloading  
            ) {  
                if (uiState.isDownloading) {  
                    CircularProgressIndicator(  
                        modifier = Modifier.height(20.dp).width(20.dp),  
                        strokeWidth = 2.dp  
                    )  
                    Spacer(modifier = Modifier.width(8.dp))  
                    Text("下载中...")  
                } else {  
                    Icon(Icons.Default.Download, contentDescription = null)  
                    Spacer(modifier = Modifier.width(8.dp))  
                    Text(  
                        if (uiState.dataLoaded) "更新数据（已加载${uiState.allClans.size}个公会）"  
                        else "更新数据"  
                    )  
                }  
            }  
  
            // ==================== 搜索模式选择 ====================  
            Text("搜索模式", style = MaterialTheme.typography.labelMedium)  
            Row(  
                modifier = Modifier.fillMaxWidth(),  
                horizontalArrangement = Arrangement.spacedBy(8.dp)  
            ) {  
                SearchMode.entries.forEach { mode ->  
                    FilterChip(  
                        selected = uiState.searchMode == mode,  
                        onClick = { viewModel.updateSearchMode(mode) },  
                        label = { Text(mode.displayName) }  
                    )  
                }  
            }  
  
            // ==================== 搜索输入框 ====================  
            val placeholder = when (uiState.searchMode) {  
                SearchMode.LEADER -> "输入会长名关键词"  
                SearchMode.CLAN -> "输入公会名关键词"  
                SearchMode.RANK -> "排名，如: 100 或 1-10 或 100,200"  
            }  
  
            OutlinedTextField(  
                value = uiState.searchKeyword,  
                onValueChange = { viewModel.updateSearchKeyword(it) },  
                modifier = Modifier.fillMaxWidth(),  
                label = { Text(uiState.searchMode.displayName) },  
                placeholder = { Text(placeholder) },  
                singleLine = true,  
                trailingIcon = {  
                    IconButton(onClick = { viewModel.search() }) {  
                        Icon(Icons.Default.Search, contentDescription = "搜索")  
                    }  
                },  
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),  
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() })  
            )  
  
            // ==================== 搜索按钮 ====================  
            Button(  
                onClick = { viewModel.search() },  
                modifier = Modifier.fillMaxWidth(),  
                enabled = !uiState.isDownloading && uiState.searchKeyword.isNotBlank()  
            ) {  
                Icon(Icons.Default.Search, contentDescription = null)  
                Spacer(modifier = Modifier.width(8.dp))  
                Text("搜索")  
            }  
  
            // ==================== 搜索结果标题 ====================  
            if (uiState.searchTitle.isNotBlank()) {  
                Text(  
                    text = uiState.searchTitle,  
                    style = MaterialTheme.typography.titleSmall,  
                    color = MaterialTheme.colorScheme.primary  
                )  
            }  
  
            // ==================== 搜索结果列表 ====================  
            if (uiState.searchResults.isEmpty() && uiState.searchTitle.isNotBlank()) {  
                Text(  
                    text = "无结果",  
                    style = MaterialTheme.typography.bodyMedium,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            } else {  
                LazyColumn(  
                    modifier = Modifier.fillMaxWidth().weight(1f),  
                    verticalArrangement = Arrangement.spacedBy(8.dp)  
                ) {  
                    items(uiState.searchResults, key = { it.rank }) { clan ->  
                        ClanCard(clan = clan)  
                    }  
                    item { Spacer(modifier = Modifier.height(16.dp)) }  
                }  
            }  
        }  
    }  
}  
  
// ==================== 公会卡片 ====================  
  
@Composable  
private fun ClanCard(clan: ClanInfo) {  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
    ) {  
        Row(  
            modifier = Modifier  
                .fillMaxWidth()  
                .padding(horizontal = 16.dp, vertical = 12.dp),  
            verticalAlignment = Alignment.CenterVertically  
        ) {  
            // 左侧排名  
            Text(  
                text = "#${clan.rank}",  
                style = MaterialTheme.typography.headlineSmall,  
                fontWeight = FontWeight.Bold,  
                color = MaterialTheme.colorScheme.primary,  
                modifier = Modifier.width(80.dp)  
            )  
  
            // 右侧信息  
            Column(modifier = Modifier.weight(1f)) {  
                Text(  
                    text = clan.clanName,  
                    style = MaterialTheme.typography.titleMedium,  
                    fontWeight = FontWeight.Bold  
                )  
                Spacer(modifier = Modifier.height(2.dp))  
                Text(  
                    text = "会长: ${clan.leaderName}",  
                    style = MaterialTheme.typography.bodyMedium,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
                Spacer(modifier = Modifier.height(2.dp))  
  
                val damageStr = String.format("%,d", clan.damage)  
                Text(  
                    text = "总伤害: $damageStr",  
                    style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
  
                Row(  
                    modifier = Modifier.fillMaxWidth(),  
                    horizontalArrangement = Arrangement.SpaceBetween  
                ) {  
                    Text(  
                        text = "成员: ${clan.memberNum}/30",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                    Text(  
                        text = "上期: ${clan.gradeRank}位",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.outline  
                    )  
                }  
            }  
        }  
    }  
}