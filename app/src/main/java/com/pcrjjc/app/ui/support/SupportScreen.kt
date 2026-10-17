package com.pcrjjc.app.ui.support  
  
import androidx.compose.foundation.border  
import androidx.compose.foundation.clickable  
import androidx.compose.foundation.horizontalScroll  
import androidx.compose.foundation.layout.Arrangement  
import androidx.compose.foundation.layout.Box  
import androidx.compose.foundation.layout.Column  
import androidx.compose.foundation.layout.Row  
import androidx.compose.foundation.layout.Spacer  
import androidx.compose.foundation.layout.fillMaxSize  
import androidx.compose.foundation.layout.fillMaxWidth  
import androidx.compose.foundation.layout.height  
import androidx.compose.foundation.layout.padding  
import androidx.compose.foundation.layout.size  
import androidx.compose.foundation.layout.width  
import androidx.compose.foundation.lazy.grid.GridCells  
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid  
import androidx.compose.foundation.lazy.grid.items  
import androidx.compose.foundation.rememberScrollState  
import androidx.compose.foundation.verticalScroll  
import androidx.compose.foundation.shape.RoundedCornerShape  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
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
import androidx.compose.material3.Tab  
import androidx.compose.material3.TabRow  
import androidx.compose.material3.Text  
import androidx.compose.material3.TopAppBar  
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.runtime.mutableIntStateOf  
import androidx.compose.runtime.mutableStateOf  
import androidx.compose.runtime.remember  
import androidx.compose.runtime.setValue  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.draw.clip  
import androidx.compose.ui.layout.ContentScale  
import androidx.compose.ui.platform.LocalContext  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
import coil.compose.AsyncImage  
import coil.request.ImageRequest  
import com.pcrjjc.app.domain.SupportEngine.SupportData  
import com.pcrjjc.app.domain.SupportEngine.SupportSlot  
import com.pcrjjc.app.ui.detail.UnitIcon  
import com.pcrjjc.app.util.ExIconStorage  
import java.io.File  
  
