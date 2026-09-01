package com.pcrjjc.app.ui.settings  
  
import android.graphics.Bitmap  
import android.graphics.drawable.BitmapDrawable  
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
import androidx.compose.ui.geometry.Size  
import androidx.compose.ui.graphics.Color  
import androidx.compose.ui.graphics.TransformOrigin  
import androidx.compose.ui.graphics.asImageBitmap  
import androidx.compose.ui.graphics.graphicsLayer  
import androidx.compose.ui.input.pointer.pointerInput  
import androidx.compose.ui.layout.ContentScale  
import androidx.compose.ui.layout.onSizeChanged  
import androidx.compose.ui.platform.LocalContext  
import androidx.compose.ui.unit.IntSize  
import coil.imageLoader  
import coil.request.ImageRequest  
import com.pcrjjc.app.util.TopBarBgStorage  
import kotlinx.coroutines.launch  
import kotlin.math.min  
  
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
    LaunchedEffect(imageUri) {  
        val req = ImageRequest.Builder(context)  
            .data(imageUri)  
            .allowHardware(false)   // 需要读取像素做裁剪，禁用硬件位图  
            .build()  
        val result = context.imageLoader.execute(req)  
        srcBitmap = (result.drawable as? BitmapDrawable)?.bitmap  
    }  
  
    var userScale by remember { mutableStateOf(1f) }  
    var offset by remember { mutableStateOf(Offset.Zero) }  
  
    // 记录整个显示容器的真实像素尺寸（图片完整显示在这里）  
    var containerSize by remember { mutableStateOf(IntSize.Zero) }  
  
    val ratio = TopBarBgStorage.TARGET_WIDTH.toFloat() / TopBarBgStorage.TARGET_HEIGHT  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = { Text("裁剪顶栏背景") },  
                navigationIcon = { TextButton(onClick = onCancel) { Text("取消") } },  
                actions = {  
                    TextButton(onClick = {  
                        val bmp = srcBitmap ?: return@TextButton  
                        if (containerSize.width == 0 || containerSize.height == 0) return@TextButton  
                        val cropped = computeCrop(bmp, containerSize, ratio, userScale, offset)  
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
                .padding(padding)  
                .onSizeChanged { containerSize = it },  // 关键：拿到容器像素尺寸  
            contentAlignment = Alignment.Center  
        ) {  
            val bmp = srcBitmap  
            if (bmp != null) {  
                // 整张图完整显示（Fit），用户可拖动/缩放  
                Image(  
                    bitmap = bmp.asImageBitmap(),  
                    contentDescription = null,  
                    contentScale = ContentScale.Fit,  
                    modifier = Modifier  
                        .fillMaxSize()  
                        .graphicsLayer(  
                            scaleX = userScale,  
                            scaleY = userScale,  
                            translationX = offset.x,  
                            translationY = offset.y,  
                            transformOrigin = TransformOrigin(0.5f, 0.5f)  
                        )  
                        .pointerInput(Unit) {  
                            detectTransformGestures { _, pan, zoom, _ ->  
                                userScale = (userScale * zoom).coerceIn(1f, 5f)  
                                offset += pan  
                            }  
                        }  
                )  
  
                // 顶栏比例裁剪框 + 框外半透明遮罩（固定居中，全宽）  
                Canvas(modifier = Modifier.fillMaxSize()) {  
                    val frameW = size.width  
                    val frameH = frameW / ratio  
                    val top = (size.height - frameH) / 2f  
                    val mask = Color(0x99000000)  
                    // 上遮罩  
                    drawRect(mask, size = Size(size.width, top))  
                    // 下遮罩  
                    drawRect(  
                        mask,  
                        topLeft = Offset(0f, top + frameH),  
                        size = Size(size.width, size.height - top - frameH)  
                    )  
                    // 框边线  
                    drawRect(  
                        color = Color.White,  
                        topLeft = Offset(0f, top),  
                        size = Size(frameW, frameH),  
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)  
                    )  
                }  
            }  
        }  
    }  
}  
  
/**  
 * 图片以 ContentScale.Fit 完整显示在 container 中（居中），  
 * 再经 graphicsLayer 以 container 中心为原点做 userScale 缩放 + offset 平移。  
 * 裁剪框固定居中、全宽、顶栏比例。  
 *  
 * 某原图像素 p 映射到 container 坐标 f：  
 *   f = containerCenter + (p - srcCenter) * (baseFit * userScale) + offset  
 * 反解裁剪框四条边即得原图裁剪矩形。  
 */  
private fun computeCrop(  
    src: Bitmap,  
    container: IntSize,  
    ratio: Float,  
    userScale: Float,  
    offset: Offset  
): Bitmap {  
    val cw = container.width.toFloat()  
    val ch = container.height.toFloat()  
  
    // 裁剪框在容器中的位置（居中、全宽）  
    val frameW = cw  
    val frameH = frameW / ratio  
    val frameLeft = 0f  
    val frameTop = (ch - frameH) / 2f  
  
    // Fit 基准缩放  
    val baseFit = min(cw / src.width, ch / src.height)  
    val s = baseFit * userScale  
  
    val srcCx = src.width / 2f  
    val srcCy = src.height / 2f  
    val contCx = cw / 2f  
    val contCy = ch / 2f  
  
    // 反解：p = srcCenter + (f - containerCenter - offset) / s  
    var left = srcCx + (frameLeft - contCx - offset.x) / s  
    var top = srcCy + (frameTop - contCy - offset.y) / s  
    var cropW = frameW / s  
    var cropH = frameH / s  
  
    // 边界修正  
    if (left < 0f) { cropW += left; left = 0f }  
    if (top < 0f) { cropH += top; top = 0f }  
    if (left + cropW > src.width) cropW = src.width - left  
    if (top + cropH > src.height) cropH = src.height - top  
  
    val x = left.toInt().coerceIn(0, src.width - 1)  
    val y = top.toInt().coerceIn(0, src.height - 1)  
    val w = cropW.toInt().coerceIn(1, src.width - x)  
    val h = cropH.toInt().coerceIn(1, src.height - y)  
  
    val cropped = Bitmap.createBitmap(src, x, y, w, h)  
    return Bitmap.createScaledBitmap(  
        cropped,  
        TopBarBgStorage.TARGET_WIDTH,  
        TopBarBgStorage.TARGET_HEIGHT,  
        true  
    )  
}