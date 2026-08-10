package com.pcrjjc.app.ui.labyrinth  
  
import androidx.compose.foundation.ExperimentalFoundationApi  
import androidx.compose.foundation.layout.Arrangement  
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
import androidx.compose.foundation.layout.width  
import androidx.compose.foundation.lazy.LazyColumn  
import androidx.compose.foundation.lazy.items  
import androidx.compose.foundation.rememberScrollState  
import androidx.compose.foundation.verticalScroll  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
import androidx.compose.material.icons.filled.ArrowDropDown  
import androidx.compose.material3.Button  
import androidx.compose.material3.Card  
import androidx.compose.material3.CardDefaults  
import androidx.compose.material3.CircularProgressIndicator  
import androidx.compose.material3.DropdownMenuItem  
import androidx.compose.material3.ExperimentalMaterial3Api  
import androidx.compose.material3.ExposedDropdownMenuBox  
import androidx.compose.material3.ExposedDropdownMenuDefaults  
import androidx.compose.material3.FilterChip  
import androidx.compose.material3.HorizontalDivider  
import androidx.compose.material3.Icon  
import androidx.compose.material3.IconButton  
import androidx.compose.material3.MaterialTheme  
import androidx.compose.material3.OutlinedTextField  
import androidx.compose.material3.Scaffold  
import androidx.compose.material3.SnackbarHost  
import androidx.compose.material3.SnackbarHostState  
import androidx.compose.material3.Switch  
import androidx.compose.material3.Text  
import androidx.compose.material3.TopAppBar  
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.LaunchedEffect  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.runtime.mutableStateOf  
import androidx.compose.runtime.remember  
import androidx.compose.runtime.setValue  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.text.style.TextAlign  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
import com.pcrjjc.app.domain.LabyrinthRouteFinder  
import com.pcrjjc.app.util.Platform  
  
