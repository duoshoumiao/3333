package com.pcrjjc.app.ui.home  
  
import android.content.Context  
import android.content.Intent  
import android.net.Uri  
import android.provider.Settings  
import android.widget.Toast  
import androidx.compose.foundation.clickable  
import androidx.compose.foundation.layout.Arrangement  
import androidx.compose.foundation.layout.Column  
import androidx.compose.foundation.layout.Row  
import androidx.compose.foundation.layout.Spacer  
import androidx.compose.foundation.layout.fillMaxSize  
import androidx.compose.foundation.layout.fillMaxWidth  
import androidx.compose.foundation.layout.height  
import androidx.compose.foundation.layout.padding  
import androidx.compose.foundation.lazy.LazyColumn  
import androidx.compose.foundation.lazy.itemsIndexed  
import androidx.compose.foundation.pager.HorizontalPager  
import androidx.compose.foundation.pager.rememberPagerState  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.filled.Add  
import androidx.compose.material.icons.filled.ContentCut  
import androidx.compose.material.icons.filled.Delete  
import androidx.compose.material.icons.filled.History  
import androidx.compose.material.icons.filled.ManageAccounts  
import androidx.compose.material.icons.filled.Search  
import androidx.compose.material.icons.filled.Settings  
import androidx.compose.material.icons.filled.Visibility  
import androidx.compose.material3.Card  
import androidx.compose.material3.CardDefaults  
import androidx.compose.material3.ExperimentalMaterial3Api  
import androidx.compose.material3.FloatingActionButton  
import androidx.compose.material3.Icon  
import androidx.compose.material3.IconButton  
import androidx.compose.material3.MaterialTheme  
import androidx.compose.material3.Scaffold  
import androidx.compose.material3.Tab  
import androidx.compose.material3.TabRow  
import androidx.compose.material3.Text  
import androidx.compose.material3.TopAppBar  
import androidx.compose.material3.TopAppBarDefaults  
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.runtime.rememberCoroutineScope  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.platform.LocalContext  
import androidx.compose.ui.text.font.FontWeight  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
import com.pcrjjc.app.ScreenCaptureActivity  
import com.pcrjjc.app.data.local.entity.PcrBind  
import com.pcrjjc.app.data.local.entity.RankCache  
import com.pcrjjc.app.util.Platform  
import kotlinx.coroutines.launch  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun HomeScreen(  
    viewModel: HomeViewModel = hiltViewModel(),  
    onNavigateToBind: () -> Unit,  
    onNavigateToQuery: (Int) -> Unit,  
    onNavigateToDetail: (Int) -> Unit,  
    onNavigateToHistory: (Long, Int) -> Unit,  
    onNavigateToSettings: () -> Unit,  
    onNavigateToAccount: () -> Unit,  
    onNavigateToMaster: () -> Unit  
) {  
    val jjcBinds by viewModel.jjcBinds.collectAsState()  
    val pjjcBinds by viewModel.pjjcBinds.collectAsState()  
    val manualBinds by viewModel.manualBinds.collectAsState()  
    val rankCaches by viewModel.rankCaches.collectAsState()  
    val context = LocalContext.current  
  
    val totalCount = jjcBinds.size + pjjcBinds.size + manualBinds.size  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = { Text("莱宝竞技场查询") },  
                colors = TopAppBarDefaults.topAppBarColors(  
                    containerColor = MaterialTheme.colorScheme.primaryContainer,  
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer  
                ),  
                actions = {  
                    IconButton(onClick = { launchArenaBreaker(context) }) {  
                        Icon(Icons.Default.ContentCut, contentDescription = "怎么拆")  
                    }  
                    IconButton(onClick = onNavigateToMaster) {  
                        Icon(Icons.Default.Visibility, contentDescription = "账号")  
                    }  
                    IconButton(onClick = onNavigateToAccount) {  
                        Icon(Icons.Default.ManageAccounts, contentDescription = "账号管理")  
                    }  
                    IconButton(onClick = onNavigateToSettings) {  
                        Icon(Icons.Default.Settings, contentDescription = "设置")  
                    }  
                }  
            )  
        },  
        floatingActionButton = {  
            FloatingActionButton(onClick = onNavigateToBind) {  
                Icon(Icons.Default.Add, contentDescription = "添加绑定")  
            }  
        }  
    ) { paddingValues ->  
        if (totalCount == 0) {  
            Column(  
                modifier = Modifier  
                    .fillMaxSize()  
                    .padding(paddingValues),  
                horizontalAlignment = Alignment.CenterHorizontally,  
                verticalArrangement = Arrangement.Center  
            ) {  
                Text(  
                    text = "暂无绑定",  
                    style = MaterialTheme.typography.titleLarge,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
                Spacer(modifier = Modifier.height(8.dp))  
                Text(  
                    text = "点击右下角按钮添加竞技场绑定",  
                    style = MaterialTheme.typography.bodyMedium,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            }  
        } else {  
            // 构建动态 tab 列表  
            val tabs = mutableListOf<String>()  
            if (jjcBinds.isNotEmpty() || pjjcBinds.isEmpty() && manualBinds.isEmpty()) {  
                tabs.add("J场（JJC）")  
            }  
            if (pjjcBinds.isNotEmpty() || jjcBinds.isEmpty() && manualBinds.isEmpty()) {  
                tabs.add("P场（PJJC）")  
            }  
            if (manualBinds.isNotEmpty()) {  
                tabs.add("手动绑定")  
            }  
            // 确保至少有 J场 和 P场 两个 tab  
            if (!tabs.contains("J场（JJC）")) tabs.add(0, "J场（JJC）")  
            if (!tabs.contains("P场（PJJC）")) tabs.add(  
                if (tabs.indexOf("J场（JJC）") >= 0) tabs.indexOf("J场（JJC）") + 1 else 0,  
                "P场（PJJC）"  
            )  
  
            val pagerState = rememberPagerState(pageCount = { tabs.size })  
            val coroutineScope = rememberCoroutineScope()  
  
            Column(  
                modifier = Modifier  
                    .fillMaxSize()  
                    .padding(paddingValues)  
            ) {  
                TabRow(selectedTabIndex = pagerState.currentPage) {  
                    tabs.forEachIndexed { index, title ->  
                        Tab(  
                            selected = pagerState.currentPage == index,  
                            onClick = {  
                                coroutineScope.launch {  
                                    pagerState.animateScrollToPage(index)  
                                }  
                            },  
                            text = {  
                                val count = when (title) {  
                                    "J场（JJC）" -> jjcBinds.size  
                                    "P场（PJJC）" -> pjjcBinds.size  
                                    "手动绑定" -> manualBinds.size  
                                    else -> 0  
                                }  
                                Text("$title ($count)")  
                            }  
                        )  
                    }  
                }  
  
                HorizontalPager(  
                    state = pagerState,  
                    modifier = Modifier.fillMaxSize()  
                ) { page ->  
                    val tabTitle = tabs[page]  
                    when (tabTitle) {  
                        "J场（JJC）" -> {  
                            if (jjcBinds.isEmpty()) {  
                                Column(  
                                    modifier = Modifier.fillMaxSize(),  
                                    horizontalAlignment = Alignment.CenterHorizontally,  
                                    verticalArrangement = Arrangement.Center  
                                ) {  
                                    Text(  
                                        text = "暂无 JJC 绑定",  
                                        style = MaterialTheme.typography.bodyMedium,  
                                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                                    )  
                                }  
                            } else {  
                                LazyColumn(  
                                    modifier = Modifier  
                                        .fillMaxSize()  
                                        .padding(horizontal = 16.dp),  
                                    verticalArrangement = Arrangement.spacedBy(8.dp)  
                                ) {  
                                    item { Spacer(modifier = Modifier.height(8.dp)) }  
                                    itemsIndexed(jjcBinds, key = { _, bind -> "jjc_${bind.id}" }) { index, bind ->  
                                        BindCard(  
                                            index = index + 1,  
                                            bind = bind,  
                                            rankCache = rankCaches[Pair(bind.pcrid, bind.platform)],  
                                            onQuery = { onNavigateToQuery(bind.id) },  
                                            onDetail = { onNavigateToDetail(bind.id) },  
                                            onHistory = { onNavigateToHistory(bind.pcrid, bind.platform) },  
                                            onDelete = { viewModel.deleteBind(bind) }  
                                        )  
                                    }  
                                    item { Spacer(modifier = Modifier.height(80.dp)) }  
                                }  
                            }  
                        }  
                        "P场（PJJC）" -> {  
                            if (pjjcBinds.isEmpty()) {  
                                Column(  
                                    modifier = Modifier.fillMaxSize(),  
                                    horizontalAlignment = Alignment.CenterHorizontally,  
                                    verticalArrangement = Arrangement.Center  
                                ) {  
                                    Text(  
                                        text = "暂无 PJJC 绑定",  
                                        style = MaterialTheme.typography.bodyMedium,  
                                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                                    )  
                                }  
                            } else {  
                                LazyColumn(  
                                    modifier = Modifier  
                                        .fillMaxSize()  
                                        .padding(horizontal = 16.dp),  
                                    verticalArrangement = Arrangement.spacedBy(8.dp)  
                                ) {  
                                    item { Spacer(modifier = Modifier.height(8.dp)) }  
                                    itemsIndexed(pjjcBinds, key = { _, bind -> "pjjc_${bind.id}" }) { index, bind ->  
                                        BindCard(  
                                            index = index + 1,  
                                            bind = bind,  
                                            rankCache = rankCaches[Pair(bind.pcrid, bind.platform)],  
                                            onQuery = { onNavigateToQuery(bind.id) },  
                                            onDetail = { onNavigateToDetail(bind.id) },  
                                            onHistory = { onNavigateToHistory(bind.pcrid, bind.platform) },  
                                            onDelete = { viewModel.deleteBind(bind) }  
                                        )  
                                    }  
                                    item { Spacer(modifier = Modifier.height(80.dp)) }  
                                }  
                            }  
                        }  
                        "手动绑定" -> {  
                            if (manualBinds.isEmpty()) {  
                                Column(  
                                    modifier = Modifier.fillMaxSize(),  
                                    horizontalAlignment = Alignment.CenterHorizontally,  
                                    verticalArrangement = Arrangement.Center  
                                ) {  
                                    Text(  
                                        text = "暂无手动绑定",  
                                        style = MaterialTheme.typography.bodyMedium,  
                                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                                    )  
                                }  
                            } else {  
                                LazyColumn(  
                                    modifier = Modifier  
                                        .fillMaxSize()  
                                        .padding(horizontal = 16.dp),  
                                    verticalArrangement = Arrangement.spacedBy(8.dp)  
                                ) {  
                                    item { Spacer(modifier = Modifier.height(8.dp)) }  
                                    itemsIndexed(manualBinds, key = { _, bind -> "manual_${bind.id}" }) { index, bind ->  
                                        BindCard(  
                                            index = index + 1,  
                                            bind = bind,  
                                            rankCache = rankCaches[Pair(bind.pcrid, bind.platform)],  
                                            onQuery = { onNavigateToQuery(bind.id) },  
                                            onDetail = { onNavigateToDetail(bind.id) },  
                                            onHistory = { onNavigateToHistory(bind.pcrid, bind.platform) },  
                                            onDelete = { viewModel.deleteBind(bind) }  
                                        )  
                                    }  
                                    item { Spacer(modifier = Modifier.height(80.dp)) }  
                                }  
                            }  
                        }  
                    }  
                }  
            }  
        }  
    }  
}  
  
private fun launchArenaBreaker(context: Context) {  
    if (!Settings.canDrawOverlays(context)) {  
        Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show()  
        val intent = Intent(  
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,  
            Uri.parse("package:${context.packageName}")  
        )  
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)  
        context.startActivity(intent)  
        return  
    }  
    val intent = Intent(context, ScreenCaptureActivity::class.java)  
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)  
    context.startActivity(intent)  
}  
  
