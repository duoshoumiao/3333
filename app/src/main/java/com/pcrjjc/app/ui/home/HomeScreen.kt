package com.pcrjjc.app.ui.home    

import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.scale
import androidx.compose.material3.Checkbox
import com.pcrjjc.app.ui.components.StrokedIcon  
import com.pcrjjc.app.ui.components.GoldColor
import androidx.compose.material.icons.filled.Edit  // ← 新增  
import android.content.Context 
import androidx.compose.material.icons.filled.QuestionAnswer  
import androidx.compose.material.icons.filled.Explore 
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow  
import androidx.compose.foundation.lazy.items  
import androidx.compose.foundation.horizontalScroll  
import androidx.compose.foundation.rememberScrollState  
import androidx.compose.foundation.layout.size  
import androidx.compose.material3.OutlinedTextField  
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices        // ← 新增
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog  
import androidx.compose.material3.Button  
import androidx.compose.material3.ButtonDefaults  
import androidx.compose.material3.TextButton
import com.pcrjjc.app.ui.components.ImageTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue    
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
  
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)  
@Composable      
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToBind: () -> Unit,
    onNavigateToQuery: (Int) -> Unit,
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToHistory: (Long, Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToFortnightly: () -> Unit,
    onNavigateToDaily: () -> Unit,
    onNavigateToRoom: () -> Unit,                     
    onNavigateToClanRanking: () -> Unit,              // ← 加逗号  
    onNavigateToEqa: () -> Unit,  
    onNavigateToLabyrinth: () -> Unit               
) {
    val jjcBinds by viewModel.jjcBinds.collectAsState()
    val pjjcBinds by viewModel.pjjcBinds.collectAsState()
    val manualBinds by viewModel.manualBinds.collectAsState()
    val rankCaches by viewModel.rankCaches.collectAsState()
    val context = LocalContext.current

    val totalCount = jjcBinds.size + pjjcBinds.size + manualBinds.size

    // 折叠菜单状态
    var showMenu by remember { mutableStateOf(false) }

    // 检索用状态  
    var showSearch by remember { mutableStateOf(false) }  
    var searchQuery by remember { mutableStateOf("") }  
  
    // 全部功能入口（顶部横向栏和搜索弹窗共用同一数据源）  
    data class FeatureEntry(val name: String, val icon: ImageVector, val onClick: () -> Unit)  
    val featureEntries = listOf(  
        FeatureEntry("公会战", Icons.Default.MeetingRoom, onNavigateToRoom),  
        FeatureEntry("公会排名", Icons.Default.Leaderboard, onNavigateToClanRanking),  
        FeatureEntry("问答", Icons.Default.QuestionAnswer, onNavigateToEqa),  
        FeatureEntry("黎明界刷开局", Icons.Default.Explore, onNavigateToLabyrinth),  
        FeatureEntry("清日常", Icons.Default.CleaningServices, onNavigateToDaily),  
        FeatureEntry("半月刊", Icons.Default.DateRange, onNavigateToFortnightly),  
        FeatureEntry("怎么拆", Icons.Default.ContentCut) { launchArenaBreaker(context) },  
        FeatureEntry("账号管理", Icons.Default.ManageAccounts, onNavigateToAccount),  
        FeatureEntry("设置", Icons.Default.Settings, onNavigateToSettings)  
    )
	
	// 一键清空确认对话框状态  
    var showClearJjcDialog by remember { mutableStateOf(false) }  
    var showClearPjjcDialog by remember { mutableStateOf(false) }
	var showClearManualDialog by remember { mutableStateOf(false) }
	
	Scaffold(
        topBar = {
            TopAppBar(  
				title = {    
                    LazyRow(    
                        modifier = Modifier.fillMaxWidth(),    
                        verticalAlignment = Alignment.CenterVertically    
                    ) {    
                        item {    
                            IconButton(onClick = { showSearch = true }) {    
                                Icon(Icons.Default.Search, contentDescription = "搜索")    
                            }    
                        }    
                        items(featureEntries) { entry ->    
                            Column(    
                                modifier = Modifier    
                                    .clickable { entry.onClick() }    
                                    .padding(horizontal = 8.dp),    
                                horizontalAlignment = Alignment.CenterHorizontally    
                            ) {    
                                Icon(entry.icon, contentDescription = entry.name)    
                                Text(entry.name, style = MaterialTheme.typography.labelSmall)    
                            }    
                        }    
                    }    
                },    
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
                                    item {  
                                        Button(  
                                            onClick = { showClearJjcDialog = true },  
                                            modifier = Modifier.fillMaxWidth(),  
                                            colors = ButtonDefaults.buttonColors(  
                                                containerColor = MaterialTheme.colorScheme.error  
                                            )  
                                        ) {  
                                            Icon(  
                                                Icons.Default.CleaningServices,  
                                                contentDescription = null,  
                                                tint = MaterialTheme.colorScheme.onError  
                                            )  
                                            Spacer(modifier = Modifier.width(8.dp))  
                                            Text(  
                                                "一键清空绑定（保留已开启J场推送）",  
                                                color = MaterialTheme.colorScheme.onError  
                                            )  
                                        }  
                                    }   
                                    itemsIndexed(jjcBinds, key = { _, bind -> "jjc_${bind.id}" }) { index, bind ->    
                                        BindCard(    
                                            index = index + 1,    
                                            bind = bind,    
                                            rankCache = rankCaches[Pair(bind.pcrid, bind.platform)],    
                                            onQuery = { onNavigateToQuery(bind.id) },    
                                            onDetail = { onNavigateToDetail(bind.id) },    
                                            onHistory = { onNavigateToHistory(bind.pcrid, bind.platform) },    
                                            onDelete = { viewModel.deleteBind(bind) },  
											viewModel = viewModel     
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
                                    item {  
                                        Button(  
                                            onClick = { showClearPjjcDialog = true },  
                                            modifier = Modifier.fillMaxWidth(),  
                                            colors = ButtonDefaults.buttonColors(  
                                                containerColor = MaterialTheme.colorScheme.error  
                                            )  
                                        ) {  
                                            Icon(  
                                                Icons.Default.CleaningServices,  
                                                contentDescription = null,  
                                                tint = MaterialTheme.colorScheme.onError  
                                            )  
                                            Spacer(modifier = Modifier.width(8.dp))  
                                            Text(  
                                                "一键清空绑定（保留已开启P场推送）",  
                                                color = MaterialTheme.colorScheme.onError  
                                            )  
                                        }  
                                    }  
                                    itemsIndexed(pjjcBinds, key = { _, bind -> "pjjc_${bind.id}" }) { index, bind ->
                                        BindCard(    
                                            index = index + 1,    
                                            bind = bind,    
                                            rankCache = rankCaches[Pair(bind.pcrid, bind.platform)],    
                                            onQuery = { onNavigateToQuery(bind.id) },    
                                            onDetail = { onNavigateToDetail(bind.id) },    
                                            onHistory = { onNavigateToHistory(bind.pcrid, bind.platform) },    
                                            onDelete = { viewModel.deleteBind(bind) },  
											viewModel = viewModel  // ← 添加这行      
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
                                    item {  
                                        Button(  
                                            onClick = { showClearManualDialog = true },  
                                            modifier = Modifier.fillMaxWidth(),  
                                            colors = ButtonDefaults.buttonColors(  
                                                containerColor = MaterialTheme.colorScheme.error  
                                            )  
                                        ) {  
                                            Icon(  
                                                Icons.Default.CleaningServices,  
                                                contentDescription = null,  
                                                tint = MaterialTheme.colorScheme.onError  
                                            )  
                                            Spacer(modifier = Modifier.width(8.dp))  
                                            Text(  
                                                "一键清空绑定（保留已开启推送）",
                                                color = MaterialTheme.colorScheme.onError  
                                            )  
                                        }  
                                    }  
                                    itemsIndexed(manualBinds, key = { _, bind -> "manual_${bind.id}" }) { index, bind ->  
                                        BindCard(    
                                            index = index + 1,    
                                            bind = bind,    
                                            rankCache = rankCaches[Pair(bind.pcrid, bind.platform)],    
                                            onQuery = { onNavigateToQuery(bind.id) },    
                                            onDetail = { onNavigateToDetail(bind.id) },    
                                            onHistory = { onNavigateToHistory(bind.pcrid, bind.platform) },    
                                            onDelete = { viewModel.deleteBind(bind) },  
											viewModel = viewModel  // ← 添加这行      
                                        )    
                                    }    
                                    item { Spacer(modifier = Modifier.height(80.dp)) }    
                                }    
                            }    
                        }    
                    }    
                }    
            }
			if (showClearJjcDialog) {  
                AlertDialog(  
                    onDismissRequest = { showClearJjcDialog = false },  
                    title = { Text("清空 J 场绑定") },  
                    text = { Text("将删除所有 J 场绑定，但保留已开启「J场」推送的绑定。确定继续吗？") },  
                    confirmButton = {  
                        TextButton(onClick = {  
                            viewModel.clearJjcBinds()  
                            showClearJjcDialog = false  
                        }) { Text("确定") }  
                    },  
                    dismissButton = {  
                        TextButton(onClick = { showClearJjcDialog = false }) { Text("取消") }  
                    }  
                )  
            }  
            if (showClearPjjcDialog) {  
                AlertDialog(  
                    onDismissRequest = { showClearPjjcDialog = false },  
                    title = { Text("清空 P 场绑定") },  
                    text = { Text("将删除所有 P 场绑定，但保留已开启「P场」推送的绑定。确定继续吗？") },  
                    confirmButton = {  
                        TextButton(onClick = {  
                            viewModel.clearPjjcBinds()  
                            showClearPjjcDialog = false  
                        }) { Text("确定") }  
                    },  
                    dismissButton = {  
                        TextButton(onClick = { showClearPjjcDialog = false }) { Text("取消") }  
                    }  
                )  
            }
			if (showClearManualDialog) {  
                AlertDialog(  
                    onDismissRequest = { showClearManualDialog = false },  
                    title = { Text("清空手动绑定") },  
                    text = { Text("将删除所有手动绑定，但保留已开启任意推送（J场/P场/上升/上线）的绑定。确定继续吗？") },  
                    confirmButton = {  
                        TextButton(onClick = {  
                            viewModel.clearManualBinds()  
                            showClearManualDialog = false  
                        }) { Text("确定") }  
                    },  
                    dismissButton = {  
                        TextButton(onClick = { showClearManualDialog = false }) { Text("取消") }  
                    }  
                )  
            }	
        }
		if (showSearch) {  
                AlertDialog(  
                    onDismissRequest = {  
                        showSearch = false  
                        searchQuery = ""  
                    },  
                    title = { Text("搜索功能") },  
                    text = {  
                        Column {  
                            OutlinedTextField(  
                                value = searchQuery,  
                                onValueChange = { searchQuery = it },  
                                modifier = Modifier.fillMaxWidth(),  
                                singleLine = true,  
                                leadingIcon = {  
                                    Icon(Icons.Default.Search, contentDescription = null)  
                                },  
                                placeholder = { Text("输入功能名") }  
                            )  
                            Spacer(modifier = Modifier.height(8.dp))  
                            // 可上下滑动的功能名列表（按输入过滤）  
                            LazyColumn(  
                                modifier = Modifier  
                                    .fillMaxWidth()  
                                    .heightIn(max = 320.dp)  
                            ) {  
                                items(  
                                    featureEntries.filter {  
                                        it.name.contains(searchQuery, ignoreCase = true)  
                                    }  
                                ) { entry ->  
                                    Row(  
                                        modifier = Modifier  
                                            .fillMaxWidth()  
                                            .clickable {  
                                                entry.onClick()  
                                                showSearch = false  
                                                searchQuery = ""  
                                            }  
                                            .padding(vertical = 12.dp),  
                                        verticalAlignment = Alignment.CenterVertically  
                                    ) {  
                                        Icon(entry.icon, contentDescription = null)  
                                        Spacer(modifier = Modifier.width(12.dp))  
                                        Text(entry.name)  
                                    }  
                                }  
                            }  
                        }  
                    },  
                    confirmButton = {  
                        TextButton(onClick = {  
                            showSearch = false  
                            searchQuery = ""  
                        }) { Text("关闭") }  
                    }  
                )  
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
    onDelete: () -> Unit,  
    viewModel: HomeViewModel  
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
					
                    Spacer(modifier = Modifier.height(8.dp))  
                      
                    Row(  
						modifier = Modifier.fillMaxWidth(),  
						horizontalArrangement = Arrangement.spacedBy(8.dp) // 增加间距  
					) {  
						NoticeCheckbox(  
							label = "J场",  
							checked = bind.jjcNotice,  
							onCheckedChange = {   
								viewModel.updateBindNotice(bind, jjcNotice = it)  
							},  
							modifier = Modifier.weight(1f)  
						)  
						NoticeCheckbox(  
							label = "P场",  
							checked = bind.pjjcNotice,  
							onCheckedChange = {   
								viewModel.updateBindNotice(bind, pjjcNotice = it)  
							},  
							modifier = Modifier.weight(1f)  
						)  
						NoticeCheckbox(  
							label = "上升",  
							checked = bind.upNotice,  
							onCheckedChange = {   
								viewModel.updateBindNotice(bind, upNotice = it)  
							},  
							modifier = Modifier.weight(1f)  
						)  
						NoticeCheckbox(  
							label = "上线",  
							checked = bind.onlineNotice != 0,  
							onCheckedChange = { checked ->  
								viewModel.updateBindNotice(  
									bind,  
									onlineNotice = if (checked) 1 else 0  
								)  
							},  
							modifier = Modifier.weight(1f)  
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

@Composable  
private fun NoticeCheckbox(  
    label: String,  
    checked: Boolean,  
    onCheckedChange: (Boolean) -> Unit,  
    modifier: Modifier = Modifier  
) {  
    Row(  
        modifier = modifier,  
        horizontalArrangement = Arrangement.Start, // 改为左对齐  
        verticalAlignment = Alignment.CenterVertically  
    ) {  
        Text(  
            text = label,   
            style = MaterialTheme.typography.bodySmall, // 使用更小字体  
            modifier = Modifier.padding(end = 4.dp) // 减少间距  
        )  
        Checkbox(  
            checked = checked,   
            onCheckedChange = onCheckedChange,  
            modifier = Modifier.scale(0.8f) // 缩小复选框  
        )  
    }  
}