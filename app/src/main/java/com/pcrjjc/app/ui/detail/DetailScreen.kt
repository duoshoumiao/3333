package com.pcrjjc.app.ui.detail  
  
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
import androidx.compose.foundation.rememberScrollState  
import androidx.compose.foundation.verticalScroll  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
import androidx.compose.material.icons.filled.Refresh  
import androidx.compose.material3.Button  
import androidx.compose.material3.Card  
import androidx.compose.material3.CardDefaults  
import androidx.compose.material3.CircularProgressIndicator  
import androidx.compose.material3.ExperimentalMaterial3Api  
import androidx.compose.material3.HorizontalDivider  
import androidx.compose.material3.Icon  
import androidx.compose.material3.IconButton  
import androidx.compose.material3.MaterialTheme  
import androidx.compose.material3.Scaffold  
import androidx.compose.material3.Text  
import androidx.compose.material3.TopAppBar  
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.text.font.FontWeight  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
import java.text.SimpleDateFormat  
import java.util.Date  
import java.util.Locale  
  
// ==================== 工具函数 ====================  
  
private fun formatDateTime(timestamp: Long): String {  
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())  
    return sdf.format(Date(timestamp * 1000))  
}  
  
private fun formatDate(timestamp: Long): String {  
    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())  
    return sdf.format(Date(timestamp * 1000))  
}  
  
private fun talentQuestText(clearCount: Int): String {  
    if (clearCount <= 0) return "未通关"  
    val chapter = (clearCount - 1) / 10 + 1  
    var queNum = clearCount % 10  
    if (queNum == 0) queNum = 10  
    return "$chapter-$queNum"  
}  
  
@Suppress("UNCHECKED_CAST")  
private fun getSupportUnitInfo(unit: Map<String, Any?>): Triple<String, String, String> {  
    val unitData = unit["unit_data"] as? Map<String, Any?> ?: return Triple("?", "?", "?")  
    val unitId = (unitData["id"] as? Number)?.toInt() ?: 0  
    val unitLevel = (unitData["unit_level"] as? Number)?.toInt() ?: 0  
    val promotionLevel = (unitData["promotion_level"] as? Number)?.toInt() ?: 0  
    return Triple("$unitId", "$unitLevel", "$promotionLevel")  
}  
  
// ==================== 主屏幕 ====================  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun DetailScreen(  
    bindId: Int,  
    viewModel: DetailViewModel = hiltViewModel(),  
    onNavigateBack: () -> Unit  
) {  
    val uiState by viewModel.uiState.collectAsState()  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = { Text("详细资料") },  
                navigationIcon = {  
                    IconButton(onClick = onNavigateBack) {  
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")  
                    }  
                },  
                actions = {  
                    // 刷新按钮  
                    IconButton(onClick = { viewModel.retry() }) {  
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")  
                    }  
                }  
            )  
        }  
    ) { paddingValues ->  
        when {  
            uiState.isLoading -> {  
                Box(  
                    modifier = Modifier.fillMaxSize().padding(paddingValues),  
                    contentAlignment = Alignment.Center  
                ) {  
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {  
                        CircularProgressIndicator()  
                        Spacer(modifier = Modifier.height(16.dp))  
                        Text("加载中...")  
                    }  
                }  
            }  
            uiState.errorMessage != null -> {  
                Box(  
                    modifier = Modifier.fillMaxSize().padding(paddingValues),  
                    contentAlignment = Alignment.Center  
                ) {  
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {  
                        Text(text = uiState.errorMessage!!, color = MaterialTheme.colorScheme.error)  
                        Spacer(modifier = Modifier.height(16.dp))  
                        Button(onClick = { viewModel.retry() }) { Text("重试") }  
                    }  
                }  
            }  
            else -> {  
                DetailContent(uiState)  
            }  
        }  
    }  
}  
  
// ==================== 内容布局 ====================  
  
