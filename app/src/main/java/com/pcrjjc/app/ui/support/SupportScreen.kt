package com.pcrjjc.app.ui.support  
  
import android.util.Log  
import androidx.compose.foundation.background  
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
import androidx.compose.foundation.layout.heightIn  
import androidx.compose.foundation.layout.padding  
import androidx.compose.foundation.layout.size  
import androidx.compose.foundation.layout.width  
import androidx.compose.foundation.lazy.grid.GridCells  
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid  
import androidx.compose.foundation.lazy.grid.items  
import androidx.compose.foundation.rememberScrollState  
import androidx.compose.foundation.shape.RoundedCornerShape  
import androidx.compose.foundation.verticalScroll  
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
import coil.compose.SubcomposeAsyncImage  
import coil.request.CachePolicy  
import coil.request.ImageRequest  
import com.pcrjjc.app.domain.SupportEngine.SupportData  
import com.pcrjjc.app.domain.SupportEngine.SupportSlot  
import com.pcrjjc.app.ui.detail.UnitIcon  
import com.pcrjjc.app.util.ExIconStorage  
import com.pcrjjc.app.util.ExMeta  
import java.io.File  
  
/**  
 * EX装备图标：三级回退  
 * 1. 本地缓存 {filesDir}/icons/ex/icon_icon_extra_equip_{id}.png  
 * 2. 内置 assets/ex_icons/icon_icon_extra_equip_{id}.png  
 * 3. 加载失败显示 "?" 占位（不再静默空白）  
 */  
