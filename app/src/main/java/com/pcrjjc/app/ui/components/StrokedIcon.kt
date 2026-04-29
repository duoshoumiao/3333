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
    strokeColor: Color = GoldColor,  
    strokeWidth: Float = 3f  
) {  
    Box(modifier = modifier, contentAlignment = Alignment.Center) {  
        // 底层：金色描边  
        Icon(  
            imageVector = imageVector,  
            contentDescription = null,  
            tint = strokeColor,  
            modifier = Modifier.drawWithContent {  
                drawContent()  
                // 用 Stroke 样式重绘一遍，产生描边效果  
            }  
        )  
        // 上层：黑色填充（略小，视觉上形成描边）  
        Icon(  
            imageVector = imageVector,  
            contentDescription = contentDescription,  
            tint = fillColor  
        )  
    }  
}