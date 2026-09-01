package com.pcrjjc.app.ui.settings  
  
import android.graphics.Bitmap  
import android.net.Uri  
import androidx.compose.foundation.Canvas  
import androidx.compose.foundation.Image  
import androidx.compose.foundation.gestures.detectTransformGestures  
import androidx.compose.foundation.layout.*  
import androidx.compose.material3.*  
import androidx.compose.runtime.*  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.geometry.Offset  
import androidx.compose.ui.geometry.Rect  
import androidx.compose.ui.graphics.Color  
import androidx.compose.ui.graphics.ImageBitmap  
import androidx.compose.ui.graphics.asImageBitmap  
import androidx.compose.ui.graphics.graphicsLayer  
import androidx.compose.ui.input.pointer.pointerInput  
import androidx.compose.ui.layout.ContentScale  
import androidx.compose.ui.platform.LocalContext  
import androidx.compose.ui.unit.dp  
import coil.imageLoader  
import coil.request.ImageRequest  
import com.pcrjjc.app.util.TopBarBgStorage  
import kotlinx.coroutines.launch  
  
@OptIn(ExperimentalMaterial3Api::class)  
@Composable  
fun TopBarBgCropScreen(  
    imageUri: Uri,  
    onDone: () -> Unit,  
    onCancel: () -> Unit  
) {  
    val context = LocalContext.current  
    val scope = rememberCoroutineScope()  
  
    var srcBitmap by remember { mutableStateOf<Bitmap?>(null) }  
  
    // 用 Coil 解码原图（自动处理 EXIF 旋转）  
    LaunchedEffect(imageUri) {  
        val req = ImageRequest.Builder(context).data(imageUri).allowHardware(false).build()  
        val result = context.imageLoader.execute(req)  
        srcBitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap  
    }  
  
    var scale by remember { mutableStateOf(1f) }  
    var offset by remember { mutableStateOf(Offset.Zero) }  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = { Text("裁剪顶栏背景") },  
                navigationIcon = {  
                    TextButton(onClick = onCancel) { Text("取消") }  
                },  
                actions = {  
                    TextButton(onClick = {  
                        val bmp = srcBitmap ?: return@TextButton  
                        // TODO: 根据 scale/offset 与显示区域换算裁剪矩形，  
                        // 从原图裁出对应像素并缩放到 TARGET_WIDTH x TARGET_HEIGHT  
                        val cropped = cropToTarget(bmp, scale, offset)  
                        scope.launch {  
                            TopBarBgStorage.save(context, cropped)  
                            onDone()  
                        }  
                    }) { Text("确定") }  
                }  
            )  
        }  
    ) { padding ->  
        Box(  
            modifier = Modifier  
                .fillMaxSize()  
                .padding(padding),  
            contentAlignment = Alignment.Center  
        ) {  
            srcBitmap?.let { bmp ->  
                Image(  
                    bitmap = bmp.asImageBitmap(),  
                    contentDescription = null,  
                    contentScale = ContentScale.Fit,  
                    modifier = Modifier  
                        .fillMaxSize()  
                        .graphicsLayer(  
                            scaleX = scale,  
                            scaleY = scale,  
                            translationX = offset.x,  
                            translationY = offset.y  
                        )  
                        .pointerInput(Unit) {  
                            detectTransformGestures { _, pan, zoom, _ ->  
                                scale = (scale * zoom).coerceIn(1f, 5f)  
                                offset += pan  
                            }  
                        }  
                )  
            }  
            // 顶栏比例的裁剪框遮罩  
            CropOverlay(  
                aspectRatio = TopBarBgStorage.TARGET_WIDTH.toFloat() /  
                        TopBarBgStorage.TARGET_HEIGHT  
            )  
        }  
    }  
}  
  
@Composable  
private fun CropOverlay(aspectRatio: Float) {  
    Canvas(modifier = Modifier.fillMaxSize()) {  
        val boxWidth = size.width  
        val boxHeight = boxWidth / aspectRatio  
        val top = (size.height - boxHeight) / 2f  
        // 上下遮罩  
        drawRect(Color(0x99000000), size = androidx.compose.ui.geometry.Size(size.width, top))  
        drawRect(  
            Color(0x99000000),  
            topLeft = Offset(0f, top + boxHeight),  
            size = androidx.compose.ui.geometry.Size(size.width, size.height - top - boxHeight)  
        )  
    }  
}  
  
// 依据手势状态从原图裁出目标区域并缩放到固定规格  
private fun cropToTarget(src: Bitmap, scale: Float, offset: Offset): Bitmap {  
    // 简化实现：按目标宽高比中心裁剪后缩放。  
    // 执行方需结合实际显示尺寸/scale/offset 做精确换算。  
    val ratio = TopBarBgStorage.TARGET_WIDTH.toFloat() / TopBarBgStorage.TARGET_HEIGHT  
    val cropW: Int  
    val cropH: Int  
    if (src.width.toFloat() / src.height > ratio) {  
        cropH = src.height  
        cropW = (cropH * ratio).toInt()  
    } else {  
        cropW = src.width  
        cropH = (cropW / ratio).toInt()  
    }  
    val x = ((src.width - cropW) / 2).coerceAtLeast(0)  
    val y = ((src.height - cropH) / 2).coerceAtLeast(0)  
    val cropped = Bitmap.createBitmap(src, x, y, cropW, cropH)  
    return Bitmap.createScaledBitmap(  
        cropped, TopBarBgStorage.TARGET_WIDTH, TopBarBgStorage.TARGET_HEIGHT, true  
    )  
}