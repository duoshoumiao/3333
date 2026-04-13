package com.pcrjjc.app.ui.fortnightly  
  
import androidx.compose.foundation.background  
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
import androidx.compose.foundation.layout.padding  
import androidx.compose.foundation.layout.width  
import androidx.compose.foundation.lazy.LazyColumn  
import androidx.compose.foundation.lazy.items  
import androidx.compose.foundation.shape.RoundedCornerShape  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.filled.ArrowBack  
import androidx.compose.material.icons.filled.Refresh  
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
import androidx.compose.material3.Text  
import androidx.compose.material3.TopAppBar  
import androidx.compose.material3.TopAppBarDefaults  
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.LaunchedEffect  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.runtime.remember  
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
  
    LaunchedEffect(uiState.message) {  
        uiState.message?.let {  
            snackbarHostState.showSnackbar(it)  
            viewModel.clearMessage()  
        }  
    }  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = { Text("公主连结半月刊") },  
                colors = TopAppBarDefaults.topAppBarColors(  
                    containerColor = MaterialTheme.colorScheme.primaryContainer,  
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer  
                ),  
                navigationIcon = {  
                    IconButton(onClick = onNavigateBack) {  
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")  
                    }  
                },  
                actions = {  
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
                            activities = activities  
                        )  
                    }  
  
                    item { Spacer(modifier = Modifier.height(16.dp)) }  
                }  
            }  
        }  
    }  
}  
  
@OptIn(ExperimentalLayoutApi::class)  
@Composable  
private fun CategoryCard(  
    category: String,  
    categoryColor: Color,  
    activities: List<ClassifiedActivity>  
) {  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
    ) {  
        Column {  
            // 分类标题栏 — 彩色背景  
            Box(  
                modifier = Modifier  
                    .fillMaxWidth()  
                    .background(categoryColor)  
                    .padding(horizontal = 16.dp, vertical = 10.dp)  
            ) {  
                Text(  
                    text = category,  
                    style = MaterialTheme.typography.titleMedium,  
                    fontWeight = FontWeight.Bold,  
                    color = Color.White  
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
            activity.isFuture -> Color(0xFFE65100)    // 橙色 — 开始倒计时  
            else -> Color(0xFF2E7D32)                 // 绿色 — 剩余时间  
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