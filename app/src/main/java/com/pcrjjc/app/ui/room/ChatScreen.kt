package com.pcrjjc.app.ui.room  
  
import androidx.compose.foundation.ExperimentalFoundationApi  
import androidx.compose.foundation.layout.*  
import androidx.compose.foundation.lazy.LazyColumn  
import androidx.compose.foundation.lazy.items  
import androidx.compose.foundation.lazy.rememberLazyListState  
import androidx.compose.foundation.pager.HorizontalPager  
import androidx.compose.foundation.pager.rememberPagerState  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
import androidx.compose.material.icons.automirrored.filled.Send  
import androidx.compose.material.icons.filled.DeleteForever  
import androidx.compose.material.icons.filled.SportsEsports  
import androidx.compose.material3.*  
import androidx.compose.runtime.*  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.text.style.TextOverflow  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
import com.pcrjjc.app.data.local.entity.ChatMessage  
import kotlinx.coroutines.launch  
import java.text.SimpleDateFormat  
import java.util.Date  
import java.util.Locale  
  
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)  
@Composable  
fun ChatScreen(  
    viewModel: ChatViewModel = hiltViewModel(),  
    onNavigateBack: () -> Unit  
) {  
    val uiState by viewModel.uiState.collectAsState()  
    val pagerState = rememberPagerState(initialPage = 0) { 2 }  
    val coroutineScope = rememberCoroutineScope()  
  
    HorizontalPager(  
        state = pagerState,  
        modifier = Modifier.fillMaxSize()
    ) { page ->  
        when (page) {  
            0 -> ChatPageContent(  
                uiState = uiState,  
                viewModel = viewModel,  
                onNavigateBack = onNavigateBack,  
                onNavigateToBoss = {  
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }  
                }  
            )  
            1 -> ClanBattleScreen(  
                onNavigateBack = {  
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }  
                }  
            )  
        }  
    }  
}  
  
