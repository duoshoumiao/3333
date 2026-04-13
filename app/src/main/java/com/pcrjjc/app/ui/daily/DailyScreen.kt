package com.pcrjjc.app.ui.daily  
  
import androidx.compose.foundation.clickable  
import androidx.compose.foundation.layout.Arrangement  
import androidx.compose.foundation.layout.Column  
import androidx.compose.foundation.layout.Spacer  
import androidx.compose.foundation.layout.fillMaxSize  
import androidx.compose.foundation.layout.fillMaxWidth  
import androidx.compose.foundation.layout.height  
import androidx.compose.foundation.layout.padding  
import androidx.compose.foundation.layout.size  
import androidx.compose.foundation.layout.width  
import androidx.compose.foundation.lazy.LazyColumn  
import androidx.compose.foundation.lazy.items  
import androidx.compose.foundation.text.KeyboardOptions  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
import androidx.compose.material3.Button  
import androidx.compose.material3.Card  
import androidx.compose.material3.CardDefaults  
import androidx.compose.material3.CircularProgressIndicator  
import androidx.compose.material3.ExperimentalMaterial3Api  
import androidx.compose.material3.HorizontalDivider  
import androidx.compose.material3.Icon  
import androidx.compose.material3.IconButton  
import androidx.compose.material3.MaterialTheme  
import androidx.compose.material3.OutlinedTextField  
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
import androidx.compose.ui.text.font.FontWeight  
import androidx.compose.ui.text.input.KeyboardType  
import androidx.compose.ui.text.input.PasswordVisualTransformation  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun DailyScreen(  
    viewModel: DailyViewModel = hiltViewModel(),  
    onNavigateBack: () -> Unit  
) {  
    val uiState by viewModel.uiState.collectAsState()  
    val snackbarHostState = remember { SnackbarHostState() }  
  
    LaunchedEffect(uiState.errorMessage) {  
        uiState.errorMessage?.let {  
            snackbarHostState.showSnackbar(it)  
            viewModel.clearError()  
        }  
    }  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = {  
                    Text(  
                        when (uiState.phase) {  
                            DailyPhase.LOGIN -> "清日常 - 登录"  
                            DailyPhase.ACCOUNTS -> "清日常 - 选择账号"  
                            DailyPhase.COMMANDS -> "清日常 - ${uiState.selectedAccount ?: "指令"}"  
                        }  
                    )  
                },  
                navigationIcon = {  
                    IconButton(onClick = {  
                        when (uiState.phase) {  
                            DailyPhase.LOGIN -> onNavigateBack()  
                            else -> viewModel.goBack()  
                        }  
                    }) {  
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")  
                    }  
                },  
                colors = TopAppBarDefaults.topAppBarColors(  
                    containerColor = MaterialTheme.colorScheme.primaryContainer,  
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer  
                )  
            )  
        },  
        snackbarHost = { SnackbarHost(snackbarHostState) }  
    ) { paddingValues ->  
        when (uiState.phase) {  
            DailyPhase.LOGIN -> LoginContent(  
                uiState = uiState,  
                onQqChanged = viewModel::onQqChanged,  
                onPasswordChanged = viewModel::onPasswordChanged,  
                onLogin = viewModel::login,  
                modifier = Modifier.padding(paddingValues)  
            )  
            DailyPhase.ACCOUNTS -> AccountsContent(  
                uiState = uiState,  
                onSelectAccount = viewModel::selectAccount,  
                modifier = Modifier.padding(paddingValues)  
            )  
            DailyPhase.COMMANDS -> CommandsContent(  
                selectedAccount = uiState.selectedAccount ?: "",  
                modifier = Modifier.padding(paddingValues)  
            )  
        }  
    }  
}  
  
// ==================== 登录界面 ====================  
  
@Composable  
private fun LoginContent(  
    uiState: DailyUiState,  
    onQqChanged: (String) -> Unit,  
    onPasswordChanged: (String) -> Unit,  
    onLogin: () -> Unit,  
    modifier: Modifier = Modifier  
) {  
    Column(  
        modifier = modifier  
            .fillMaxSize()  
            .padding(24.dp),  
        horizontalAlignment = Alignment.CenterHorizontally,  
        verticalArrangement = Arrangement.Center  
    ) {  
        Text(  
            text = "清日常登录",  
            style = MaterialTheme.typography.headlineMedium,  
            fontWeight = FontWeight.Bold  
        )  
        Spacer(modifier = Modifier.height(8.dp))  
        Text(  
            text = "使用QQ账号密码登录清日常服务",  
            style = MaterialTheme.typography.bodyMedium,  
            color = MaterialTheme.colorScheme.onSurfaceVariant  
        )  
  
        if (uiState.serverUrl.isNullOrBlank()) {  
            Spacer(modifier = Modifier.height(24.dp))  
            Card(  
                modifier = Modifier.fillMaxWidth(),  
                colors = CardDefaults.cardColors(  
                    containerColor = MaterialTheme.colorScheme.errorContainer  
                )  
            ) {  
                Text(  
                    text = "请先在「设置」中配置服务器地址（IP和端口）",  
                    modifier = Modifier.padding(16.dp),  
                    style = MaterialTheme.typography.bodyMedium,  
                    color = MaterialTheme.colorScheme.onErrorContainer  
                )  
            }  
            return  
        }  
  
        Spacer(modifier = Modifier.height(32.dp))  
  
        OutlinedTextField(  
            value = uiState.qqInput,  
            onValueChange = onQqChanged,  
            label = { Text("QQ号") },  
            placeholder = { Text("请输入QQ号") },  
            singleLine = true,  
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  
            modifier = Modifier.fillMaxWidth(),  
            enabled = !uiState.isLoading  
        )  
  
        Spacer(modifier = Modifier.height(16.dp))  
  
        OutlinedTextField(  
            value = uiState.passwordInput,  
            onValueChange = onPasswordChanged,  
            label = { Text("密码") },  
            placeholder = { Text("请输入密码") },  
            singleLine = true,  
            visualTransformation = PasswordVisualTransformation(),  
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),  
            modifier = Modifier.fillMaxWidth(),  
            enabled = !uiState.isLoading  
        )  
  
        Spacer(modifier = Modifier.height(24.dp))  
  
        Button(  
            onClick = onLogin,  
            modifier = Modifier.fillMaxWidth(),  
            enabled = !uiState.isLoading  
                    && uiState.qqInput.isNotBlank()  
                    && uiState.passwordInput.isNotBlank()  
        ) {  
            if (uiState.isLoading) {  
                CircularProgressIndicator(  
                    modifier = Modifier.size(20.dp),  
                    strokeWidth = 2.dp,  
                    color = MaterialTheme.colorScheme.onPrimary  
                )  
                Spacer(modifier = Modifier.width(8.dp))  
                Text("登录中...")  
            } else {  
                Text("登录")  
            }  
        }  
    }  
}  
  
