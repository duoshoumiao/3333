package com.pcrjjc.app.ui.components  
  
import androidx.compose.foundation.layout.Box  
import androidx.compose.material3.Icon  
import androidx.compose.runtime.Composable  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.graphics.Color  
import androidx.compose.ui.graphics.graphicsLayer  
import androidx.compose.ui.graphics.vector.ImageVector  
  
val GoldColor = Color(0xFFFFD700)  
  
@Composable  
fun StrokedIcon(  
    imageVector: ImageVector,  
    contentDescription: String?,  
    modifier: Modifier = Modifier,  
    fillColor: Color = Color.Black,  
    strokeColor: Color = GoldColor,  
    outlineScale: Float = 1.25f  // 控制描边粗细，越大描边越粗  
) {  
    Box(modifier = modifier, contentAlignment = Alignment.Center) {  
        // 底层：金色图标，放大显示，露出的边缘形成描边效果  
        Icon(  
            imageVector = imageVector,  
            contentDescription = null,  
            tint = strokeColor,  
            modifier = Modifier.graphicsLayer(scaleX = outlineScale, scaleY = outlineScale)  
        )  
        // 上层：黑色图标，正常大小，覆盖在金色图标上方  
        Icon(  
            imageVector = imageVector,  
            contentDescription = contentDescription,  
            tint = fillColor  
        )  
    }  
}