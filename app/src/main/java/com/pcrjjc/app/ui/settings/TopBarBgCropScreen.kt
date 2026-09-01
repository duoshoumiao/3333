package com.pcrjjc.app.ui.settings  
  
import android.graphics.Bitmap  
import android.graphics.drawable.BitmapDrawable  
import android.net.Uri  
import androidx.compose.foundation.Image  
import androidx.compose.foundation.gestures.detectTransformGestures  
import androidx.compose.foundation.layout.*  
import androidx.compose.material3.*  
import androidx.compose.runtime.*  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.draw.clipToBounds  
import androidx.compose.ui.geometry.Offset  
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
import kotlin.math.max  
  
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
  
    // 关键：记录裁剪框在屏幕上的真实像素尺寸，用于反算裁剪区域  
    var frameSize by remember { mutableStateOf(IntSize.Zero) }  
  
    Scaffold(  
        topBar = {  
            TopAppBar(  
                title = { Text("裁剪顶栏背景") },  
                navigationIcon = { TextButton(onClick = onCancel) { Text("取消") } },  
                actions = {  
                    TextButton(onClick = {  
                        val bmp = srcBitmap ?: return@TextButton  
                        if (frameSize.width == 0 || frameSize.height == 0) return@TextButton  
                        val cropped = computeCrop(bmp, frameSize, userScale, offset)  
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
            val bmp = srcBitmap  
            if (bmp != null) {  
                val ratio = TopBarBgStorage.TARGET_WIDTH.toFloat() /  
                        TopBarBgStorage.TARGET_HEIGHT  
                // 裁剪框：全宽 + 顶栏比例定高，居中，超出部分裁掉  
                Box(  
                    modifier = Modifier  
                        .fillMaxWidth()  
                        .aspectRatio(ratio)  
                        .onSizeChanged { frameSize = it }  
                        .clipToBounds()  
                ) {  
                    Image(  
                        bitmap = bmp.asImageBitmap(),  
                        contentDescription = null,  
                        contentScale = ContentScale.Crop, // cover 作为基准缩放  
                        modifier = Modifier  
                            .matchParentSize()  
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
                }  
            }  
        }  
    }  
}  
  
/**  
 * 依据裁剪框像素尺寸 frame、用户缩放 userScale、平移 offset，  
 * 反算出原图上对应的裁剪矩形，再缩放到目标固定规格。  
 *  
 * 显示时：Image 用 ContentScale.Crop 铺满 frame（baseScale=cover），  
 * 再经 graphicsLayer 以 frame 中心为原点做 userScale 缩放 + offset 平移。  
 * 因此某原图像素 p 映射到 frame 坐标 f 满足：  
 *   f = frameCenter + (p - bmpCenter) * (baseScale * userScale) + offset  
 * 反解 f=0 与 f=frame 两条边即得裁剪矩形。  
 */  
private fun computeCrop(  
    src: Bitmap,  
    frame: IntSize,  
    userScale: Float,  
    offset: Offset  
): Bitmap {  
    val frameW = frame.width.toFloat()  
    val frameH = frame.height.toFloat()  
    val baseScale = max(frameW / src.width, frameH / src.height)  
    val s = baseScale * userScale  
  
    var left = src.width / 2f + (-frameW / 2f - offset.x) / s  
    var top = src.height / 2f + (-frameH / 2f - offset.y) / s  
    var cropW = frameW / s  
    var cropH = frameH / s  
  
    // 边界修正，防止越界  
    if (left < 0f) left = 0f  
    if (top < 0f) top = 0f  
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