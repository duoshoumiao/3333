package com.pcrjjc.app.ui.detail  
  
import androidx.compose.foundation.Image  
import androidx.compose.foundation.layout.size  
import androidx.compose.foundation.shape.RoundedCornerShape  
import androidx.compose.runtime.Composable  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.draw.clip  
import androidx.compose.ui.layout.ContentScale  
import androidx.compose.ui.platform.LocalContext  
import androidx.compose.ui.res.painterResource  
import androidx.compose.ui.unit.Dp  
import androidx.compose.ui.unit.dp  
import coil.compose.AsyncImage  
import coil.compose.SubcomposeAsyncImage  
import coil.request.ImageRequest  
import coil.request.CachePolicy  
import com.pcrjjc.app.util.CharaIconUtil  
import androidx.compose.material3.CircularProgressIndicator  
  
@Composable  
fun UnitIcon(  
    unitId: Int,  
    modifier: Modifier = Modifier,  
    size: Dp = 48.dp,  
    star: Int = 0  
) {  
    val context = LocalContext.current  
    // 优先尝试61（6星），失败后回退到31（3星）  
    val primaryUrl = CharaIconUtil.getPriorityIconUrl(unitId)  
    val fallbackUrl = CharaIconUtil.getFallbackIconUrl(unitId)  
  
    SubcomposeAsyncImage(  
        model = ImageRequest.Builder(context)  
            .data(primaryUrl)  
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
            // 主URL失败，尝试回退URL  
            AsyncImage(  
                model = ImageRequest.Builder(context)  
                    .data(fallbackUrl)  
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