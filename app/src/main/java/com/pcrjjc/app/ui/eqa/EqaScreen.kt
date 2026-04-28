package com.pcrjjc.app.ui.eqa  
  
import android.graphics.BitmapFactory  
import android.util.Base64  
import androidx.compose.animation.AnimatedVisibility  
import androidx.compose.foundation.Image  
import androidx.compose.foundation.clickable  
import androidx.compose.foundation.layout.Arrangement  
import androidx.compose.foundation.layout.Column  
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
import androidx.compose.foundation.shape.RoundedCornerShape  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.automirrored.filled.ArrowBack  
import androidx.compose.material.icons.filled.ExpandLess  
import androidx.compose.material.icons.filled.ExpandMore  
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
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.LaunchedEffect  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.runtime.remember  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.draw.clip  
import androidx.compose.ui.graphics.asImageBitmap  
import androidx.compose.ui.layout.ContentScale  
import androidx.compose.ui.platform.LocalContext  
import androidx.compose.ui.unit.dp  
import androidx.hilt.navigation.compose.hiltViewModel  
import coil.compose.AsyncImage  
import coil.request.ImageRequest  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun EqaScreen(  
    viewModel: EqaViewModel = hiltViewModel(),  
    onNavigateBack: () -> Unit  
) {  
    val uiState by viewModel.uiState.collectAsState()  
    val snackbarHostState = remember { SnackbarHostState() }  
  
    LaunchedEffect(Unit) {  
        viewModel.loadQuestions()  
    }  
  
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
            if (uiState.isLoading) {  
                Column(  
                    modifier = Modifier.fillMaxSize(),  
                    horizontalAlignment = Alignment.CenterHorizontally,  
                    verticalArrangement = Arrangement.Center  
                ) {  
                    CircularProgressIndicator()  
                    Spacer(modifier = Modifier.height(8.dp))  
                    Text("加载中...")  
                }  
            } else if (uiState.questions.isEmpty()) {  
                Column(  
                    modifier = Modifier.fillMaxSize(),  
                    horizontalAlignment = Alignment.CenterHorizontally,  
                    verticalArrangement = Arrangement.Center  
                ) {  
                    Text(  
                        text = "暂无问答数据",  
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
                                modifier = Modifier  
                                    .height(24.dp)  
                                    .width(24.dp),  
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
  
@Composable  
private fun AnswerItem(answer: EqaAnswer) {  
    val context = LocalContext.current  
    Column(modifier = Modifier.fillMaxWidth()) {  
        Text(  
            text = if (answer.isMe) "个人专属 (QQ: ${answer.userId})" else "QQ: ${answer.userId}",  
            style = MaterialTheme.typography.labelSmall,  
            color = MaterialTheme.colorScheme.primary  
        )  
        Spacer(modifier = Modifier.height(4.dp))  
        // 渲染每个内容片段（文本或图片）  
        answer.segments.forEach { segment ->  
            when (segment.type) {  
                "text" -> {  
                    Text(  
                        text = segment.data,  
                        style = MaterialTheme.typography.bodyMedium  
                    )  
                }  
                "image" -> {  
                    if (segment.data.startsWith("base64://")) {  
                        // base64 图片：解码后显示  
                        val b64String = segment.data.removePrefix("base64://")  
                        try {  
                            val bytes = Base64.decode(b64String, Base64.DEFAULT)  
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)  
                            if (bitmap != null) {  
                                Image(  
                                    bitmap = bitmap.asImageBitmap(),  
                                    contentDescription = "图片",  
                                    modifier = Modifier  
                                        .fillMaxWidth()  
                                        .heightIn(max = 300.dp)  
                                        .clip(RoundedCornerShape(8.dp)),  
                                    contentScale = ContentScale.FillWidth  
                                )  
                            }  
                        } catch (e: Exception) {  
                            Text(  
                                text = "[图片加载失败]",  
                                style = MaterialTheme.typography.bodySmall,  
                                color = MaterialTheme.colorScheme.error  
                            )  
                        }  
                    } else {  
                        // HTTP URL 图片：用 Coil 加载  
                        AsyncImage(  
                            model = ImageRequest.Builder(context)  
                                .data(segment.data)  
                                .crossfade(true)  
                                .build(),  
                            contentDescription = "图片",  
                            modifier = Modifier  
                                .fillMaxWidth()  
                                .heightIn(max = 300.dp)  
                                .clip(RoundedCornerShape(8.dp)),  
                            contentScale = ContentScale.FillWidth  
                        )  
                    }  
                    Spacer(modifier = Modifier.height(4.dp))  
                }  
            }  
        }  
    }  
}