@Composable  
private fun DetailContent(uiState: DetailUiState) {  
    Column(  
        modifier = Modifier  
            .fillMaxSize()  
            .padding(16.dp)  
            .verticalScroll(rememberScrollState()),  
        verticalArrangement = Arrangement.spacedBy(16.dp)  
    ) {  
        PersonalInfoCard(uiState)  
        ArenaInfoRow(uiState)  
        FavoriteUnitCard(uiState)  
        AdventureCard(uiState)  
        TowerCard(uiState)  
        SupportCard(uiState)  
        TalentCard(uiState)  
    }  
}  
  
// ==================== 个人信息卡片 ====================  
  
@Composable  
private fun PersonalInfoCard(uiState: DetailUiState) {  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)  
    ) {  
        Column(modifier = Modifier.padding(16.dp)) {  
            Text(text = uiState.userName, style = MaterialTheme.typography.headlineSmall)  
            Spacer(modifier = Modifier.height(8.dp))  
            InfoRow("UID", "${uiState.bind?.pcrid}")  
            if (uiState.viewerId.isNotEmpty()) {  
                InfoRow("Viewer ID", uiState.viewerId)  
            }  
            InfoRow("等级", "${uiState.teamLevel}")  
            InfoRow("全角色战力", "${uiState.totalPower}")  
            InfoRow("角色数", "${uiState.unitNum}")  
            if (uiState.clanName.isNotEmpty()) {  
                InfoRow("公会", uiState.clanName)  
            }  
            InfoRow("服务器", uiState.serverName)  
            if (uiState.lastLoginTime > 0) {  
                InfoRow("最后登录", formatDateTime(uiState.lastLoginTime))  
            }  
            if (uiState.userComment.isNotEmpty()) {  
                Spacer(modifier = Modifier.height(4.dp))  
                HorizontalDivider()  
                Spacer(modifier = Modifier.height(4.dp))  
                Text(  
                    text = "个人签名",  
                    style = MaterialTheme.typography.labelMedium,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
                Text(text = uiState.userComment, style = MaterialTheme.typography.bodyMedium)  
            }  
        }  
    }  
}  
  
// ==================== 竞技场信息 ====================  
  
@Composable  
private fun ArenaInfoRow(uiState: DetailUiState) {  
    Row(  
        modifier = Modifier.fillMaxWidth(),  
        horizontalArrangement = Arrangement.spacedBy(16.dp)  
    ) {  
        Card(  
            modifier = Modifier.weight(1f),  
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)  
        ) {  
            Column(  
                modifier = Modifier.padding(16.dp),  
                horizontalAlignment = Alignment.CenterHorizontally  
            ) {  
                Text("竞技场", style = MaterialTheme.typography.titleSmall)  
                Spacer(modifier = Modifier.height(8.dp))  
                Text(text = "${uiState.arenaRank}", style = MaterialTheme.typography.headlineMedium)  
                if (uiState.arenaGroup > 0) {  
                    Text(  
                        text = "${uiState.arenaGroup}场",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onPrimaryContainer  
                    )  
                }  
                if (uiState.arenaTime > 0) {  
                    Text(  
                        text = formatDate(uiState.arenaTime),  
                        style = MaterialTheme.typography.labelSmall,  
                        color = MaterialTheme.colorScheme.onPrimaryContainer  
                    )  
                }  
            }  
        }  
        Card(  
            modifier = Modifier.weight(1f),  
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)  
        ) {  
            Column(  
                modifier = Modifier.padding(16.dp),  
                horizontalAlignment = Alignment.CenterHorizontally  
            ) {  
                Text("公主竞技场", style = MaterialTheme.typography.titleSmall)  
                Spacer(modifier = Modifier.height(8.dp))  
                Text(text = "${uiState.grandArenaRank}", style = MaterialTheme.typography.headlineMedium)  
                if (uiState.grandArenaGroup > 0) {  
                    Text(  
                        text = "${uiState.grandArenaGroup}场",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSecondaryContainer  
                    )  
                }  
                if (uiState.grandArenaTime > 0) {  
                    Text(  
                        text = formatDate(uiState.grandArenaTime),  
                        style = MaterialTheme.typography.labelSmall,  
                        color = MaterialTheme.colorScheme.onSecondaryContainer  
                    )  
                }  
            }  
        }  
    }  
}  
  
