package com.pcrjjc.app.ui.whois

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pcrjjc.app.R
import com.pcrjjc.app.util.CharaProfile
import com.pcrjjc.app.util.ElementUnits
import com.pcrjjc.app.util.IconStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhoIsScreen(
    viewModel: WhoIsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("这是谁") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("角色名 / 别称 / ID") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(onClick = viewModel::clear) {
                                    Icon(Icons.Default.Close, contentDescription = "清空")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                viewModel.search()
                            }
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.search()
                        },
                        modifier = Modifier.height(56.dp),
                        enabled = !uiState.isLoading && uiState.query.isNotBlank()
                    ) {
                        Text("搜索")
                    }
                }
            }

            when {
                uiState.isLoading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                !uiState.hasSearched -> {
                    item { WhoIsHintCard() }
                }

                uiState.results.isEmpty() -> {
                    item { WhoIsNoResultCard(uiState.query) }
                }

                else -> {
                    if (!uiState.exactMatch) {
                        item {
                            Text(
                                text = "兰德索尔似乎没有叫「${uiState.query}」的人...\n您要找的可能是：",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(uiState.results, key = { it.id }) { profile ->
                        WhoIsCard(profile)
                    }
                }
            }
        }
    }
}

@Composable
private fun WhoIsHintCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "查询角色档案",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "输入角色名、日文名、英文名、常见别称或 4 位 ID，例如「可可罗」「kkr」「1059」。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "头像优先使用本地缓存的角色图标，未缓存时自动下载并保存。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WhoIsNoResultCard(query: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "兰德索尔似乎没有叫「$query」的人...",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "换个名字试试，或直接输入角色 ID。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WhoIsCard(profile: CharaProfile) {
    val dark = isSystemInDarkTheme()
    // 还原 whois 图片的淡青绿色背景与深褐红文字
    val container = if (dark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFB6D0C9)
    val content = if (dark) MaterialTheme.colorScheme.onSurface else Color(0xFF6A3E42)
    val secondary = if (dark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF7A565A)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CharaAvatar(profileId = profile.id, size = 128.dp)

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "ID: ${profile.id.toString().padStart(4, '0')}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = content
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = profile.names.joinToString(" "),
                style = MaterialTheme.typography.bodyMedium,
                color = content
            )

            val fields = profile.displayFields
            if (fields.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                fields.forEach { (key, value) ->
                    Text(
                        text = "$key: $value",
                        style = MaterialTheme.typography.bodyMedium,
                        color = content,
                        lineHeight = 22.sp
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "暂无档案资料",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondary
                )
            }
        }
    }
}

@Composable
private fun CharaAvatar(profileId: Int, size: Dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val localPath = remember(profileId) {
        IconStorage.getIconPath(context, profileId, 6)
            ?: IconStorage.getIconPath(context, profileId, 3)
            ?: IconStorage.getIconPath(context, profileId, 1)
    }

    var useNetwork by remember(profileId) { mutableStateOf(false) }
    var retryStar by remember(profileId) { mutableStateOf(6) }
    var savedToCache by remember(profileId) { mutableStateOf(false) }

    val model: Any = if (localPath != null && !useNetwork) {
        File(localPath)
    } else {
        "https://redive.estertion.win/icon/unit/${profileId}${retryStar}1.webp"
    }

    Box(modifier = Modifier.size(size)) {
        // 占位图始终垫在底层：加载中/加载失败时不会露出空白
        AvatarPlaceholder()

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = "角色头像 $profileId",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
            onError = {
                if (localPath != null && !useNetwork) {
                    // 本地缓存损坏时回退到网络
                    useNetwork = true
                } else if (retryStar == 6) {
                    // 6 星头像不存在时回退到 3 星
                    retryStar = 3
                }
            },
            onSuccess = { state ->
                // 把网络加载成功的头像写入本地缓存，下次直接走本地
                if (!savedToCache && (localPath == null || useNetwork)) {
                    savedToCache = true
                    val bitmap = (state.result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val star = retryStar
                        scope.launch(Dispatchers.IO) {
                            try {
                                val software = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                                    ?: return@launch
                                IconStorage.saveBitmap(context, profileId, star, software)
                                software.recycle()
                            } catch (_: Exception) {
                                // 保存缓存失败不影响显示
                            }
                        }
                    }
                }
            }
        )

        val elementKey = ElementUnits.elementKeyOf(profileId)
        val elementRes = elementDrawable(elementKey)
        if (elementRes != null) {
            androidx.compose.foundation.Image(
                painter = painterResource(elementRes),
                contentDescription = ElementUnits.elementNameOf(profileId),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(size * 0.30f)
                    .padding(2.dp)
            )
        }
    }
}

@Composable
private fun AvatarPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun elementDrawable(key: String?): Int? = when (key) {
    "fire" -> R.drawable.whois_element_fire
    "water" -> R.drawable.whois_element_water
    "wind" -> R.drawable.whois_element_wind
    "light" -> R.drawable.whois_element_light
    "dark" -> R.drawable.whois_element_dark
    else -> null
}
