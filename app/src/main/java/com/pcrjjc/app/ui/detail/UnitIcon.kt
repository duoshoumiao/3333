package com.pcrjjc.app.ui.detail  
  
import androidx.compose.foundation.layout.size  
import androidx.compose.foundation.shape.RoundedCornerShape  
import androidx.compose.material3.CircularProgressIndicator  
import androidx.compose.runtime.Composable  
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
  
    // 优先使用本地缓存的图标  
    val localPath = CharaIconUtil.getLocalIconPath(context, unitId)  
    val localFallback = CharaIconUtil.getLocalFallbackPath(context, unitId)  
  
    // 主数据源：本地文件优先，否则用网络 URL  
    val primarySource: Any = if (localPath != null) {  
        File(localPath)  
    } else {  
        CharaIconUtil.getPriorityIconUrl(unitId)  
    }  
  
    // 回退数据源  
    val fallbackSource: Any = if (localFallback != null) {  
        File(localFallback)  
    } else {  
        CharaIconUtil.getFallbackIconUrl(unitId)  
    }  
  
    SubcomposeAsyncImage(  
        model = ImageRequest.Builder(context)  
            .data(primarySource)  
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
            CircularProgressIndicator(modifier = Modifier.size(size / 2))  
        },  
        error = {  
            AsyncImage(  
                model = ImageRequest.Builder(context)  
                    .data(fallbackSource)  
                    .crossfade(true)  
                    .diskCachePolicy(CachePolicy.ENABLED)  
                    .build(),  
                contentDescription = "角色头像 $unitId",  
                modifier = Modifier  
                    .size(size)  
                    .clip(RoundedCornerShape(8.dp)),  
                contentScale = ContentScale.Crop  
            )  
        }  
    )  
}