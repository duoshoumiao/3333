package com.pcrjjc.app.ui.fortnightly  

import androidx.compose.foundation.background  
import androidx.compose.foundation.clickable  
import androidx.compose.foundation.layout.Arrangement  
import androidx.compose.foundation.layout.Box  
import androidx.compose.foundation.layout.Column  
import androidx.compose.foundation.layout.ExperimentalLayoutApi  
import androidx.compose.foundation.layout.FlowRow  
import androidx.compose.foundation.layout.Row  
import androidx.compose.foundation.layout.Spacer  
import androidx.compose.foundation.layout.fillMaxSize  
import androidx.compose.foundation.layout.fillMaxWidth  
import androidx.compose.foundation.layout.height  
import androidx.compose.foundation.layout.heightIn  
import androidx.compose.foundation.layout.padding  
import androidx.compose.foundation.layout.size  
import androidx.compose.foundation.layout.width  
import androidx.compose.foundation.lazy.LazyColumn  
import androidx.compose.foundation.lazy.items  
import androidx.compose.foundation.rememberScrollState  
import androidx.compose.foundation.shape.CircleShape  
import androidx.compose.foundation.shape.RoundedCornerShape  
import androidx.compose.foundation.verticalScroll  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.filled.ArrowBack  
import androidx.compose.material.icons.filled.Check  
import androidx.compose.material.icons.filled.Notifications  
import androidx.compose.material.icons.filled.NotificationsActive  
import androidx.compose.material.icons.filled.NotificationsOff  
import androidx.compose.material.icons.filled.Refresh  
import androidx.compose.material3.AlertDialog  
import androidx.compose.material3.Card  
import androidx.compose.material3.CardDefaults  
import androidx.compose.material3.CircularProgressIndicator  
import androidx.compose.material3.ExperimentalMaterial3Api  
import androidx.compose.material3.HorizontalDivider  
import androidx.compose.material3.Icon  
import androidx.compose.material3.IconButton  
import androidx.compose.material3.MaterialTheme  
import androidx.compose.material3.Scaffold  
import androidx.compose.material3.SnackbarHost  
import androidx.compose.material3.SnackbarHostState  
import androidx.compose.material3.Switch  
import androidx.compose.material3.Text  
import androidx.compose.material3.TextButton  
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.LaunchedEffect  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.runtime.mutableStateOf  
import androidx.compose.runtime.remember  
import androidx.compose.runtime.setValue  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.draw.clip  
import androidx.compose.ui.graphics.Color  
import androidx.compose.ui.text.font.FontWeight  
import androidx.compose.ui.text.style.TextAlign  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
import com.pcrjjc.app.data.model.ClassifiedActivity  
import com.pcrjjc.app.domain.ActivityClassifier  
import com.pcrjjc.app.ui.components.ImageTopAppBar  
import com.pcrjjc.app.ui.detail.UnitIcon  
import java.text.SimpleDateFormat  
import java.util.Date  
import java.util.Locale  

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)  
@Composable  
fun FortnightlyScreen(  
    viewModel: FortnightlyViewModel = hiltViewModel(),  
    onNavigateBack: () -> Unit  
) {  
    val uiState by viewModel.uiState.collectAsState()  
    val snackbarHostState = remember { SnackbarHostState() }  
    var showSubscriptionPanel by remember { mutableStateOf(false) }  

    LaunchedEffect(uiState.message) {  
        uiState.message?.let {  
            snackbarHostState.showSnackbar(it)  
            viewModel.clearMessage()  
        }  
    }  

    Scaffold(  
        topBar = {  
            ImageTopAppBar(  
                title = { Text("公主连结半月刊") },  
                navigationIcon = {  
                    IconButton(onClick = onNavigateBack) {  
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")  
                    }  
                },  
                actions = {  
                    // 订阅管理入口（带订阅数量角标）  
                    IconButton(onClick = { showSubscriptionPanel = true }) {  
                        Box {  
                            Icon(  
                                imageVector = if (uiState.pushEnabled && uiState.subscriptionCount > 0) {  
                                    Icons.Default.NotificationsActive  
                                } else {  
                                    Icons.Default.NotificationsOff  
                                },  
                                contentDescription = "订阅管理"  
                            )  
                            if (uiState.subscriptionCount > 0) {  
                                Box(  
                                    modifier = Modifier  
                                        .align(Alignment.TopEnd)  
                                        .size(16.dp)  
                                        .clip(CircleShape)  
                                        .background(Color(0xFFD32F2F)),  
                                    contentAlignment = Alignment.Center  
                                ) {  
                                    Text(  
                                        text = uiState.subscriptionCount.toString(),  
                                        style = MaterialTheme.typography.labelSmall,  
                                        color = Color.White  
                                    )  
                                }  
                            }  
                        }  
                    }  

                    if (uiState.isUpdating) {  
                        CircularProgressIndicator(  
                            modifier = Modifier  
                                .padding(end = 12.dp)  
                                .height(24.dp)  
                                .width(24.dp),  
                            strokeWidth = 2.dp  
                        )  
                    } else {  
                        IconButton(onClick = { viewModel.updateData() }) {  
                            Icon(Icons.Default.Refresh, contentDescription = "更新半月刊")  
                        }  
                    }  
                }  
            )  
        },  
        snackbarHost = { SnackbarHost(snackbarHostState) }  
    ) { paddingValues ->  
        when {  
            uiState.isLoading -> {  
                Box(  
                    modifier = Modifier  
                        .fillMaxSize()  
                        .padding(paddingValues),  
                    contentAlignment = Alignment.Center  
                ) {  
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {  
                        CircularProgressIndicator()  
                        Spacer(modifier = Modifier.height(16.dp))  
                        Text(  
                            text = "正在加载半月刊数据...",  
                            style = MaterialTheme.typography.bodyMedium,  
                            color = MaterialTheme.colorScheme.onSurfaceVariant  
                        )  
                    }  
                }  
            }  

            uiState.errorMessage != null -> {  
                Box(  
                    modifier = Modifier  
                        .fillMaxSize()  
                        .padding(paddingValues),  
                    contentAlignment = Alignment.Center  
                ) {  
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {  
                        Text(  
                            text = uiState.errorMessage ?: "未知错误",  
                            style = MaterialTheme.typography.bodyLarge,  
                            color = MaterialTheme.colorScheme.error  
                        )  
                        Spacer(modifier = Modifier.height(8.dp))  
                        Text(  
                            text = "点击右上角刷新按钮重试",  
                            style = MaterialTheme.typography.bodyMedium,  
                            color = MaterialTheme.colorScheme.onSurfaceVariant  
                        )  
                    }  
                }  
            }  

            uiState.activities.isEmpty() -> {  
                Box(  
                    modifier = Modifier  
                        .fillMaxSize()  
                        .padding(paddingValues),  
                    contentAlignment = Alignment.Center  
                ) {  
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {  
                        Text(  
                            text = "当前没有进行中和即将开始的活动",  
                            style = MaterialTheme.typography.titleMedium,  
                            color = MaterialTheme.colorScheme.onSurfaceVariant  
                        )  
                        Spacer(modifier = Modifier.height(8.dp))  
                        Text(  
                            text = "点击右上角刷新按钮更新数据",  
                            style = MaterialTheme.typography.bodyMedium,  
                            color = MaterialTheme.colorScheme.onSurfaceVariant  
                        )  
                    }  
                }  
            }  

            else -> {  
                LazyColumn(  
                    modifier = Modifier  
                        .fillMaxSize()  
                        .padding(paddingValues)  
                        .padding(horizontal = 12.dp),  
                    verticalArrangement = Arrangement.spacedBy(12.dp)  
                ) {  
                    item {  
                        Spacer(modifier = Modifier.height(8.dp))  
                        val dateStr = SimpleDateFormat(  
                            "yyyy年M月d日", Locale.CHINA  
                        ).format(Date())  
                        Text(  
                            text = dateStr,  
                            style = MaterialTheme.typography.bodyMedium,  
                            color = MaterialTheme.colorScheme.onSurfaceVariant,  
                            modifier = Modifier.fillMaxWidth(),  
                            textAlign = TextAlign.Center  
                        )  
                        Spacer(modifier = Modifier.height(4.dp))  
                    }  

                    // 订阅状态提示条  
                    item {  
                        SubscriptionHintBar(  
                            subscriptionCount = uiState.subscriptionCount,  
                            pushEnabled = uiState.pushEnabled,  
                            onClick = { showSubscriptionPanel = true }  
                        )  
                    }  

                    val orderedCategories = ActivityClassifier.categoryOrder.filter {  
                        uiState.activities.containsKey(it)  
                    }  

                    items(orderedCategories) { category ->  
                        val activities = uiState.activities[category] ?: return@items  
                        val categoryColor = ActivityClassifier.categoryColors[category]  
                            ?: Color.Gray  

                        CategoryCard(  
                            category = category,  
                            categoryColor = categoryColor,  
                            activities = activities,  
                            isSubscribed = category in uiState.subscribedCategories,  
                            onToggleSubscribe = { viewModel.toggleSubscription(category) }  
                        )  
                    }  

                    item { Spacer(modifier = Modifier.height(16.dp)) }  
                }  
            }  
        }  
    }  

    if (showSubscriptionPanel) {  
        SubscriptionDialog(  
            uiState = uiState,  
            onToggleCategory = { viewModel.toggleSubscription(it) },  
            onSubscribeAll = { viewModel.subscribeAll() },  
            onClearAll = { viewModel.clearSubscriptions() },  
            onTogglePush = { viewModel.setPushEnabled(it) },  
            onCheckNow = { viewModel.checkNow() },  
            onDismiss = { showSubscriptionPanel = false }  
        )  
    }  
}  