/** EX装备图标：优先本地缓存，无缓存回退网络URL */  
@Composable  
fun ExEquipIcon(exEquipId: Int, modifier: Modifier = Modifier, size: Int = 40) {  
    val context = LocalContext.current  
    val localPath = ExIconStorage.getIconPath(context, exEquipId)  
    android.util.Log.d("ExIcon", "ex=$exEquipId localPath=$localPath")  
    AsyncImage(  
        model = ImageRequest.Builder(context)  
            .data(if (localPath != null) File(localPath) else null)  
            .crossfade(true)  
            .listener(  
                onError = { _, r ->  
                    android.util.Log.w("ExIcon", "load fail ex=$exEquipId ${r.throwable}")  
                }  
            )  
            .build(),  
        contentDescription = "EX装备 $exEquipId",  
        modifier = modifier.size(size.dp).clip(RoundedCornerShape(6.dp)),  
        contentScale = ContentScale.Crop  
    )  
}
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun SupportScreen(  
    viewModel: SupportViewModel = hiltViewModel(),  
    onNavigateBack: () -> Unit  
) {  
    val uiState by viewModel.uiState.collectAsState()  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = { Text("我的支援") },  
                navigationIcon = {  
                    IconButton(onClick = onNavigateBack) {  
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")  
                    }  
                }  
            )  
        }  
    ) { paddingValues ->  
        Column(  
            modifier = Modifier  
                .fillMaxSize()  
                .padding(paddingValues)  
                .padding(16.dp)  
                .verticalScroll(rememberScrollState()),  
            verticalArrangement = Arrangement.spacedBy(12.dp)  
        ) {  
            // 账号选择（来自「我的账号」isMaster 列表）  
            Row(  
                modifier = Modifier  
                    .fillMaxWidth()  
                    .horizontalScroll(rememberScrollState()),  
                horizontalArrangement = Arrangement.spacedBy(8.dp)  
            ) {  
                uiState.masterAccounts.forEach { acc ->  
                    FilterChip(  
                        selected = uiState.selectedAccount?.id == acc.id,  
                        onClick = { viewModel.selectAccount(acc) },  
                        label = { Text(acc.account) }  
                    )  
                }  
            }  
  
            if (uiState.isLoading && uiState.data == null) {  
                Row(  
                    modifier = Modifier.fillMaxWidth(),  
                    horizontalArrangement = Arrangement.Center  
                ) { CircularProgressIndicator() }  
            }  
  
            uiState.error?.let {  
                Text(it, color = MaterialTheme.colorScheme.error,  
                    style = MaterialTheme.typography.bodySmall)  
            }  
            uiState.message?.let {  
                Text(it, color = MaterialTheme.colorScheme.primary,  
                    style = MaterialTheme.typography.bodySmall)  
            }  
  
            val data = uiState.data  
            val editing = uiState.editingSlot  
  
            if (editing != null) {  
                // ===== 支援角色更换模式 =====  
                EditSupportPanel(  
                    viewModel = viewModel,  
                    data = data,  
                    editingUnitId = uiState.editingUnitId ?: 0L,  
                    nameMap = uiState.nameMap  
                )  
            } else if (data != null) {  
                // ===== 三类支援展示 =====  
                SupportGroup(  
                    title = "地下城·追忆的战场",  
                    slots = data.dungeon,  
                    data = data,  
                    isCb = false,  
                    onIconClick = { slot ->  
                        viewModel.startEdit(1, slot.position, slot.unitId)  
                    }  
                )  
                SupportGroup(  
                    title = "团队战·露娜塔",  
                    slots = data.clanBattle,  
                    data = data,  
                    isCb = true,  
                    onIconClick = { slot ->  
                        viewModel.startEdit(1, slot.position, slot.unitId)  
                    }  
                )  
                SupportGroup(  
                    title = "关卡·活动(好友)",  
                    slots = data.friend,  
                    data = data,  
                    isCb = false,  
                    onIconClick = { slot ->  
                        viewModel.startEdit(2, slot.position, slot.unitId)  
                    }  
                )  
            }  
        }  
    }  
}  
  
@Composable  
private fun SupportGroup(  
    title: String,  
    slots: List<SupportSlot>,  
    data: SupportData,  
    isCb: Boolean,  
    onIconClick: (SupportSlot) -> Unit  
) {  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
    ) {  
        Column(modifier = Modifier.padding(16.dp)) {  
            Text(title, style = MaterialTheme.typography.titleMedium)  
            Spacer(modifier = Modifier.height(8.dp))  
            if (slots.isEmpty()) {  
                Text("无", style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant)  
            }  
            slots.forEach { slot ->  
                Row(verticalAlignment = Alignment.CenterVertically) {  
                    if (slot.unitId == 0L) {  
                        // 空支援位：占位框，点击进入更换模式  
                        Box(  
                            modifier = Modifier  
                                .size(56.dp)  
                                .clip(RoundedCornerShape(6.dp))  
                                .border(  
                                    1.dp,  
                                    MaterialTheme.colorScheme.outline,  
                                    RoundedCornerShape(6.dp)  
                                )  
                                .clickable { onIconClick(slot) },  
                            contentAlignment = Alignment.Center  
                        ) {  
                            Text("+", style = MaterialTheme.typography.titleMedium,  
                                color = MaterialTheme.colorScheme.outline)  
                        }  
                    } else {  
                        // 头像来自本地缓存（UnitIcon 内部走 IconStorage）  
                        UnitIcon(  
                            unitId = slot.unitId.toInt(),  
                            size = 56.dp,  
                            modifier = Modifier.clickable { onIconClick(slot) }  
                        )  
                    }  
                    Spacer(modifier = Modifier.width(12.dp))  
                    // 该角色穿戴的EX装备（会战位显示会战EX，其余显示普通EX）  
                    val exSlots = if (isCb) slot.cbExSlots else slot.exSlots  
                    val worn = exSlots.mapNotNull { (_, serial) ->  
                        data.exInventory[serial]  
                    }  
                    if (slot.unitId == 0L) {  
                        Text("未设置支援", style = MaterialTheme.typography.bodySmall,  
                            color = MaterialTheme.colorScheme.onSurfaceVariant)  
                    } else if (worn.isEmpty()) {  
                        Text("无EX装备", style = MaterialTheme.typography.bodySmall,  
                            color = MaterialTheme.colorScheme.onSurfaceVariant)  
                    } else {  
                        worn.forEach { ex ->  
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {  
                                ExEquipIcon(ex.exEquipId, size = 40)  
                                Text("★${ex.rank}", style = MaterialTheme.typography.labelSmall)  
                            }  
                            Spacer(modifier = Modifier.width(8.dp))  
                        }  
                    }  
                }  
                Spacer(modifier = Modifier.height(8.dp))  
            }  
        }  
    }  
}  
  
