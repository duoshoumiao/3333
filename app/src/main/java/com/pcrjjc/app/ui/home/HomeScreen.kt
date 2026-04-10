package com.pcrjjc.app.ui.home  
  
import androidx.compose.foundation.clickable  
import androidx.compose.foundation.layout.*  
import androidx.compose.foundation.lazy.LazyColumn  
import androidx.compose.foundation.lazy.itemsIndexed  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.filled.*  
import androidx.compose.material3.*  
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
import com.pcrjjc.app.data.local.entity.PcrBind  
import com.pcrjjc.app.data.local.entity.RankCache  
import com.pcrjjc.app.util.Platform  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun HomeScreen(  
    viewModel: HomeViewModel = hiltViewModel(),  
    onNavigateToBind: () -> Unit,  
    onNavigateToQuery: (Int) -> Unit,  
    onNavigateToDetail: (Int) -> Unit,  
    onNavigateToHistory: (Long, Int) -> Unit,  
    onNavigateToSettings: () -> Unit,  
    onNavigateToAccount: () -> Unit  
) {  
    val binds by viewModel.binds.collectAsState()  
    val rankCacheMap by viewModel.rankCacheMap.collectAsState()  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = { Text("竞技场查询") },  
                colors = TopAppBarDefaults.topAppBarColors(  
                    containerColor = MaterialTheme.colorScheme.primaryContainer,  
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer  
                ),  
                actions = {  
                    IconButton(onClick = onNavigateToAccount) { Icon(Icons.Default.ManageAccounts, "账号管理") }  
                    IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, "设置") }  
                }  
            )  
        },  
        floatingActionButton = { FloatingActionButton(onClick = onNavigateToBind) { Icon(Icons.Default.Add, "添加绑定") } }  
    ) { pv ->  
        if (binds.isEmpty()) {  
            Column(Modifier.fillMaxSize().padding(pv), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {  
                Text("暂无绑定", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)  
                Spacer(Modifier.height(8.dp))  
                Text("点击右下角按钮添加竞技场绑定", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)  
            }  
        } else {  
            LazyColumn(Modifier.fillMaxSize().padding(pv).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {  
                item { Spacer(Modifier.height(8.dp)) }  
                itemsIndexed(binds) { index, bind ->  
                    BindCard(index + 1, bind, rankCacheMap[Pair(bind.pcrid, bind.platform)],  
                        { onNavigateToQuery(bind.id) }, { onNavigateToDetail(bind.id) },  
                        { onNavigateToHistory(bind.pcrid, bind.platform) }, { viewModel.deleteBind(bind) })  
                }  
                item { Spacer(Modifier.height(80.dp)) }  
            }  
        }  
    }  
}  
  
@Composable  
private fun BindCard(index: Int, bind: PcrBind, rankCache: RankCache?, onQuery: () -> Unit, onDetail: () -> Unit, onHistory: () -> Unit, onDelete: () -> Unit) {  
    Card(Modifier.fillMaxWidth().clickable(onClick = onQuery), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {  
        Column(Modifier.padding(16.dp)) {  
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {  
                Column(Modifier.weight(1f)) {  
                    Text("【$index】${bind.name ?: "未命名"}", style = MaterialTheme.typography.titleMedium)  
                    Spacer(Modifier.height(4.dp))  
                    Text("UID: ${bind.pcrid}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)  
                    Text("服务器: ${Platform.fromId(bind.platform).displayName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)  
                }  
                Row {  
                    IconButton(onClick = onQuery) { Icon(Icons.Default.Search, "查询") }  
                    IconButton(onClick = onHistory) { Icon(Icons.Default.History, "击剑记录") }  
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error) }  
                }  
            }  
            Spacer(Modifier.height(8.dp))  
            if (rankCache != null) {  
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {  
                    Text("JJC: ${rankCache.arenaRank}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)  
                    Text("PJJC: ${rankCache.grandArenaRank}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)  
                }  
            } else {  
                Text("暂无排名数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)  
            }  
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {  
                if (bind.jjcNotice) NoticeChip("JJC")  
                if (bind.pjjcNotice) NoticeChip("PJJC")  
                if (bind.upNotice) NoticeChip("排名上升")  
                if (bind.onlineNotice > 0) NoticeChip("上线LV${bind.onlineNotice}")  
            }  
        }  
    }  
}  
  
@Composable  
private fun NoticeChip(text: String) {  
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {  
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)  
    }  
}