package com.pcrjjc.app.ui.eqa  
  
import androidx.compose.animation.AnimatedVisibility  
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
import androidx.compose.foundation.lazy.items  
import androidx.compose.foundation.text.KeyboardActions  
import androidx.compose.foundation.text.KeyboardOptions  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
import androidx.compose.material.icons.filled.ExpandLess  
import androidx.compose.material.icons.filled.ExpandMore  
import androidx.compose.material.icons.filled.Search  
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
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.LaunchedEffect  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.runtime.remember  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.text.input.ImeAction  
import androidx.compose.ui.text.input.KeyboardType  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun EqaScreen(  
    viewModel: EqaViewModel = hiltViewModel(),  
    onNavigateBack: () -> Unit  
) {  
    val uiState by viewModel.uiState.collectAsState()  
    val snackbarHostState = remember { SnackbarHostState() }  
  
    LaunchedEffect(uiState.errorMessage) {  
        uiState.errorMessage?.let {  
            snackbarHostState.showSnackbar(it)  
        }  
    }  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = { Text("问答") },  
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
                .padding(horizontal = 16.dp)  
        ) {  
            // 群号输入 + 查询按钮  
            Row(  
                modifier = Modifier.fillMaxWidth(),  
                horizontalArrangement = Arrangement.spacedBy(8.dp),  
                verticalAlignment = Alignment.CenterVertically  
            ) {  
                OutlinedTextField(  
                    value = uiState.groupIdInput,  
                    onValueChange = { viewModel.updateGroupId(it) },  
                    label = { Text("群号") },  
                    modifier = Modifier.weight(1f),  
                    singleLine = true,  
                    keyboardOptions = KeyboardOptions(  
                        keyboardType = KeyboardType.Number,  
                        imeAction = ImeAction.Search  
                    ),  
                    keyboardActions = KeyboardActions(  
                        onSearch = { viewModel.loadQuestions() }  
                    )  
                )  
                Button(  
                    onClick = { viewModel.loadQuestions() },  
                    enabled = !uiState.isLoading  
                ) {  
                    if (uiState.isLoading) {  
                        CircularProgressIndicator(  
                            modifier = Modifier.height(20.dp).width(20.dp),  
                            strokeWidth = 2.dp,  
                            color = MaterialTheme.colorScheme.onPrimary  
                        )  
                    } else {  
                        Icon(Icons.Default.Search, contentDescription = null)  
                        Spacer(modifier = Modifier.width(4.dp))  
                        Text("查询")  
                    }  
                }  
            }  
  
            Spacer(modifier = Modifier.height(12.dp))  
  
            // 问题列表  
            if (uiState.questions.isEmpty() && !uiState.isLoading) {  
                Column(  
                    modifier = Modifier.fillMaxSize(),  
                    horizontalAlignment = Alignment.CenterHorizontally,  
                    verticalArrangement = Arrangement.Center  
                ) {  
                    Text(  
                        text = "输入群号后点击查询，显示该群的问答列表",  
                        style = MaterialTheme.typography.bodyMedium,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                }  
            } else {  
                LazyColumn(  
                    modifier = Modifier.fillMaxSize(),  
                    verticalArrangement = Arrangement.spacedBy(8.dp)  
                ) {  
                    item { Spacer(modifier = Modifier.height(4.dp)) }  
                    items(uiState.questions, key = { it.question }) { question ->  
                        QuestionCard(  
                            question = question,  
                            isSelected = uiState.selectedQuestion == question.question,  
                            answers = if (uiState.selectedQuestion == question.question) uiState.answers else emptyList(),  
                            isLoadingAnswer = uiState.isLoadingAnswer && uiState.selectedQuestion == question.question,  
                            onClick = {  
                                if (uiState.selectedQuestion == question.question) {  
                                    viewModel.clearAnswer()  
                                } else {  
                                    viewModel.loadAnswer(question.question)  
                                }  
                            }  
                        )  
                    }  
                    item { Spacer(modifier = Modifier.height(16.dp)) }  
                }  
            }  
        }  
    }  
}  
  
// ==================== 问题卡片 ====================  
  
@Composable  
private fun QuestionCard(  
    question: EqaQuestion,  
    isSelected: Boolean,  
    answers: List<EqaAnswer>,  
    isLoadingAnswer: Boolean,  
    onClick: () -> Unit  
) {  
    Card(  
        modifier = Modifier  
            .fillMaxWidth()  
            .clickable(onClick = onClick),  
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
                        text = question.question,  
                        style = MaterialTheme.typography.titleMedium  
                    )  
                    Spacer(modifier = Modifier.height(2.dp))  
                    Text(  
                        text = "${question.answerCount} 个回答",  
                        style = MaterialTheme.typography.bodySmall,  
                        color = MaterialTheme.colorScheme.onSurfaceVariant  
                    )  
                }  
                Icon(  
                    if (isSelected) Icons.Default.ExpandLess else Icons.Default.ExpandMore,  
                    contentDescription = if (isSelected) "收起" else "展开",  
                    tint = MaterialTheme.colorScheme.onSurfaceVariant  
                )  
            }  
  
            // 展开显示回答  
            AnimatedVisibility(visible = isSelected) {  
                Column(modifier = Modifier.padding(top = 8.dp)) {  
                    HorizontalDivider()  
                    Spacer(modifier = Modifier.height(8.dp))  
                    if (isLoadingAnswer) {  
                        Row(  
                            modifier = Modifier.fillMaxWidth(),  
                            horizontalArrangement = Arrangement.Center  
                        ) {  
                            CircularProgressIndicator(  
                                modifier = Modifier.height(24.dp).width(24.dp),  
                                strokeWidth = 2.dp  
                            )  
                        }  
                    } else if (answers.isEmpty()) {  
                        Text(  
                            text = "暂无回答",  
                            style = MaterialTheme.typography.bodySmall,  
                            color = MaterialTheme.colorScheme.onSurfaceVariant  
                        )  
                    } else {  
                        answers.forEachIndexed { index, answer ->  
                            if (index > 0) {  
                                HorizontalDivider(  
                                    modifier = Modifier.padding(vertical = 4.dp),  
                                    color = MaterialTheme.colorScheme.outlineVariant  
                                )  
                            }  
                            AnswerItem(answer = answer)  
                        }  
                    }  
                }  
            }  
        }  
    }  
}  
  
// ==================== 回答条目 ====================  
  
@Composable  
private fun AnswerItem(answer: EqaAnswer) {  
    Column(modifier = Modifier.fillMaxWidth()) {  
        Text(  
            text = if (answer.isMe) "个人专属 (QQ: ${answer.userId})" else "QQ: ${answer.userId}",  
            style = MaterialTheme.typography.labelSmall,  
            color = MaterialTheme.colorScheme.primary  
        )  
        Spacer(modifier = Modifier.height(2.dp))  
        Text(  
            text = answer.content,  
            style = MaterialTheme.typography.bodyMedium  
        )  
    }  
}