/** 列表顶部的订阅状态提示条 */  
@Composable  
private fun SubscriptionHintBar(  
    subscriptionCount: Int,  
    pushEnabled: Boolean,  
    onClick: () -> Unit  
) {  
    val text = when {  
        !pushEnabled -> "新活动推送已关闭 · 点击开启"  
        subscriptionCount == 0 -> "尚未订阅任何类别 · 点击选择感兴趣的活动"  
        else -> "已订阅 $subscriptionCount 个类别，新活动开始时会推送提醒"  
    }  
    Card(  
        modifier = Modifier  
            .fillMaxWidth()  
            .clickable(onClick = onClick),  
        colors = CardDefaults.cardColors(  
            containerColor = MaterialTheme.colorScheme.secondaryContainer  
        ),  
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)  
    ) {  
        Row(  
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),  
            verticalAlignment = Alignment.CenterVertically  
        ) {  
            Icon(  
                imageVector = if (pushEnabled) {  
                    Icons.Default.NotificationsActive  
                } else {  
                    Icons.Default.NotificationsOff  
                },  
                contentDescription = null,  
                modifier = Modifier.size(18.dp),  
                tint = MaterialTheme.colorScheme.onSecondaryContainer  
            )  
            Spacer(modifier = Modifier.width(8.dp))  
            Text(  
                text = text,  
                style = MaterialTheme.typography.bodySmall,  
                color = MaterialTheme.colorScheme.onSecondaryContainer,  
                modifier = Modifier.weight(1f)  
            )  
        }  
    }  
}  