// ==================== 账号列表界面 ====================  
  
@Composable  
private fun AccountsContent(  
    uiState: DailyUiState,  
    onSelectAccount: (String) -> Unit,  
    modifier: Modifier = Modifier  
) {  
    Column(  
        modifier = modifier  
            .fillMaxSize()  
            .padding(16.dp)  
    ) {  
        Text(  
            text = "选择账号",  
            style = MaterialTheme.typography.titleLarge,  
            fontWeight = FontWeight.Bold  
        )  
        Spacer(modifier = Modifier.height(4.dp))  
        Text(  
            text = "共 ${uiState.accounts.size} 个账号",  
            style = MaterialTheme.typography.bodyMedium,  
            color = MaterialTheme.colorScheme.onSurfaceVariant  
        )  
        Spacer(modifier = Modifier.height(16.dp))  
  
        if (uiState.accounts.isEmpty()) {  
            Column(  
                modifier = Modifier.fillMaxSize(),  
                horizontalAlignment = Alignment.CenterHorizontally,  
                verticalArrangement = Arrangement.Center  
            ) {  
                Text(  
                    text = "该QQ下暂无账号",  
                    style = MaterialTheme.typography.bodyLarge,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            }  
        } else {  
            LazyColumn(  
                verticalArrangement = Arrangement.spacedBy(8.dp)  
            ) {  
                items(uiState.accounts) { alias ->  
                    Card(  
                        modifier = Modifier  
                            .fillMaxWidth()  
                            .clickable { onSelectAccount(alias) },  
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)  
                    ) {  
                        Column(modifier = Modifier.padding(16.dp)) {  
                            Text(  
                                text = alias,  
                                style = MaterialTheme.typography.titleMedium  
                            )  
                            Spacer(modifier = Modifier.height(4.dp))  
                            Text(  
                                text = "点击查看可用指令",  
                                style = MaterialTheme.typography.bodySmall,  
                                color = MaterialTheme.colorScheme.onSurfaceVariant  
                            )  
                        }  
                    }  
                }  
            }  
        }  
    }  
}  
  
// ==================== 指令列表界面 ====================  
  
@Composable  
private fun CommandsContent(  
    selectedAccount: String,  
    modifier: Modifier = Modifier  
) {  
    Column(  
        modifier = modifier  
            .fillMaxSize()  
            .padding(horizontal = 16.dp)  
    ) {  
        Spacer(modifier = Modifier.height(12.dp))  
        Text(  
            text = "账号: $selectedAccount",  
            style = MaterialTheme.typography.titleMedium,  
            fontWeight = FontWeight.Bold  
        )  
        Spacer(modifier = Modifier.height(4.dp))  
        Text(  
            text = "指令格式：命令 昵称 参数，<>表示必填，[]表示可选，|表示分割",  
            style = MaterialTheme.typography.bodySmall,  
            color = MaterialTheme.colorScheme.onSurfaceVariant  
        )  
        Spacer(modifier = Modifier.height(12.dp))  
        HorizontalDivider()  
  
        LazyColumn(  
            verticalArrangement = Arrangement.spacedBy(4.dp)  
        ) {  
            item { Spacer(modifier = Modifier.height(8.dp)) }  
            items(DAILY_COMMANDS) { cmd ->  
                CommandCard(cmd)  
            }  
            item { Spacer(modifier = Modifier.height(16.dp)) }  
        }  
    }  
}  
  
@Composable  
private fun CommandCard(cmd: CommandItem) {  
    Card(  
        modifier = Modifier.fillMaxWidth(),  
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),  
        colors = CardDefaults.cardColors(  
            containerColor = MaterialTheme.colorScheme.surfaceVariant  
        )  
    ) {  
        Column(modifier = Modifier.padding(12.dp)) {  
            Text(  
                text = cmd.command,  
                style = MaterialTheme.typography.bodyMedium,  
                fontWeight = FontWeight.Bold,  
                color = MaterialTheme.colorScheme.primary  
            )  
            if (cmd.description.isNotBlank()) {  
                Spacer(modifier = Modifier.height(2.dp))  
                Text(  
                    text = cmd.description,  
                    style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            }  
        }  
    }  
}