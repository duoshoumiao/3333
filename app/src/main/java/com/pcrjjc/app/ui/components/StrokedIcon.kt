package com.pcrjjc.app.ui.components  
  
import androidx.compose.foundation.layout.Box  
import androidx.compose.material3.Icon  
import androidx.compose.runtime.Composable  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.draw.drawWithContent  
import androidx.compose.ui.graphics.Color  
import androidx.compose.ui.graphics.drawscope.Stroke  
import androidx.compose.ui.graphics.vector.ImageVector  
  
val GoldColor = Color(0xFFFFD700)  
  
@Composable  
fun StrokedIcon(  
    imageVector: ImageVector,  
    contentDescription: String?,  
    modifier: Modifier = Modifier,  
    fillColor: Color = Color.Black,  
    strokeColor: Color = Color(0xFFFFD700), // 金色  
) {  
    Box(modifier = modifier, contentAlignment = Alignment.Center) {  
        // 底层：金色，放大 1.25 倍，露出的边缘就是"描边"  
        Icon(  
            imageVector = imageVector,  
            contentDescription = null,  
            tint = strokeColor,  
            modifier = Modifier.graphicsLayer(scaleX = 1.25f, scaleY = 1.25f)  
        )  
        // 上层：黑色，正常大小  
        Icon(  
            imageVector = imageVector,  
            contentDescription = contentDescription,  
            tint = fillColor  
        )  
    }  
}