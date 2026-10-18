package com.pcrjjc.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pcrjjc.app.ui.theme.CyberAccent
import com.pcrjjc.app.ui.theme.isNightTheme

/**
 * 轻赛博顶栏。
 * 浅色：雾面冷白 + 细网格 + 青紫渐变发丝线；
 * 夜间：深空蓝底 + 紫/青双光晕 + 发光发丝线。
 * 参数与 Material3 TopAppBar 对齐，可直接平替。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CyberTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val dark = isNightTheme()
    val scheme = MaterialTheme.colorScheme

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    if (dark) {
                        drawRect(CyberAccent.NightDeep)

                        // 紫 → 透明 → 青 的横向扫光
                        drawRect(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    CyberAccent.NightViolet.copy(alpha = 0.16f),
                                    Color.Transparent,
                                    CyberAccent.NightCyan.copy(alpha = 0.14f)
                                )
                            )
                        )

                        // 左下紫光
                        val violetRadius = size.width * 0.72f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    CyberAccent.NightViolet.copy(alpha = 0.30f),
                                    Color.Transparent
                                ),
                                center = Offset(0f, size.height),
                                radius = violetRadius
                            ),
                            radius = violetRadius,
                            center = Offset(0f, size.height)
                        )

                        // 右上青光
                        val cyanRadius = size.width * 0.62f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    CyberAccent.NightCyan.copy(alpha = 0.24f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width, 0f),
                                radius = cyanRadius
                            ),
                            radius = cyanRadius,
                            center = Offset(size.width, 0f)
                        )

                        // 电路网格
                        val step = 22.dp.toPx()
                        val gridColor = Color.White.copy(alpha = 0.055f)
                        var x = step
                        while (x < size.width) {
                            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                            x += step
                        }

                        // 极细扫描线，增加 CRT 质感
                        val scanStep = 4.dp.toPx()
                        val scanColor = Color.White.copy(alpha = 0.022f)
                        var y = 0f
                        while (y < size.height) {
                            drawLine(scanColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                            y += scanStep
                        }

                        // 顶部压暗，衬托发丝线
                        drawRect(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent)
                            )
                        )
                    } else {
                        drawRect(scheme.surface)

                        // 斜向全息渐变：紫 → 透明 → 青
                        drawRect(
                            Brush.linearGradient(
                                colors = listOf(
                                    scheme.primary.copy(alpha = 0.13f),
                                    Color.Transparent,
                                    scheme.secondary.copy(alpha = 0.11f)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height * 1.6f)
                            )
                        )

                        // 电路网格
                        val step = 24.dp.toPx()
                        val gridColor = scheme.primary.copy(alpha = 0.07f)
                        var x = step
                        while (x < size.width) {
                            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                            x += step
                        }

                        // 底部靛蓝光晕
                        val glow = 14.dp.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, scheme.primary.copy(alpha = 0.13f)),
                                startY = 0f,
                                endY = glow
                            ),
                            topLeft = Offset(0f, size.height - glow),
                            size = Size(size.width, glow)
                        )
                    }
                }
        )

        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = scheme.onSurface,
                navigationIconContentColor = if (dark) scheme.secondary else scheme.primary,
                actionIconContentColor = if (dark) scheme.secondary else scheme.primary,
                scrolledContainerColor = Color.Transparent
            )
        )

        NeonHairline(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}

/** 青 → 紫 → 洋红的霓虹发丝线；夜间带一层向上散开的光晕 */
@Composable
fun NeonHairline(modifier: Modifier = Modifier) {
    val dark = isNightTheme()
    val scheme = MaterialTheme.colorScheme
    val glowAlpha = if (dark) 0.42f else 0.11f
    val lineAlpha = if (dark) 0.95f else 0.85f

    Box(
        modifier = modifier
            .height(if (dark) 20.dp else 12.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, scheme.primary.copy(alpha = glowAlpha))
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(if (dark) 1.4.dp else 1.2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            scheme.secondary.copy(alpha = lineAlpha * 0.6f),
                            scheme.primary.copy(alpha = lineAlpha),
                            scheme.tertiary.copy(alpha = lineAlpha * 0.85f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