@Composable  
fun ExEquipIcon(exEquipId: Int, modifier: Modifier = Modifier, size: Int = 40) {  
    val context = LocalContext.current  
    val localPath = ExIconStorage.getIconPath(context, exEquipId)  
    val assetUri = "file:///android_asset/ex_icons/icon_icon_extra_equip_${exEquipId}.png"  
  
    SubcomposeAsyncImage(  
        model = ImageRequest.Builder(context)  
            .data(if (localPath != null) File(localPath) else assetUri)  
            .crossfade(true)  
            .diskCachePolicy(CachePolicy.ENABLED)  
            .listener(  
                onError = { _, r ->  
                    Log.w("ExIcon", "ex=$exEquipId local=$localPath err=${r.throwable}")  
                }  
            )  
            .build(),  
        contentDescription = "EX装备 $exEquipId",  
        modifier = modifier  
            .size(size.dp)  
            .clip(RoundedCornerShape(6.dp)),  
        contentScale = ContentScale.Crop,  
        loading = {  
            Box(  
                modifier = Modifier  
                    .size(size.dp)  
                    .clip(RoundedCornerShape(6.dp))  
                    .background(MaterialTheme.colorScheme.surfaceVariant)  
            )  
        },  
        error = {  
            if (localPath != null) {  
                // 本地文件存在但解码失败时，回退内置 assets  
                AsyncImage(  
                    model = ImageRequest.Builder(context)  
                        .data(assetUri)  
                        .crossfade(true)  
                        .build(),  
                    contentDescription = "EX装备 $exEquipId",  
                    modifier = Modifier  
                        .size(size.dp)  
                        .clip(RoundedCornerShape(6.dp)),  
                    contentScale = ContentScale.Crop  
                )  
            } else {  
                Box(  
                    modifier = Modifier  
                        .size(size.dp)  
                        .clip(RoundedCornerShape(6.dp))  
                        .background(MaterialTheme.colorScheme.surfaceVariant),  
                    contentAlignment = Alignment.Center  
                ) {  
                    Text("?", style = MaterialTheme.typography.labelSmall)  
                }  
            }  
        }  
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
                Text(  
                    it, color = MaterialTheme.colorScheme.error,  
                    style = MaterialTheme.typography.bodySmall  
                )  
            }  
            uiState.message?.let {  
                Text(  
                    it, color = MaterialTheme.colorScheme.primary,  
                    style = MaterialTheme.typography.bodySmall  
                )  
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
                Text(  
                    "无", style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            }  
            slots.forEach { slot ->  
                Row(verticalAlignment = Alignment.CenterVertically) {  
                    if (slot.unitId == 0L) {  
                        // 空支援位：占位框，点击进入更换模式  
                        Box(  
                            modifier = Modifier  
                                .size(56.dp)  
                                .clip(RoundedCornerShape(6.dp))  
                                .clickable { onIconClick(slot) }  
                                .border(  
                                    1.dp,  
                                    MaterialTheme.colorScheme.outline,  
                                    RoundedCornerShape(6.dp)  
                                ),  
                            contentAlignment = Alignment.Center  
                        ) {  
                            Text(  
                                "+", style = MaterialTheme.typography.titleMedium,  
                                color = MaterialTheme.colorScheme.outline  
                            )  
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
                        Text(  
                            "点击选择支援角色",  
                            style = MaterialTheme.typography.bodySmall,  
                            color = MaterialTheme.colorScheme.onSurfaceVariant  
                        )  
                    } else if (worn.isEmpty()) {  
                        Text(  
                            "无EX装备", style = MaterialTheme.typography.bodySmall,  
                            color = MaterialTheme.colorScheme.onSurfaceVariant  
                        )  
                    } else {  
                        worn.forEach { ex ->  
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {  
                                ExEquipIcon(ex.exEquipId, size = 40)  
                                Text(  
                                    "★${ex.rank}",  
                                    style = MaterialTheme.typography.labelSmall  
                                )  
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
    // 每个槽位选的 serial_id（3个槽），0L = 卸下  
    var pickedSerials by remember {  
        mutableStateOf(mutableMapOf<Int, Long>())  
    }  
    var searchQuery by remember { mutableStateOf("") }  
  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
    ) {  
        Column(modifier = Modifier.padding(16.dp)) {  
            Text("更换支援角色", style = MaterialTheme.typography.titleMedium)  
            Spacer(modifier = Modifier.height(8.dp))  
  
            // ===== 花名册角色搜索 =====  
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
                columns = GridCells.Adaptive(56.dp),  
                modifier = Modifier  
                    .fillMaxWidth()  
                    .heightIn(max = 300.dp),  
                horizontalArrangement = Arrangement.spacedBy(4.dp),  
                verticalArrangement = Arrangement.spacedBy(4.dp)  
            ) {  
                items(unitIds) { uid ->  
                    val isPicked = pickedUnit == uid  
                    Column(  
                        horizontalAlignment = Alignment.CenterHorizontally,  
                        modifier = Modifier  
                            .clip(RoundedCornerShape(6.dp))  
                            .clickable { pickedUnit = uid }  
                            .then(  
                                if (isPicked) Modifier.border(  
                                    2.dp,  
                                    MaterialTheme.colorScheme.primary,  
                                    RoundedCornerShape(6.dp)  
                                ) else Modifier  
                            )  
                            .padding(2.dp)  
                    ) {  
                        UnitIcon(unitId = uid.toInt(), size = 48.dp)  
                        val bId = (uid / 100).toInt()  
                        nameMap[bId]?.firstOrNull()?.let {  
                            Text(it, style = MaterialTheme.typography.labelSmall)  
                        }  
                    }  
                }  
            }  
  
            if (pickedUnit != 0L) {  
                Spacer(modifier = Modifier.height(8.dp))  
                val pickedName = nameMap[(pickedUnit / 100).toInt()]?.firstOrNull()  
                Text(  
                    "已选角色: ${pickedName ?: ""}($pickedUnit)",  
                    style = MaterialTheme.typography.bodySmall  
                )  
  
                // 普通EX / 会战EX 两个穿戴界面  
                TabRow(selectedTabIndex = tabIndex) {  
                    Tab(  
                        selected = tabIndex == 0, onClick = { tabIndex = 0 },  
                        text = { Text("普通EX") })  
                    Tab(  
                        selected = tabIndex == 1, onClick = { tabIndex = 1 },  
                        text = { Text("会战EX") })  
                }  
  
                val isCb = tabIndex == 1  
                val slots = if (isCb)  
                    data?.unitExSlots?.get(pickedUnit)?.second  
                else  
                    data?.unitExSlots?.get(pickedUnit)?.first  
  
                // serial -> (unitId, slotNum) 反查持有者  
                val wearerMap = if (isCb) data?.cbExWearer else data?.exWearer  
  
                // ===== 每个槽位：左边槽位名，右边该槽类别匹配的可穿EX =====  
                (slots ?: emptyList()).forEach { (slotNum, curSerial) ->  
                    // 该槽位的类别（unit_ex_equipment_slot.slot_category_N）  
                    val slotCat = ExMeta.slotCategory(pickedUnit, slotNum)  
  
                    // 候选：只保留类别匹配的装备（对齐一键穿ex tools.py:1804-1813）  
                    // meta 数据缺失（slotCat/info 为 null）时不过滤兜底，防止全空  
                    val candidates = data?.exInventory?.values  
                        ?.filter { ex ->  
                            // 会战EX界面排除限制(CD中)装备  
                            !isCb || ex.serialId !in data.cbExRestriction  
                        }  
                        ?.filter { ex ->  
                            val info = ExMeta.equips[ex.exEquipId]  
                            slotCat == null || info == null || info.category == slotCat  
                        }  
                        ?.sortedWith(  
                            if (isCb) {  
                                // 会战EX: 会战专用优先 -> 稀有度 -> 强化pt  
                                compareByDescending<com.pcrjjc.app.domain.SupportEngine.ExEquipItem> {  
                                    ExMeta.equips[it.exEquipId]?.clanBattleFlag ?: 0  
                                }.thenByDescending {  
                                    ExMeta.equips[it.exEquipId]?.rarity ?: 0  
                                }.thenByDescending { it.enhancementPt }  
                            } else {  
                                // 普通EX: 稀有度 -> 星数 -> ex_id  
                                compareByDescending<com.pcrjjc.app.domain.SupportEngine.ExEquipItem> {  
                                    ExMeta.equips[it.exEquipId]?.rarity ?: 0  
                                }.thenByDescending {  
                                    ExMeta.starFromPt(it.exEquipId, it.enhancementPt)  
                                }.thenByDescending { it.exEquipId }  
                            }  
                        )  
                        ?: emptyList()  
  
                    Row(  
                        modifier = Modifier.fillMaxWidth(),  
                        verticalAlignment = Alignment.CenterVertically  
                    ) {  
                        Text(  
                            "槽位$slotNum\n类别${slotCat ?: "?"}",  
                            modifier = Modifier.width(56.dp),  
                            style = MaterialTheme.typography.bodySmall  
                        )  
                        Row(  
                            modifier = Modifier  
                                .weight(1f)  
                                .horizontalScroll(rememberScrollState())  
                        ) {  
                            // "卸" 选项：serial_id=0 卸下该槽  
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
                                ) { Text("卸", style = MaterialTheme.typography.labelSmall) }  
                                Text(  
                                    " ",  
                                    style = MaterialTheme.typography.labelSmall  
                                )  
                            }  
                            if (candidates.isEmpty()) {  
                                Text(  
                                    if (slotCat != null && ExMeta.equips.isNotEmpty())  
                                        "类别$slotCat 无可穿EX"  
                                    else "无可用装备",  
                                    style = MaterialTheme.typography.labelSmall,  
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,  
                                    modifier = Modifier.align(Alignment.CenterVertically)  
                                )  
                            }  
                            candidates.take(30).forEach { ex ->  
                                val info = ExMeta.equips[ex.exEquipId]  
                                // 真实星数：enhancement_pt 反推（对齐 get_ex_equip_star_from_pt）  
                                val star = ExMeta.starFromPt(ex.exEquipId, ex.enhancementPt)  
                                val selected =  
                                    (pickedSerials[slotNum] ?: curSerial) == ex.serialId  
                                val wearer = wearerMap?.get(ex.serialId)  
                                val wearerText = when {  
                                    wearer == null -> "空闲"  
                                    wearer.first == pickedUnit -> "已穿"  
                                    else -> nameMap[(wearer.first / 100).toInt()]  
                                        ?.firstOrNull() ?: "${wearer.first}"  
                                }  
                                Column(  
                                    horizontalAlignment = Alignment.CenterHorizontally,  
                                    modifier = Modifier  
                                        .clickable {  
                                            pickedSerials = pickedSerials.toMutableMap()  
                                                .apply { put(slotNum, ex.serialId) }  
                                        }  
                                        .border(  
                                            if (selected) 2.dp else 0.dp,  
                                            if (selected) MaterialTheme.colorScheme.primary  
                                            else androidx.compose.ui.graphics.Color.Transparent,  
                                            RoundedCornerShape(6.dp)  
                                        )  
                                        .padding(2.dp)  
                                ) {  
                                    ExEquipIcon(ex.exEquipId, size = 36)  
                                    // 名字★星数  
                                    Text(  
                                        "${info?.name ?: ex.exEquipId}★$star",  
                                        style = MaterialTheme.typography.labelSmall,  
                                        color = if (selected)  
                                            MaterialTheme.colorScheme.primary  
                                        else MaterialTheme.colorScheme.onSurfaceVariant  
                                    )  
                                    // 属性串：HP+X/物攻+Y（default/max 按星插值）  
                                    Text(  
                                        ExMeta.attrStr(ex.exEquipId, star),  
                                        style = MaterialTheme.typography.labelSmall,  
                                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                                    )  
                                    // 持有者：空闲 / 已穿 / 别人名字（标红）  
                                    Text(  
                                        wearerText,  
                                        style = MaterialTheme.typography.labelSmall,  
                                        color = if (wearer != null && wearer.first != pickedUnit)  
                                            MaterialTheme.colorScheme.error  
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