@Composable  
private fun BindCard(  
    index: Int,  
    bind: PcrBind,  
    rankCache: RankCache?,  
    onQuery: () -> Unit,  
    onDetail: () -> Unit,  
    onHistory: () -> Unit,  
    onDelete: () -> Unit  
) {  
    Card(  
        modifier = Modifier  
            .fillMaxWidth()  
            .clickable(onClick = onQuery),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
    ) {  
        Column(modifier = Modifier.padding(16.dp)) {  
            Row(  
                modifier = Modifier.fillMaxWidth(),  
                horizontalArrangement = Arrangement.SpaceBetween,  
                verticalAlignment = Alignment.CenterVertically  
            ) {  
                Column(modifier = Modifier.weight(1f)) {  
                    Text(  
                        text = "【$index】${bind.name ?: "未命名"}",  
                        style = MaterialTheme.typography.titleMedium  
                    )  
                    Spacer(modifier = Modifier.height(4.dp))  
                    Text(  
                        text = "UID: ${bind.pcrid}",  
                        style = MaterialTheme.typography.bodyMedium,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                    Text(  
                        text = "服务器: ${Platform.fromId(bind.platform).displayName}",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                    if (rankCache != null) {  
                        Text(  
                            text = "JJC: ${rankCache.arenaRank}  PJJC: ${rankCache.grandArenaRank}",  
                            style = MaterialTheme.typography.bodySmall,  
                            color = MaterialTheme.colorScheme.primary  
                        )  
                    } else {  
                        Text(  
                            text = "排名: 加载中...",  
                            style = MaterialTheme.typography.bodySmall,  
                            color = MaterialTheme.colorScheme.onSurfaceVariant  
                        )  
                    }  
                }  
                Row {  
                    IconButton(onClick = onQuery) {  
                        Icon(Icons.Default.Search, contentDescription = "查询")  
                    }  
                    IconButton(onClick = onHistory) {  
                        Icon(Icons.Default.History, contentDescription = "击剑记录")  
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
  
            Row(  
                modifier = Modifier.padding(top = 8.dp),  
                horizontalArrangement = Arrangement.spacedBy(8.dp)  
            ) {  
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
    Card(  
        colors = CardDefaults.cardColors(  
            containerColor = MaterialTheme.colorScheme.secondaryContainer  
        )  
    ) {  
        Text(  
            text = text,  
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),  
            style = MaterialTheme.typography.labelSmall,  
            color = MaterialTheme.colorScheme.onSecondaryContainer  
        )  
    }  
}