// ==================== 原有聊天页面内容（提取为独立 Composable） ====================  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
private fun ChatPageContent(  
    uiState: ChatUiState,  
    viewModel: ChatViewModel,  
    onNavigateBack: () -> Unit,  
    onNavigateToBoss: () -> Unit  
) {  
    var inputText by remember { mutableStateOf("") }  
    val listState = rememberLazyListState()  
    var showDismissDialog by remember { mutableStateOf(false) }  
  
    // 新消息到达时自动滚动到底部  
    LaunchedEffect(uiState.messages.size) {  
        if (uiState.messages.isNotEmpty()) {  
            listState.animateScrollToItem(uiState.messages.size - 1)  
        }  
    }  
  
    // 房主解散房间后自动退出  
    LaunchedEffect(uiState.isDismissed) {  
        if (uiState.isDismissed) {  
            onNavigateBack()  
        }  
    }  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = {  
                    Column {  
                        Text(uiState.roomName.ifBlank { "聊天" })  
                        Text(  
                            text = "房间号: ${uiState.roomId}  ← 左滑查看Boss状态",  
                            style = MaterialTheme.typography.bodySmall,  
                            color = MaterialTheme.colorScheme.onSurfaceVariant,  
                            maxLines = 1,  
                            overflow = TextOverflow.Ellipsis  
                        )  
                    }  
                },  
                navigationIcon = {  
                    IconButton(onClick = {  
                        viewModel.leaveRoom()  
                        onNavigateBack()  
                    }) {  
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "离开房间")  
                    }  
                },  
                actions = {  
                    // Boss状态按钮（也可点击进入）  
                    IconButton(onClick = onNavigateToBoss) {  
                        Icon(  
                            Icons.Default.SportsEsports,  
                            contentDescription = "Boss状态",  
                            tint = MaterialTheme.colorScheme.primary  
                        )  
                    }  
                    if (uiState.isHost) {  
                        IconButton(onClick = { showDismissDialog = true }) {  
                            Icon(  
                                Icons.Default.DeleteForever,  
                                contentDescription = "解散房间",  
                                tint = MaterialTheme.colorScheme.error  
                            )  
                        }  
                    }  
                }  
            )  
        },  
        bottomBar = {  
            Surface(tonalElevation = 3.dp) {  
                Row(  
                    modifier = Modifier  
                        .fillMaxWidth()  
                        .padding(horizontal = 8.dp, vertical = 8.dp),  
                    verticalAlignment = Alignment.CenterVertically,  
                    horizontalArrangement = Arrangement.spacedBy(8.dp)  
                ) {  
                    OutlinedTextField(  
                        value = inputText,  
                        onValueChange = { inputText = it },  
                        modifier = Modifier.weight(1f),  
                        placeholder = { Text("输入消息...") },  
                        singleLine = false,  
                        maxLines = 4  
                    )  
                    IconButton(  
                        onClick = {  
                            if (inputText.isNotBlank()) {  
                                viewModel.sendMessage(inputText)  
                                inputText = ""  
                            }  
                        },  
                        enabled = inputText.isNotBlank() && !uiState.isSending  
                    ) {  
                        if (uiState.isSending) {  
                            CircularProgressIndicator(  
                                modifier = Modifier.size(24.dp),  
                                strokeWidth = 2.dp  
                            )  
                        } else {  
                            Icon(  
                                Icons.AutoMirrored.Filled.Send,  
                                contentDescription = "发送",  
                                tint = if (inputText.isNotBlank())  
                                    MaterialTheme.colorScheme.primary  
                                else  
                                    MaterialTheme.colorScheme.onSurfaceVariant  
                            )  
                        }  
                    }  
                }  
            }  
        }  
    ) { paddingValues ->  
        if (uiState.messages.isEmpty() && !uiState.isLoading) {  
            Box(  
                modifier = Modifier  
                    .fillMaxSize()  
                    .padding(paddingValues),  
                contentAlignment = Alignment.Center  
            ) {  
                Text(  
                    text = "暂无消息，发送第一条消息吧",  
                    style = MaterialTheme.typography.bodyLarge,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            }  
        } else {  
            LazyColumn(  
                modifier = Modifier  
                    .fillMaxSize()  
                    .padding(paddingValues),  
                state = listState,  
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),  
                verticalArrangement = Arrangement.spacedBy(4.dp)  
            ) {  
                items(uiState.messages, key = { it.id }) { message ->  
                    ChatMessageItem(  
                        message = message,  
                        isMe = message.senderQq == uiState.playerQq  
                    )  
                }  
            }  
        }  
    }  
  
    // 错误提示  
    if (uiState.error != null) {  
        Snackbar(  
            modifier = Modifier.padding(16.dp),  
            action = {  
                TextButton(onClick = { viewModel.clearError() }) {  
                    Text("关闭")  
                }  
            }  
        ) {  
            Text(uiState.error!!)  
        }  
    }  
  
    if (showDismissDialog) {  
        AlertDialog(  
            onDismissRequest = { showDismissDialog = false },  
            title = { Text("解散房间") },  
            text = { Text("解散后所有玩家将被移出房间，此操作不可撤销。确定解散吗？") },  
            confirmButton = {  
                TextButton(  
                    onClick = {  
                        showDismissDialog = false  
                        viewModel.dismissRoom()  
                    }  
                ) {  
                    Text("确定解散", color = MaterialTheme.colorScheme.error)  
                }  
            },  
            dismissButton = {  
                TextButton(onClick = { showDismissDialog = false }) {  
                    Text("取消")  
                }  
            }  
        )  
    }  
}  
  
// ==================== 聊天消息项（保持不变） ====================  
  
@Composable  
private fun ChatMessageItem(  
    message: ChatMessage,  
    isMe: Boolean  
) {  
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }  
  
    Column(  
        modifier = Modifier.fillMaxWidth(),  
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start  
    ) {  
        Row(  
            horizontalArrangement = Arrangement.spacedBy(4.dp),  
            verticalAlignment = Alignment.CenterVertically  
        ) {  
            Text(  
                text = if (isMe) "我" else message.senderName,  
                style = MaterialTheme.typography.labelSmall,  
                color = MaterialTheme.colorScheme.onSurfaceVariant  
            )  
            Text(  
                text = timeFormat.format(Date(message.timestamp)),  
                style = MaterialTheme.typography.labelSmall,  
                color = MaterialTheme.colorScheme.outline  
            )  
        }  
  
        Spacer(modifier = Modifier.height(2.dp))  
  
        Surface(  
            shape = MaterialTheme.shapes.medium,  
            color = if (isMe)  
                MaterialTheme.colorScheme.primaryContainer  
            else  
                MaterialTheme.colorScheme.surfaceVariant,  
            tonalElevation = 1.dp  
        ) {  
            Text(  
                text = message.content,  
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),  
                style = MaterialTheme.typography.bodyMedium,  
                color = if (isMe)  
                    MaterialTheme.colorScheme.onPrimaryContainer  
                else  
                    MaterialTheme.colorScheme.onSurfaceVariant  
            )  
        }  
    }  
}