@Composable  
private fun EditSupportPanel(  
    viewModel: SupportViewModel,  
    data: SupportData?,  
    editingUnitId: Long,  
    nameMap: Map<Int, List<String>>  
) {  
    var tabIndex by remember { mutableIntStateOf(0) }  
    var pickedUnit by remember { mutableStateOf(editingUnitId) }  
    // 每个槽位选的 serial_id（3个槽）  
    var pickedSerials by remember {  
        mutableStateOf(mutableMapOf<Int, Long>())  
    }  
  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
    ) {  
        Column(modifier = Modifier.padding(16.dp)) {  
            Text("更换支援角色", style = MaterialTheme.typography.titleMedium)  
            Spacer(modifier = Modifier.height(8.dp))  
  
            // ===== 花名册角色搜索 =====  
            var searchQuery by remember { mutableStateOf("") }  
            OutlinedTextField(  
                value = searchQuery,  
                onValueChange = { searchQuery = it },  
                modifier = Modifier.fillMaxWidth(),  
                placeholder = { Text("输入角色名/昵称搜索") },  
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },  
                singleLine = true  
            )  
            Spacer(modifier = Modifier.height(8.dp))  
  
            // 拥有角色网格（本地缓存头像），按花名册名字过滤  
            val allUnitIds = data?.unitExSlots?.keys?.toList() ?: emptyList()  
            val unitIds = if (searchQuery.isBlank()) {  
                allUnitIds  
            } else {  
                val q = searchQuery.trim()  
                allUnitIds.filter { uid ->  
                    val baseId = (uid / 100).toInt()  
                    nameMap[baseId]?.any { n -> n.contains(q, ignoreCase = true) } == true ||  
                            uid.toString().contains(q)  
                }  
            }  
            if (searchQuery.isNotBlank() && unitIds.isEmpty()) {  
                Text(  
                    "未找到匹配角色",  
                    style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            }  
  
            LazyVerticalGrid(  
                columns = GridCells.Fixed(6),  
                modifier = Modifier  
                    .fillMaxWidth()  
                    .height(220.dp)  
            ) {  
                items(unitIds) { uid ->  
                    val isPicked = pickedUnit == uid  
                    Box(  
                        modifier = Modifier  
                            .padding(2.dp)  
                            .clip(RoundedCornerShape(6.dp))  
                            .then(  
                                if (isPicked) Modifier.border(  
                                    2.dp,  
                                    MaterialTheme.colorScheme.primary,  
                                    RoundedCornerShape(6.dp)  
                                ) else Modifier  
                            )  
                            .clickable { pickedUnit = uid },  
                        contentAlignment = Alignment.Center  
                    ) {  
                        UnitIcon(  
                            unitId = uid.toInt(),  
                            size = 48.dp  
                        )  
                    }  
                }  
            }  
  
            if (pickedUnit != 0L) {  
                Spacer(modifier = Modifier.height(8.dp))  
                val pickedNames = nameMap[(pickedUnit / 100).toInt()]  
                    ?.firstOrNull() ?: ""  
                Text(  
                    "已选角色: $pickedNames ($pickedUnit)",  
                    style = MaterialTheme.typography.bodySmall  
                )  
  
                // 普通EX / 会战EX 两个穿戴界面  
                TabRow(selectedTabIndex = tabIndex) {  
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 },  
                        text = { Text("普通EX") })  
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 },  
                        text = { Text("会战EX") })  
                }  
  
                val isCb = tabIndex == 1  
                val slots = if (isCb)  
                    data?.unitExSlots?.get(pickedUnit)?.second  
                else  
                    data?.unitExSlots?.get(pickedUnit)?.first  
  
                (slots ?: emptyList()).forEach { (slotNum, curSerial) ->  
                    Row(  
                        modifier = Modifier.fillMaxWidth(),  
                        verticalAlignment = Alignment.CenterVertically  
                    ) {  
                        Text("槽位$slotNum", modifier = Modifier.width(56.dp),  
                            style = MaterialTheme.typography.bodySmall)  
                        // 候选EX装备列表（会战EX排除受限serial）  
                        val candidates = data?.exInventory?.values  
                            ?.filter { !isCb || it.serialId !in data.cbExRestriction }  
                            ?.sortedByDescending { it.enhancementPt }  
                            ?: emptyList()  
                        Row(  
                            modifier = Modifier  
                                .weight(1f)  
                                .horizontalScroll(rememberScrollState())  
                        ) {  
                            // 卸除选项  
                            Column(  
                                horizontalAlignment = Alignment.CenterHorizontally,  
                                modifier = Modifier  
                                    .clickable {  
                                        pickedSerials = pickedSerials.toMutableMap()  
                                            .apply { put(slotNum, 0L) }  
                                    }  
                                    .padding(2.dp)  
                            ) {  
                                Box(  
                                    modifier = Modifier  
                                        .size(36.dp)  
                                        .border(  
                                            1.dp,  
                                            MaterialTheme.colorScheme.outline,  
                                            RoundedCornerShape(6.dp)  
                                        ),  
                                    contentAlignment = Alignment.Center  
                                ) {  
                                    Text("卸", style = MaterialTheme.typography.labelSmall)  
                                }  
                                Text(  
                                    if ((pickedSerials[slotNum] ?: curSerial) == 0L) "●" else " ",  
                                    style = MaterialTheme.typography.labelSmall,  
                                    color = MaterialTheme.colorScheme.primary  
                                )  
                            }  
                            candidates.take(30).forEach { ex ->  
                                val selected =  
                                    (pickedSerials[slotNum] ?: curSerial) == ex.serialId  
                                Column(  
                                    horizontalAlignment = Alignment.CenterHorizontally,  
                                    modifier = Modifier  
                                        .clickable {  
                                            pickedSerials = pickedSerials.toMutableMap()  
                                                .apply { put(slotNum, ex.serialId) }  
                                        }  
                                        .padding(2.dp)  
                                ) {  
                                    ExEquipIcon(ex.exEquipId, size = 36)  
                                    Text(  
                                        if (selected) "●" else "★${ex.rank}",  
                                        style = MaterialTheme.typography.labelSmall,  
                                        color = if (selected)  
                                            MaterialTheme.colorScheme.primary  
                                        else MaterialTheme.colorScheme.onSurfaceVariant  
                                    )  
                                }  
                            }  
                        }  
                    }  
                }  
  
                Spacer(modifier = Modifier.height(12.dp))  
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {  
                    Button(onClick = {  
                        // 先换支援角色  
                        viewModel.changeSupport(pickedUnit)  
                        // 再穿EX（有改动的槽位）  
                        val changes = (slots ?: emptyList()).mapNotNull { (s, cur) ->  
                            val t = pickedSerials[s]  
                            if (t != null && t != cur) s to t else null  
                        }  
                        if (changes.isNotEmpty()) {  
                            viewModel.equipEx(pickedUnit, changes, isCb)  
                        }  
                    }) { Text("确定更换") }  
                    OutlinedButton(onClick = { viewModel.cancelEdit() }) {  
                        Text("取消")  
                    }  
                }  
            }  
        }  
    }  
}