package com.pcrjjc.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.pcrjjc.app.ui.theme.CyberAccent
import com.pcrjjc.app.ui.theme.isNightTheme
import kotlin.math.floor
import kotlin.math.sin

/**
 * 轻赛博主视觉背景。
 * 浅色：冰白渐变 + 细网格 + 青紫双光晕；
 * 夜间：深空渐变 + 极光光晕 + 电路网格 + 星点 + 暗角。
 *
 * 夜间由 MainActivity 铺在整个导航容器下层，所有页面透出同一层夜色。
 */
@Composable
fun CyberBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize().cyberBackdrop(), content = content)
}

/** 把轻赛博背景画在任意组件的底层 */
@Composable
fun Modifier.cyberBackdrop(): Modifier {
    val dark = isNightTheme()
    val scheme = MaterialTheme.colorScheme
    return drawBehind {
        if (dark) drawNightBackdrop() else drawLightBackdrop(scheme)
    }
}

// ==================== 浅色 ====================

private fun DrawScope.drawLightBackdrop(scheme: ColorScheme) {
    drawRect(
        Brush.verticalGradient(
            colors = listOf(
                scheme.background,
                scheme.surfaceContainerLow,
                scheme.background
            )
        )
    )

    // 全息网格
    val step = 34.dp.toPx()
    val gridColor = scheme.primary.copy(alpha = 0.05f)
    var x = 0f
    while (x <= size.width) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }

    val cyanCenter = Offset(size.width * 0.92f, size.height * 0.04f)
    val cyanRadius = size.minDimension * 0.85f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(scheme.secondary.copy(alpha = 0.14f), Color.Transparent),
            center = cyanCenter,
            radius = cyanRadius
        ),
        radius = cyanRadius,
        center = cyanCenter
    )

    val violetCenter = Offset(-size.width * 0.10f, size.height * 0.88f)
    val violetRadius = size.minDimension * 0.9f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(scheme.primary.copy(alpha = 0.11f), Color.Transparent),
            center = violetCenter,
            radius = violetRadius
        ),
        radius = violetRadius,
        center = violetCenter
    )
}

// ==================== 夜间 ====================

private fun DrawScope.drawNightBackdrop() {
    // 深空蓝 → 夜紫的纵向渐变
    drawRect(
        Brush.verticalGradient(
            0f to CyberAccent.NightDeep,
            0.45f to CyberAccent.NightIndigo,
            1f to CyberAccent.NightPlum
        )
    )

    drawStars()

    // 右上极光：青
    val cyanCenter = Offset(size.width * 0.95f, -size.height * 0.03f)
    val cyanRadius = size.maxDimension * 0.75f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(CyberAccent.NightCyan.copy(alpha = 0.20f), Color.Transparent),
            center = cyanCenter,
            radius = cyanRadius
        ),
        radius = cyanRadius,
        center = cyanCenter
    )

    // 右下极光：洋红
    val magentaCenter = Offset(size.width * 1.02f, size.height * 0.72f)
    val magentaRadius = size.maxDimension * 0.55f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(CyberAccent.NightMagenta.copy(alpha = 0.13f), Color.Transparent),
            center = magentaCenter,
            radius = magentaRadius
        ),
        radius = magentaRadius,
        center = magentaCenter
    )

    // 左下极光：紫
    val violetCenter = Offset(-size.width * 0.12f, size.height * 0.92f)
    val violetRadius = size.maxDimension * 0.7f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(CyberAccent.NightViolet.copy(alpha = 0.20f), Color.Transparent),
            center = violetCenter,
            radius = violetRadius
        ),
        radius = violetRadius,
        center = violetCenter
    )

    // 电路网格：每 4 格加一条亮线
    val step = 34.dp.toPx()
    val minor = Color.White.copy(alpha = 0.045f)
    val major = CyberAccent.NightCyan.copy(alpha = 0.075f)
    var i = 0
    var x = 0f
    while (x <= size.width) {
        drawLine(if (i % 4 == 0) major else minor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
        i++
    }
    i = 0
    var y = 0f
    while (y <= size.height) {
        drawLine(if (i % 4 == 0) major else minor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
        i++
    }

    // 暗角
    val vignetteRadius = size.maxDimension * 0.78f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
            center = Offset(size.width * 0.5f, size.height * 0.42f),
            radius = vignetteRadius
        )
    )
}

/** 低亮度星点，位置由固定种子推导，保证每次绘制一致 */
private fun DrawScope.drawStars() {
    val count = 64
    for (index in 0 until count) {
        val fx = pseudoRandom(index * 2 + 1)
        val fy = pseudoRandom(index * 2 + 2)
        val brightness = pseudoRandom(index * 7 + 11)
        val isGlint = index % 13 == 0

        val radius = (if (isGlint) 1.1f else 0.45f + brightness * 0.55f) * density
        val alpha = if (isGlint) 0.28f else 0.06f + brightness * 0.16f
        val color = when (index % 5) {
            0 -> CyberAccent.NightCyan.copy(alpha = alpha)
            1 -> CyberAccent.NightViolet.copy(alpha = alpha * 0.9f)
            else -> Color.White.copy(alpha = alpha)
        }

        drawCircle(
            color = color,
            radius = radius,
            center = Offset(x = fx * size.width, y = fy * size.height)
        )
    }
}

private fun pseudoRandom(seed: Int): Float {
    val value = sin(seed * 12.9898) * 43758.5453
    return (value - floor(value)).toFloat()
}
