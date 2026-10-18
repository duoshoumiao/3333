package com.pcrjjc.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 轻赛博（Light Cyber）色板。
 *
 * 取自启动图 / 应用图标的冰蓝全息质感：
 * 冷白底 + 电光青 + 全息紫 + 霓虹洋红；夜间转为深空蓝紫 + 霓虹光晕。
 */
object CyberAccent {
    /** 电光青 */
    val Cyan = Color(0xFF00D2E6)
    /** 全息紫（浅色主色） */
    val Violet = Color(0xFF5B3DF5)
    /** 霓虹洋红 */
    val Magenta = Color(0xFFFF4FB0)
    /** 冰白背景 */
    val Ice = Color(0xFFF5F7FE)

    // 夜景（Night）用色
    /** 深空底 */
    val NightDeep = Color(0xFF05070F)
    /** 夜蓝 */
    val NightIndigo = Color(0xFF0B1024)
    /** 夜紫 */
    val NightPlum = Color(0xFF140A28)
    /** 夜间霓虹紫 */
    val NightViolet = Color(0xFFB7A5FF)
    /** 夜间霓虹青 */
    val NightCyan = Color(0xFF5CF0FF)
    /** 夜间霓虹洋红 */
    val NightMagenta = Color(0xFFFF8AD4)
}

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5B3DF5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DEFF),
    onPrimaryContainer = Color(0xFF190A66),
    inversePrimary = Color(0xFFC9BFFF),

    secondary = Color(0xFF007287),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC6F4FF),
    onSecondaryContainer = Color(0xFF00323D),

    tertiary = Color(0xFFC2187A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8EC),
    onTertiaryContainer = Color(0xFF3D0024),

    error = Color(0xFFC62A4E),
    onError = Color.White,
    errorContainer = Color(0xFFFFD9E1),
    onErrorContainer = Color(0xFF400013),

    background = Color(0xFFF5F7FE),
    onBackground = Color(0xFF10142A),

    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF10142A),
    surfaceVariant = Color(0xFFE3E9F7),
    onSurfaceVariant = Color(0xFF4A5470),
    surfaceTint = Color(0xFF5B3DF5),

    inverseSurface = Color(0xFF232B45),
    inverseOnSurface = Color(0xFFF0F3FF),

    outline = Color(0xFF7A85A4),
    outlineVariant = Color(0xFFC7D1E6),

    scrim = Color(0xFF05070F),

    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDCE2F2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F6FE),
    surfaceContainer = Color(0xFFEDF1FC),
    surfaceContainerHigh = Color(0xFFE7ECF9),
    surfaceContainerHighest = Color(0xFFE1E7F5),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB7A5FF),
    onPrimary = Color(0xFF1B0A6B),
    primaryContainer = Color(0xFF3B2AAA),
    onPrimaryContainer = Color(0xFFEDE7FF),
    inversePrimary = Color(0xFF5B3DF5),

    secondary = Color(0xFF5CF0FF),
    onSecondary = Color(0xFF00333B),
    secondaryContainer = Color(0xFF0B4A57),
    onSecondaryContainer = Color(0xFFC4F8FF),

    tertiary = Color(0xFFFF8AD4),
    onTertiary = Color(0xFF4B002E),
    tertiaryContainer = Color(0xFF70144C),
    onTertiaryContainer = Color(0xFFFFDCEF),

    error = Color(0xFFFF7595),
    onError = Color(0xFF4A0016),
    errorContainer = Color(0xFF7A1030),
    onErrorContainer = Color(0xFFFFDAE3),

    // 夜晚背景交给 MainActivity 的 CyberBackdrop 绘制（极光 + 网格 + 星点），
    // 这里保持透明，让每个页面的 Scaffold 都透出同一层夜色。
    background = Color.Transparent,
    onBackground = Color(0xFFE9EDFF),

    surface = Color(0xFF0A0E1D),
    onSurface = Color(0xFFE9EDFF),
    surfaceVariant = Color(0xFF1A2238),
    onSurfaceVariant = Color(0xFFA9B4D6),
    surfaceTint = Color(0xFFB7A5FF),

    inverseSurface = Color(0xFFE9EDFF),
    inverseOnSurface = Color(0xFF121828),

    outline = Color(0xFF5E6B8C),
    outlineVariant = Color(0xFF26304C),

    scrim = Color(0xFF000000),

    surfaceBright = Color(0xFF2B3556),
    surfaceDim = Color(0xFF05070F),
    surfaceContainerLowest = Color(0xFF06090F),
    surfaceContainerLow = Color(0xFF111A2E),
    surfaceContainer = Color(0xFF141D33),
    surfaceContainerHigh = Color(0xFF18223B),
    surfaceContainerHighest = Color(0xFF1D2844),
)

private val BaseTypography = Typography()

/** 标题略加重、标签拉开字距，营造电子面板的秩序感 */
private val CyberTypography = BaseTypography.copy(
    headlineSmall = BaseTypography.headlineSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp
    ),
    titleLarge = BaseTypography.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp
    ),
    titleMedium = BaseTypography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.25.sp
    ),
    titleSmall = BaseTypography.titleSmall.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp
    ),
    labelLarge = BaseTypography.labelLarge.copy(letterSpacing = 0.6.sp),
    labelMedium = BaseTypography.labelMedium.copy(letterSpacing = 0.6.sp),
    labelSmall = BaseTypography.labelSmall.copy(
        letterSpacing = 0.7.sp,
        fontWeight = FontWeight.Medium
    ),
)

private val CyberShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PcrJjcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 轻赛博是定制风格，默认关闭动态取色，避免被系统壁纸色覆盖
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CyberTypography,
        shapes = CyberShapes,
        content = content
    )
}

/**
 * 当前是否夜间主题。
 *
 * 用配色亮度判断而不是系统深色，这样「设置 → 外观」里强制夜间 / 强制日间时，
 * 顶栏、背景等自定义组件也能走对分支。
 */
@Composable
fun isNightTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
