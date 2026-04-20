package com.pcrjjc.app.ui.room  
  
import androidx.compose.foundation.clickable  
import androidx.compose.foundation.layout.*  
import androidx.compose.foundation.lazy.LazyColumn  
import androidx.compose.foundation.lazy.items  
import androidx.compose.foundation.text.KeyboardOptions  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
import androidx.compose.material.icons.filled.*  
import androidx.compose.material3.*  
import androidx.compose.runtime.*  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.text.input.KeyboardType  
import androidx.compose.ui.text.input.PasswordVisualTransformation  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
import com.pcrjjc.app.data.local.entity.Room  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun RoomScreen(  
    viewModel: RoomViewModel = hiltViewModel(),  
    onNavigateBack: () -> Unit,  
    onNavigateToChat: (roomId: String, playerQq: String, roomName: String) -> Unit = { _, _, _ -> }  
) {  
    val uiState by viewModel.uiState.collectAsState()  
  
    var showCreateDialog by remember { mutableStateOf(false) }  
    var showJoinDialog by remember { mutableStateOf(false) }  
    var selectedRoom by remember { mutableStateOf<Room?>(null) }  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = { Text("房间") },  
                navigationIcon = {  
                    IconButton(onClick = onNavigateBack) {  
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")  
                    }  
                },  
                actions = {  
                    IconButton(onClick = { viewModel.refreshRoomList() }) {  
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")  
                    }  
                }  
            )  
        },  
        floatingActionButton = {  
            Column(  
                horizontalAlignment = Alignment.End,  
                verticalArrangement = Arrangement.spacedBy(8.dp)  
            ) {  
                SmallFloatingActionButton(  
                    onClick = { showJoinDialog = true },  
                    containerColor = MaterialTheme.colorScheme.secondaryContainer  
                ) {  
                    Icon(Icons.Default.Login, contentDescription = "加入房间")  
                }  
                FloatingActionButton(  
                    onClick = { showCreateDialog = true }  
                ) {  
                    Icon(Icons.Default.Add, contentDescription = "创建房间")  
                }  
            }  
        }  
    ) { paddingValues ->  
        Column(  
            modifier = Modifier  
                .fillMaxSize()  
                .padding(paddingValues)  
        ) {  
            var selectedTab by remember { mutableIntStateOf(0) }  
            TabRow(selectedTabIndex = selectedTab) {  
                Tab(  
                    selected = selectedTab == 0,  
                    onClick = { selectedTab = 0 },  
                    text = { Text("查找房间") },  
                    icon = { Icon(Icons.Default.Search, contentDescription = null) }  
                )  
                Tab(  
                    selected = selectedTab == 1,  
                    onClick = { selectedTab = 1 },  
                    text = { Text("创建房间") },  
                    icon = { Icon(Icons.Default.Add, contentDescription = null) }  
                )  
            }  
  
            when (selectedTab) {  
                0 -> {  
                    if (uiState.isLoading) {  
                        Box(  
                            modifier = Modifier.fillMaxSize(),  
                            contentAlignment = Alignment.Center  
                        ) {  
                            CircularProgressIndicator()  
                        }  
                    } else if (uiState.error != null) {  
                        Box(  
                            modifier = Modifier.fillMaxSize(),  
                            contentAlignment = Alignment.Center  
                        ) {  
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {  
                                Text(  
                                    text = uiState.error!!,  
                                    color = MaterialTheme.colorScheme.error  
                                )  
                                Spacer(modifier = Modifier.height(8.dp))  
                                Button(onClick = { viewModel.refreshRoomList() }) {  
                                    Text("重试")  
                                }  
                            }  
                        }  
                    } else if (uiState.rooms.isEmpty()) {  
                        Box(  
                            modifier = Modifier.fillMaxSize(),  
                            contentAlignment = Alignment.Center  
                        ) {  
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {  
                                Icon(  
                                    Icons.Default.MeetingRoom,  
                                    contentDescription = null,  
                                    modifier = Modifier.size(64.dp),  
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant  
                                )  
                                Spacer(modifier = Modifier.height(8.dp))  
                                Text(  
                                    text = "暂无房间",  
                                    style = MaterialTheme.typography.bodyLarge,  
                                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                                )  
                                Spacer(modifier = Modifier.height(4.dp))  
                                Text(  
                                    text = "点击右下角按钮创建或加入房间",  
                                    style = MaterialTheme.typography.bodySmall,  
                                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                                )  
                            }  
                        }  
                    } else {  
                        LazyColumn(  
                            modifier = Modifier.fillMaxSize(),  
                            contentPadding = PaddingValues(16.dp),  
                            verticalArrangement = Arrangement.spacedBy(8.dp)  
                        ) {  
                            items(uiState.rooms, key = { it.roomId }) { room ->  
                                RoomCard(  
                                    room = room,  
                                    onClick = {  
                                        selectedRoom = room  
                                        showJoinDialog = true  
                                    }  
                                )  
                            }  
                        }  
                    }  
                }  
                1 -> {  
                    CreateRoomContent(  
                        isCreating = uiState.isCreating,  
                        initialQq = uiState.savedQq,  
                        onCreateRoom = { name, password, qq ->  
                            viewModel.createRoom(name, password, qq)  
                        }  
                    )  
                }  
            }  
        }  
    }  
  
    // 加入房间对话框  
    if (showJoinDialog && selectedRoom != null) {  
        JoinRoomDialog(  
            room = selectedRoom!!,  
            initialQq = uiState.savedQq,  
            initialPassword = viewModel.getCachedPassword(selectedRoom!!.roomId) ?: "",  
            onDismiss = {  
                showJoinDialog = false  
                selectedRoom = null  
            },  
            onJoin = { password, qq ->  
                viewModel.joinRoom(selectedRoom!!.roomId, password, qq)  
                showJoinDialog = false  
                selectedRoom = null  
            }  
        )  
    } else if (showJoinDialog) {  
        QuickJoinDialog(  
            initialQq = uiState.savedQq,  
            onDismiss = { showJoinDialog = false },  
            onJoin = { roomId, password, qq ->  
                viewModel.joinRoom(roomId, password, qq)  
                showJoinDialog = false  
            }  
        )  
    }  
  
    // 创建成功对话框 → 进入房间  
    if (uiState.createdRoom != null) {  
        AlertDialog(  
            onDismissRequest = { viewModel.clearCreatedRoom() },  
            title = { Text("房间创建成功") },  
            text = {  
                Column {  
                    Text("房间号: ${uiState.createdRoom!!.roomId}")  
                    Text("房间名: ${uiState.createdRoom!!.roomName}")  
                    if (!uiState.createdRoom!!.password.isNullOrBlank()) {  
                        Text("密码: ${uiState.createdRoom!!.password}")  
                    }  
                }  
            },  
            confirmButton = {  
                Button(onClick = {  
                    val room = uiState.createdRoom!!  
                    val qq = uiState.currentPlayerQq ?: uiState.savedQq  
                    viewModel.clearCreatedRoom()  
                    onNavigateToChat(room.roomId, qq, room.roomName)  
                }) {  
                    Text("进入房间")  
                }  
            },  
            dismissButton = {  
                TextButton(onClick = { viewModel.clearCreatedRoom() }) {  
                    Text("稍后加入")  
                }  
            }  
        )  
    }  
  
    // 加入成功 → 自动导航到聊天  
    LaunchedEffect(uiState.joinedRoom) {  
        if (uiState.joinedRoom != null) {  
            val room = uiState.joinedRoom!!  
            val qq = uiState.currentPlayerQq ?: uiState.savedQq  
            viewModel.clearJoinedRoom()  
            onNavigateToChat(room.roomId, qq, room.roomName)  
        }  
    }  
}
@Composable  
private fun RoomCard(  
    room: Room,  
    onClick: () -> Unit  
) {  
    Card(  
        modifier = Modifier  
            .fillMaxWidth()  
            .clickable(onClick = onClick),  
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
    ) {  
        Row(  
            modifier = Modifier  
                .fillMaxWidth()  
                .padding(16.dp),  
            horizontalArrangement = Arrangement.SpaceBetween,  
            verticalAlignment = Alignment.CenterVertically  
        ) {  
            Column(modifier = Modifier.weight(1f)) {  
                Text(  
                    text = room.roomName,  
                    style = MaterialTheme.typography.titleMedium  
                )  
                Spacer(modifier = Modifier.height(4.dp))  
                Text(  
                    text = "房主: ${room.hostName}",  
                    style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
                Text(  
                    text = "玩家: ${room.playerCount}/${room.maxPlayers}",  
                    style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            }  
            Row(  
                horizontalArrangement = Arrangement.spacedBy(8.dp),  
                verticalAlignment = Alignment.CenterVertically  
            ) {  
                if (room.password != null) {  
                    Icon(  
                        Icons.Default.Lock,  
                        contentDescription = "需要密码",  
                        tint = MaterialTheme.colorScheme.primary,  
                        modifier = Modifier.size(20.dp)  
                    )  
                }  
                Icon(  
                    Icons.Default.ChevronRight,  
                    contentDescription = "加入",  
                    tint = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            }  
        }  
    }  
}  
  
@Composable  
private fun CreateRoomContent(  
    isCreating: Boolean,  
    initialQq: String = "",  
    onCreateRoom: (String, String?, String) -> Unit  
) {  
    var roomName by remember { mutableStateOf("") }  
    var password by remember { mutableStateOf("") }  
    var hasPassword by remember { mutableStateOf(false) }  
    var qq by remember { mutableStateOf(initialQq) }  
  
    LaunchedEffect(initialQq) {  
        if (qq.isBlank() && initialQq.isNotBlank()) {  
            qq = initialQq  
        }  
    }  
  
    Column(  
        modifier = Modifier  
            .fillMaxSize()  
            .padding(16.dp),  
        verticalArrangement = Arrangement.spacedBy(16.dp)  
    ) {  
        OutlinedTextField(  
            value = roomName,  
            onValueChange = { roomName = it },  
            label = { Text("房间名称") },  
            placeholder = { Text("例如：竞技场车队") },  
            singleLine = true,  
            modifier = Modifier.fillMaxWidth()  
        )  
  
        Row(  
            modifier = Modifier.fillMaxWidth(),  
            verticalAlignment = Alignment.CenterVertically  
        ) {  
            Checkbox(  
                checked = hasPassword,  
                onCheckedChange = { hasPassword = it }  
            )  
            Text("设置密码")  
        }  
  
        if (hasPassword) {  
            OutlinedTextField(  
                value = password,  
                onValueChange = { password = it },  
                label = { Text("房间密码") },  
                singleLine = true,  
                visualTransformation = PasswordVisualTransformation(),  
                modifier = Modifier.fillMaxWidth()  
            )  
        }  
  
        OutlinedTextField(  
            value = qq,  
            onValueChange = { qq = it },  
            label = { Text("您的QQ号") },  
            placeholder = { Text("用于加入房间验证") },  
            singleLine = true,  
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  
            modifier = Modifier.fillMaxWidth()  
        )  
  
        Spacer(modifier = Modifier.weight(1f))  
  
        Button(  
            onClick = {  
                if (roomName.isNotBlank() && qq.isNotBlank()) {  
                    onCreateRoom(roomName, if (hasPassword && password.isNotBlank()) password else null, qq)  
                }  
            },  
            modifier = Modifier.fillMaxWidth(),  
            enabled = !isCreating && roomName.isNotBlank() && qq.isNotBlank()  
        ) {  
            if (isCreating) {  
                CircularProgressIndicator(  
                    modifier = Modifier.size(20.dp),  
                    strokeWidth = 2.dp  
                )  
                Spacer(modifier = Modifier.width(8.dp))  
            }  
            Text(if (isCreating) "创建中..." else "创建房间")  
        }  
    }  
}  
  
@Composable  
private fun JoinRoomDialog(  
    room: Room,  
    initialQq: String = "",  
    initialPassword: String = "",  
    onDismiss: () -> Unit,  
    onJoin: (String?, String) -> Unit  
) {  
    var password by remember { mutableStateOf(initialPassword) }  
    var qq by remember { mutableStateOf(initialQq) }  
    var showError by remember { mutableStateOf(false) }  
  
    LaunchedEffect(initialQq) {  
        if (qq.isBlank() && initialQq.isNotBlank()) {  
            qq = initialQq  
        }  
    }  
  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text("加入房间") },  
        text = {  
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {  
                Text("房间: ${room.roomName}")  
                Text("房主: ${room.hostName}")  
                Text("玩家: ${room.playerCount}/${room.maxPlayers}")  
  
                if (room.password != null) {  
                    OutlinedTextField(  
                        value = password,  
                        onValueChange = {  
                            password = it  
                            showError = false  
                        },  
                        label = { Text("房间密码") },  
                        singleLine = true,  
                        visualTransformation = PasswordVisualTransformation(),  
                        isError = showError,  
                        modifier = Modifier.fillMaxWidth()  
                    )  
                }  
  
                OutlinedTextField(  
                    value = qq,  
                    onValueChange = {  
                        qq = it  
                        showError = false  
                    },  
                    label = { Text("您的QQ号") },  
                    singleLine = true,  
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  
                    isError = showError,  
                    modifier = Modifier.fillMaxWidth()  
                )  
  
                if (showError) {  
                    Text(  
                        text = "请填写必要信息",  
                        color = MaterialTheme.colorScheme.error,  
                        style = MaterialTheme.typography.bodySmall  
                    )  
                }  
            }  
        },  
        confirmButton = {  
            Button(  
                onClick = {  
                    if (qq.isBlank() || (room.password != null && password.isBlank())) {  
                        showError = true  
                        return@Button  
                    }  
                    onJoin(if (password.isNotBlank()) password else null, qq)  
                }  
            ) {  
                Text("加入")  
            }  
        },  
        dismissButton = {  
            TextButton(onClick = onDismiss) {  
                Text("取消")  
            }  
        }  
    )  
}  
  
@Composable  
private fun QuickJoinDialog(  
    initialQq: String = "",  
    onDismiss: () -> Unit,  
    onJoin: (String, String?, String) -> Unit  
) {  
    var roomId by remember { mutableStateOf("") }  
    var password by remember { mutableStateOf("") }  
    var qq by remember { mutableStateOf(initialQq) }  
  
    LaunchedEffect(initialQq) {  
        if (qq.isBlank() && initialQq.isNotBlank()) {  
            qq = initialQq  
        }  
    }  
  
    AlertDialog(  
        onDismissRequest = onDismiss,  
        title = { Text("加入房间") },  
        text = {  
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {  
                OutlinedTextField(  
                    value = roomId,  
                    onValueChange = { roomId = it },  
                    label = { Text("房间号") },  
                    singleLine = true,  
                    modifier = Modifier.fillMaxWidth()  
                )  
  
                OutlinedTextField(  
                    value = password,  
                    onValueChange = { password = it },  
                    label = { Text("密码（可选）") },  
                    singleLine = true,  
                    visualTransformation = PasswordVisualTransformation(),  
                    modifier = Modifier.fillMaxWidth()  
                )  
  
                OutlinedTextField(  
                    value = qq,  
                    onValueChange = { qq = it },  
                    label = { Text("您的QQ号") },  
                    singleLine = true,  
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  
                    modifier = Modifier.fillMaxWidth()  
                )  
            }  
        },  
        confirmButton = {  
            Button(  
                onClick = {  
                    if (roomId.isNotBlank() && qq.isNotBlank()) {  
                        onJoin(roomId, if (password.isNotBlank()) password else null, qq)  
                    }  
                },  
                enabled = roomId.isNotBlank() && qq.isNotBlank()  
            ) {  
                Text("加入")  
            }  
        },  
        dismissButton = {  
            TextButton(onClick = onDismiss) {  
                Text("取消")  
            }  
        }  
    )  
}