@OptIn(ExperimentalLayoutApi::class)  
@Composable  
private fun CategoryCard(  
    category: String,  
    categoryColor: Color,  
    activities: List<ClassifiedActivity>,  
    isSubscribed: Boolean,  
    onToggleSubscribe: () -> Unit  
) {  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
    ) {  
        Column {  
            // 分类标题栏 — 彩色背景 + 订阅按钮  
            Row(  
                modifier = Modifier  
                    .fillMaxWidth()  
                    .background(categoryColor)  
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),  
                verticalAlignment = Alignment.CenterVertically  
            ) {  
                Text(  
                    text = category,  
                    style = MaterialTheme.typography.titleMedium,  
                    fontWeight = FontWeight.Bold,  
                    color = Color.White,  
                    modifier = Modifier.weight(1f)  
                )  
                SubscribeChip(  
                    isSubscribed = isSubscribed,  
                    onToggle = onToggleSubscribe  
                )  
            }  

            // 活动列表  
            activities.forEachIndexed { index, activity ->  
                ActivityItem(activity = activity)  
                if (index < activities.lastIndex) {  
                    HorizontalDivider(  
                        modifier = Modifier.padding(horizontal = 16.dp),  
                        color = MaterialTheme.colorScheme.outlineVariant  
                    )  
                }  
            }  
        }  
    }  
}  

/** 分类标题栏右侧的「订阅 / 已订阅」小按钮 */  
@Composable  
private fun SubscribeChip(isSubscribed: Boolean, onToggle: () -> Unit) {  
    Row(  
        modifier = Modifier  
            .clip(RoundedCornerShape(50))  
            .background(  
                if (isSubscribed) Color(0x33FFFFFF) else Color(0x22000000)  
            )  
            .clickable(onClick = onToggle)  
            .padding(horizontal = 10.dp, vertical = 5.dp),  
        verticalAlignment = Alignment.CenterVertically  
    ) {  
        Icon(  
            imageVector = if (isSubscribed) Icons.Default.Check else Icons.Default.Notifications,  
            contentDescription = null,  
            modifier = Modifier.size(14.dp),  
            tint = Color.White  
        )  
        Spacer(modifier = Modifier.width(4.dp))  
        Text(  
            text = if (isSubscribed) "已订阅" else "订阅",  
            style = MaterialTheme.typography.labelMedium,  
            color = Color.White,  
            fontWeight = FontWeight.Medium  
        )  
    }  
}  

