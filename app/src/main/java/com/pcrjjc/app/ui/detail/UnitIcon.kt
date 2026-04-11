package com.pcrjjc.app.ui.detail  
  
import androidx.compose.foundation.background  
import androidx.compose.foundation.layout.Box  
import androidx.compose.foundation.layout.size  
import androidx.compose.foundation.shape.RoundedCornerShape  
import androidx.compose.material3.MaterialTheme  
import androidx.compose.material3.Text  
import androidx.compose.runtime.Composable  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.draw.clip  
import androidx.compose.ui.layout.ContentScale  
import androidx.compose.ui.platform.LocalContext  
import androidx.compose.ui.unit.Dp  
import androidx.compose.ui.unit.dp  
import coil.compose.AsyncImage  
import coil.compose.SubcomposeAsyncImage  
import coil.request.CachePolicy  
import coil.request.ImageRequest  
import com.pcrjjc.app.util.CharaIconUtil  
import java.io.File  
  
@Composable  
fun UnitIcon(  
    unitId: Int,  
    modifier: Modifier = Modifier,  
    size: Dp = 48.dp,  
    star: Int = 0  
) {  
    val context = LocalContext.current  
  
    val localPath: String? = CharaIconUtil.getLocalIconPath(context, unitId)  
    val localFallback: String? = CharaIconUtil.getLocalFallbackPath(context, unitId)  
  
    if (localPath != null) {  
        val primaryFile = File(localPath)  
        SubcomposeAsyncImage(  
            model = ImageRequest.Builder(context)  
                .data(primaryFile)  
                .crossfade(true)  
                .diskCachePolicy(CachePolicy.ENABLED)  
                .memoryCachePolicy(CachePolicy.ENABLED)  
                .build(),  
            contentDescription = "角色头像 $unitId",  
            modifier = modifier  
                .size(size)  
                .clip(RoundedCornerShape(8.dp)),  
            contentScale = ContentScale.Crop,  
            loading = {  
                IconPlaceholder(size = size)  
            },  
            error = {  
                if (localFallback != null) {  
                    val fallbackFile = File(localFallback)  
                    AsyncImage(  
                        model = ImageRequest.Builder(context)  
                            .data(fallbackFile)  
                            .crossfade(true)  
                            .diskCachePolicy(CachePolicy.ENABLED)  
                            .build(),  
                        contentDescription = "角色头像 $unitId",  
                        modifier = Modifier  
                            .size(size)  
                            .clip(RoundedCornerShape(8.dp)),  
                        contentScale = ContentScale.Crop  
                    )  
                } else {  
                    IconPlaceholder(size = size)  
                }  
            }  
        )  
    } else if (localFallback != null) {  
        val fallbackFile = File(localFallback)  
        SubcomposeAsyncImage(  
            model = ImageRequest.Builder(context)  
                .data(fallbackFile)  
                .crossfade(true)  
                .diskCachePolicy(CachePolicy.ENABLED)  
                .memoryCachePolicy(CachePolicy.ENABLED)  
                .build(),  
            contentDescription = "角色头像 $unitId",  
            modifier = modifier  
                .size(size)  
                .clip(RoundedCornerShape(8.dp)),  
            contentScale = ContentScale.Crop,  
            loading = {  
                IconPlaceholder(size = size)  
            },  
            error = {  
                IconPlaceholder(size = size)  
            }  
        )  
    } else {  
        IconPlaceholder(  
            size = size,  
            modifier = modifier  
        )  
    }  
}  
  
@Composable  
private fun IconPlaceholder(  
    size: Dp,  
    modifier: Modifier = Modifier  
) {  
    Box(  
        modifier = modifier  
            .size(size)  
            .clip(RoundedCornerShape(8.dp))  
            .background(MaterialTheme.colorScheme.surfaceVariant),  
        contentAlignment = Alignment.Center  
    ) {  
        Text(  
            text = "?",  
            style = MaterialTheme.typography.bodySmall,  
            color = MaterialTheme.colorScheme.onSurfaceVariant  
        )  
    }  
}