@OptIn(  
    ExperimentalMaterial3Api::class,  
    ExperimentalFoundationApi::class,  
    ExperimentalLayoutApi::class  
)  
@Composable  
fun LabyrinthScreen(  
    viewModel: LabyrinthViewModel = hiltViewModel(),  
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
                title = { Text("黎明界刷开局") },  
                navigationIcon = {  
                    IconButton(onClick = onNavigateBack) {  
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")  
                    }  
                }  
            )  
        },  
        snackbarHost = { SnackbarHost(snackbarHostState) }  
    ) { paddingValues ->  
        Column(  
            modifier = Modifier  
                .fillMaxSize()  
                .padding(paddingValues)  
        ) {  
            // ==================== 上半部分：表单 ====================  
            Column(  
                modifier = Modifier  
                    .fillMaxWidth()  
                    .heightIn(max = 360.dp)  
                    .verticalScroll(rememberScrollState())  
                    .padding(horizontal = 16.dp),  
                verticalArrangement = Arrangement.spacedBy(10.dp)  
            ) {  
                Spacer(modifier = Modifier.height(2.dp))  
                Text(  
                    "使用“我的账号”里对应服务器的账号登录（与竞技场透视共用）。若已进入黎明界会先撤退再刷。",  
                    style = MaterialTheme.typography.bodySmall,  
                    color = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
  
                // 服务器  
                Text("选择服务器", style = MaterialTheme.typography.labelMedium)  
                FlowRow(  
                    modifier = Modifier.fillMaxWidth(),  
                    horizontalArrangement = Arrangement.spacedBy(6.dp),  
                    verticalArrangement = Arrangement.spacedBy(4.dp)  
                ) {  
                    Platform.entries.forEach { platform ->  
                        FilterChip(  
                            selected = uiState.selectedPlatform == platform,  
                            onClick = { viewModel.updatePlatform(platform) },  
                            label = { Text(platform.displayName, style = MaterialTheme.typography.bodySmall) }  
                        )  
                    }  
                }  
  
                // 难度  
                Text("难度", style = MaterialTheme.typography.labelMedium)  
                FlowRow(  
                    modifier = Modifier.fillMaxWidth(),  
                    horizontalArrangement = Arrangement.spacedBy(6.dp),  
                    verticalArrangement = Arrangement.spacedBy(4.dp)  
                ) {  
                    (1..5).forEach { d ->  
                        FilterChip(  
                            selected = uiState.difficulty == d,  
                            onClick = { viewModel.updateDifficulty(d) },  
                            label = { Text("难度$d", style = MaterialTheme.typography.bodySmall) }  
                        )  
                    }  
                }  
  
                // 公会下拉  
                Text("公会", style = MaterialTheme.typography.labelMedium)  
                var guildExpanded by remember { mutableStateOf(false) }  
                val guildName = viewModel.guildOptions  
                    .firstOrNull { it.first == uiState.selectedGuildId }?.second  
                    ?: uiState.selectedGuildId.toString()  
                ExposedDropdownMenuBox(  
                    expanded = guildExpanded,  
                    onExpandedChange = { guildExpanded = it }  
                ) {  
                    OutlinedTextField(  
                        value = guildName,  
                        onValueChange = {},  
                        readOnly = true,  
                        label = { Text("公会") },  
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },  
                        modifier = Modifier  
                            .fillMaxWidth()  
                            .menuAnchor()  
                    )  
                    ExposedDropdownMenu(  
                        expanded = guildExpanded,  
                        onDismissRequest = { guildExpanded = false }  
                    ) {  
                        viewModel.guildOptions.forEach { (id, name) ->  
                            DropdownMenuItem(  
                                text = { Text(name) },  
                                onClick = {  
                                    viewModel.updateGuild(id)  
                                    guildExpanded = false  
                                }  
                            )  
                        }  
                    }  
                }  
  
                // 完美开局  
                Row(  
                    modifier = Modifier.fillMaxWidth(),  
                    horizontalArrangement = Arrangement.SpaceBetween,  
                    verticalAlignment = Alignment.CenterVertically  
                ) {  
                    Text("完美开局（凹高分，不错过EX/必要遗物）", style = MaterialTheme.typography.bodyMedium)  
                    Switch(  
                        checked = uiState.perfectStart,  
                        onCheckedChange = { viewModel.updatePerfectStart(it) }  
                    )  
                }  
  
                // 区域3/5 第3格  
                Text("区域3/5第3格", style = MaterialTheme.typography.labelMedium)  
                FlowRow(  
                    modifier = Modifier.fillMaxWidth(),  
                    horizontalArrangement = Arrangement.spacedBy(6.dp)  
                ) {  
                    listOf("必须遗物", "必须事件", "两者都行").forEach { opt ->  
                        FilterChip(  
                            selected = uiState.thirdBlockType == opt,  
                            onClick = { viewModel.updateThirdBlockType(opt) },  
                            label = { Text(opt, style = MaterialTheme.typography.bodySmall) }  
                        )  
                    }  
                }  
  
                // 区域3 Boss 多选  
                Text("区域3 Boss（可多选）", style = MaterialTheme.typography.labelMedium)  
                FlowRow(  
                    modifier = Modifier.fillMaxWidth(),  
                    horizontalArrangement = Arrangement.spacedBy(6.dp),  
                    verticalArrangement = Arrangement.spacedBy(4.dp)  
                ) {  
                    LabyrinthRouteFinder.AREA3_BOSSES.forEach { (unitId, name, diff) ->  
                        FilterChip(  
                            selected = unitId in uiState.area3Bosses,  
                            onClick = { viewModel.toggleArea3Boss(unitId) },  
                            label = { Text("【$diff】$name", style = MaterialTheme.typography.bodySmall) }  
                        )  
                    }  
                }  
  
                // 区域5 Boss 多选  
                Text("区域5 Boss（可多选）", style = MaterialTheme.typography.labelMedium)  
                FlowRow(  
                    modifier = Modifier.fillMaxWidth(),  
                    horizontalArrangement = Arrangement.spacedBy(6.dp),  
                    verticalArrangement = Arrangement.spacedBy(4.dp)  
                ) {  
                    LabyrinthRouteFinder.AREA5_BOSSES.forEach { (unitId, name, diff) ->  
                        FilterChip(  
                            selected = unitId in uiState.area5Bosses,  
                            onClick = { viewModel.toggleArea5Boss(unitId) },  
                            label = { Text("【$diff】$name", style = MaterialTheme.typography.bodySmall) }  
                        )  
                    }  
                }  
  
                Button(  
                    onClick = { viewModel.startReroll() },  
                    modifier = Modifier.fillMaxWidth(),  
                    enabled = !uiState.isLoading  
                ) {  
                    if (uiState.isLoading) {  
                        CircularProgressIndicator(  
                            modifier = Modifier.height(20.dp).width(20.dp),  
                            strokeWidth = 2.dp,  
                            color = MaterialTheme.colorScheme.onPrimary  
                        )  
                        Spacer(modifier = Modifier.width(8.dp))  
                        Text("刷取中...")  
                    } else {  
                        Text("开始刷开局")  
                    }  
                }  
  
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))  
            }  
  
            // ==================== 下半部分：结果日志 ====================  
            if (uiState.logs.isEmpty()) {  
                Column(  
                    modifier = Modifier.fillMaxSize().padding(16.dp),  
                    horizontalAlignment = Alignment.CenterHorizontally,  
                    verticalArrangement = Arrangement.Center  
                ) {  
                    Text(  
                        text = "配置好后点击“开始刷开局”，结果将显示在此处",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant,  
                        textAlign = TextAlign.Center  
                    )  
                }  
            } else {  
                LazyColumn(  
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),  
                    verticalArrangement = Arrangement.spacedBy(8.dp)  
                ) {  
                    item { Spacer(modifier = Modifier.height(8.dp)) }  
                    items(uiState.logs) { line ->  
                        Card(  
                            modifier = Modifier.fillMaxWidth(),  
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)  
                        ) {  
                            Text(  
                                text = line,  
                                modifier = Modifier.fillMaxWidth().padding(12.dp),  
                                style = MaterialTheme.typography.bodyMedium  
                            )  
                        }  
                    }  
                    item { Spacer(modifier = Modifier.height(24.dp)) }  
                }  
            }  
        }  
    }  
}