// ==================== 看板角色卡片（已加头像） ====================  
  
@Composable  
private fun FavoriteUnitCard(uiState: DetailUiState) {  
    if (uiState.favoriteUnit.isEmpty()) return  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
    ) {  
        Column(modifier = Modifier.padding(16.dp)) {  
            Text("看板角色", style = MaterialTheme.typography.titleMedium)  
            Spacer(modifier = Modifier.height(8.dp))  
            val unitId = (uiState.favoriteUnit["id"] as? Number)?.toInt() ?: 0  
            val unitLevel = (uiState.favoriteUnit["unit_level"] as? Number)?.toInt() ?: 0  
            val unitRarity = (uiState.favoriteUnit["unit_rarity"] as? Number)?.toInt() ?: 0  
            Row(  
                verticalAlignment = Alignment.CenterVertically  
            ) {  
                UnitIcon(unitId = unitId, size = 64.dp)  
                Spacer(modifier = Modifier.width(12.dp))  
                Column(modifier = Modifier.weight(1f)) {  
                    InfoRow("角色ID", "$unitId")  
                    InfoRow("等级", "$unitLevel")  
                    InfoRow("星级", "$unitRarity")  
                }  
            }  
        }  
    }  
}  
  
// ==================== 冒险经历 ====================  
  
@Composable  
private fun AdventureCard(uiState: DetailUiState) {  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
    ) {  
        Column(modifier = Modifier.padding(16.dp)) {  
            Text("冒险经历", style = MaterialTheme.typography.titleMedium)  
            Spacer(modifier = Modifier.height(8.dp))  
            if (uiState.normalQuest.isNotEmpty()) {  
                InfoRow("普通关卡", uiState.normalQuest)  
            }  
            if (uiState.hardQuest.isNotEmpty() || uiState.veryHardQuest.isNotEmpty()) {  
                InfoRow("困难 / 超难", "H${uiState.hardQuest} / VH${uiState.veryHardQuest}")  
            }  
            InfoRow("已开启剧情", "${uiState.openStoryNum}")  
        }  
    }  
}  
  
// ==================== 露娜塔 ====================  
  
@Composable  
private fun TowerCard(uiState: DetailUiState) {  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
    ) {  
        Column(modifier = Modifier.padding(16.dp)) {  
            Text("露娜塔", style = MaterialTheme.typography.titleMedium)  
            Spacer(modifier = Modifier.height(8.dp))  
            InfoRow("已通关层数", "${uiState.towerClearedFloorNum}阶")  
            InfoRow("EX通关数", "${uiState.towerClearedExQuestCount}")  
        }  
    }  
}  
  
// ==================== 支援角色卡片 ====================  
  