/** 订阅管理弹窗：批量订阅、推送开关、立即检查 */  
@Composable  
private fun SubscriptionDialog(  
    uiState: FortnightlyUiState,  
    onToggleCategory: (String) -> Unit,  
    onSubscribeAll: () -> Unit,  
    onClearAll: () -> Unit,  
    onTogglePush: (Boolean) -> Unit,  
    onCheckNow: () -> Unit,  
    onDismiss: () -> Unit  
) {  
    val categories = ActivityClassifier.categoryOrder  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text("半月刊订阅") },  
        text = {  
            Column(  
                modifier = Modifier  
                    .fillMaxWidth()  
                    .heightIn(max = 420.dp)  
                    .verticalScroll(rememberScrollState()),  
                verticalArrangement = Arrangement.spacedBy(4.dp)  
            ) {  
                // 总开关  
                Row(  
                    modifier = Modifier.fillMaxWidth(),  
                    verticalAlignment = Alignment.CenterVertically  
                ) {  
                    Column(modifier = Modifier.weight(1f)) {  
                        Text("新活动推送", style = MaterialTheme.typography.bodyMedium)  
                        Text(  
                            text = "订阅类别有新活动时发送系统通知",  
                            style = MaterialTheme.typography.labelSmall,  
                            color = MaterialTheme.colorScheme.onSurfaceVariant  
                        )  
                    }  
                    Switch(  
                        checked = uiState.pushEnabled,  
                        onCheckedChange = onTogglePush  
                    )  
                }  

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)  

                Text(  
                    text = "选择要订阅的类别",  
                    style = MaterialTheme.typography.labelMedium,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  

                // 类别多选列表  
                categories.forEach { category ->  
                    val subscribed = category in uiState.subscribedCategories  
                    val color = ActivityClassifier.categoryColors[category] ?: Color.Gray  
                    Row(  
                        modifier = Modifier  
                            .fillMaxWidth()  
                            .clip(RoundedCornerShape(8.dp))  
                            .clickable { onToggleCategory(category) }  
                            .padding(horizontal = 6.dp, vertical = 8.dp),  
                        verticalAlignment = Alignment.CenterVertically  
                    ) {  
                        Box(  
                            modifier = Modifier  
                                .size(10.dp)  
                                .clip(CircleShape)  
                                .background(color)  
                        )  
                        Spacer(modifier = Modifier.width(10.dp))  
                        Text(  
                            text = category,  
                            style = MaterialTheme.typography.bodyMedium,  
                            modifier = Modifier.weight(1f)  
                        )  
                        if (subscribed) {  
                            Icon(  
                                imageVector = Icons.Default.Check,  
                                contentDescription = "已订阅",  
                                tint = MaterialTheme.colorScheme.primary,  
                                modifier = Modifier.size(18.dp)  
                            )  
                        }  
                    }  
                }  

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)  

                Row(  
                    modifier = Modifier.fillMaxWidth(),  
                    horizontalArrangement = Arrangement.SpaceBetween  
                ) {  
                    TextButton(onClick = onSubscribeAll) { Text("全选") }  
                    TextButton(onClick = onClearAll) { Text("清空") }  
                    TextButton(onClick = onCheckNow) { Text("立即检查") }  
                }  
            }  
        },  
        confirmButton = {  
            TextButton(onClick = onDismiss) { Text("完成") }  
        }  
    )  
}  

@OptIn(ExperimentalLayoutApi::class)  
@Composable  
private fun ActivityItem(activity: ClassifiedActivity) {  
    Column(  
        modifier = Modifier  
            .fillMaxWidth()  
            .padding(horizontal = 16.dp, vertical = 10.dp)  
    ) {  
        // 时间状态  
        val timeColor = when {  
            activity.isEnding -> Color(0xFFD32F2F)   // 红色 — 即将结束  
            activity.isFuture -> Color(0xFFE65100)   // 橙色 — 开始倒计时  
            else -> Color(0xFF2E7D32)                // 绿色 — 剩余时间  
        }  
        Text(  
            text = activity.timeStatus,  
            style = MaterialTheme.typography.bodySmall,  
            color = timeColor,  
            fontWeight = FontWeight.Medium  
        )  

        Spacer(modifier = Modifier.height(4.dp))  

        // 活动名称  
        Text(  
            text = activity.subName,  
            style = MaterialTheme.typography.bodyMedium,  
            color = MaterialTheme.colorScheme.onSurface  
        )  

        // 角色头像行  
        if (activity.characterIds.isNotEmpty()) {  
            Spacer(modifier = Modifier.height(6.dp))  
            FlowRow(  
                horizontalArrangement = Arrangement.spacedBy(4.dp),  
                verticalArrangement = Arrangement.spacedBy(4.dp)  
            ) {  
                for (charId in activity.characterIds) {  
                    // charId 是 baseId（如 1001），UnitIcon 需要 unitId（如 100101）  
                    // CharaIconUtil.getLocalIconPath 内部用 unitId / 100 还原 baseId  
                    UnitIcon(  
                        unitId = charId * 100 + 1,  
                        size = 40.dp,  
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))  
                    )  
                }  
            }  
        }  
    }  
}  