@Composable  
private fun SupportCard(uiState: DetailUiState) {  
    if (uiState.friendSupportUnits.isEmpty() && uiState.clanSupportUnits.isEmpty()) return  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
    ) {  
        Column(modifier = Modifier.padding(16.dp)) {  
            Text("支援角色", style = MaterialTheme.typography.titleMedium)  
            Spacer(modifier = Modifier.height(8.dp))  
            if (uiState.friendSupportUnits.isNotEmpty()) {  
                SectionLabel("好友支援")  
                uiState.friendSupportUnits.forEach { unit ->  
                    SupportUnitRow(unit)  
                }  
                Spacer(modifier = Modifier.height(8.dp))  
            }  
            if (uiState.clanSupportUnits.isNotEmpty()) {  
                val dungeonUnits = uiState.clanSupportUnits.filter {  
                    val pos = (it["position"] as? Number)?.toInt() ?: 0  
                    pos in 1..2  
                }  
                val clanBattleUnits = uiState.clanSupportUnits.filter {  
                    val pos = (it["position"] as? Number)?.toInt() ?: 0  
                    pos in 3..4  
                }  
                if (dungeonUnits.isNotEmpty()) {  
                    SectionLabel("地下城支援")  
                    dungeonUnits.forEach { unit -> SupportUnitRow(unit) }  
                    Spacer(modifier = Modifier.height(8.dp))  
                }  
                if (clanBattleUnits.isNotEmpty()) {  
                    SectionLabel("战队支援")  
                    clanBattleUnits.forEach { unit -> SupportUnitRow(unit) }  
                }  
            }  
        }  
    }  
}  
  
// ==================== 深域进度 ====================  
  
@Composable  
private fun TalentCard(uiState: DetailUiState) {  
    if (uiState.talentQuest.isEmpty() && uiState.knightExp <= 0) return  
    val talentNames = mapOf(1 to "火属性", 2 to "水属性", 3 to "风属性", 4 to "光属性", 5 to "暗属性")  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
    ) {  
        Column(modifier = Modifier.padding(16.dp)) {  
            Text("深域进度", style = MaterialTheme.typography.titleMedium)  
            Spacer(modifier = Modifier.height(8.dp))  
            uiState.talentQuest.forEach { talent ->  
                val talentId = (talent["talent_id"] as? Number)?.toInt() ?: 0  
                val clearCount = (talent["clear_count"] as? Number)?.toInt() ?: 0  
                val name = talentNames[talentId] ?: "未知"  
                InfoRow(name, talentQuestText(clearCount))  
            }  
            if (uiState.knightExp > 0) {  
                Spacer(modifier = Modifier.height(4.dp))  
                HorizontalDivider()  
                Spacer(modifier = Modifier.height(4.dp))  
                InfoRow("公主骑士经验", "${uiState.knightExp}")  
                InfoRow("公主骑士RANK", "${uiState.knightRank}")  
            }  
        }  
    }  
}  
  
// ==================== 通用组件 ====================  
  
@Composable  
private fun SectionLabel(text: String) {  
    Text(  
        text = text,  
        style = MaterialTheme.typography.labelLarge,  
        fontWeight = FontWeight.Bold  
    )  
    Spacer(modifier = Modifier.height(4.dp))  
}  
  
@Composable  
private fun SupportUnitRow(unit: Map<String, Any?>) {  
    val (id, level, rank) = getSupportUnitInfo(unit)  
    val pos = (unit["position"] as? Number)?.toInt() ?: 0  
    val unitId = id.toIntOrNull() ?: 0  
    Row(  
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),  
        verticalAlignment = Alignment.CenterVertically  
    ) {  
        UnitIcon(unitId = unitId, size = 40.dp)  
        Spacer(modifier = Modifier.width(8.dp))  
        Column(modifier = Modifier.weight(1f)) {  
            Text(  
                text = "位置$pos  ID:$id",  
                style = MaterialTheme.typography.bodyMedium,  
                color = MaterialTheme.colorScheme.onSurfaceVariant  
            )  
        }  
        Text(text = "Lv.$level  Rank$rank", style = MaterialTheme.typography.bodyMedium)  
    }  
}  
  
@Composable  
private fun InfoRow(label: String, value: String) {  
    Row(  
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),  
        horizontalArrangement = Arrangement.SpaceBetween  
    ) {  
        Text(  
            text = label,  
            style = MaterialTheme.typography.bodyMedium,  
            color = MaterialTheme.colorScheme.onSurfaceVariant  
        )  
        Text(text = value, style = MaterialTheme.typography.bodyMedium)  
    }  
}