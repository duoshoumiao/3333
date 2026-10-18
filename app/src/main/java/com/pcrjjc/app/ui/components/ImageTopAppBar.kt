package com.pcrjjc.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.pcrjjc.app.R
import com.pcrjjc.app.ui.theme.CyberAccent
import com.pcrjjc.app.ui.theme.isNightTheme
import com.pcrjjc.app.util.TopBarBgStorage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val version by TopBarBgStorage.version.collectAsState()
    val customBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = version
    ) {
        value = TopBarBgStorage.load(context)?.asImageBitmap()
    }

    val scheme = MaterialTheme.colorScheme
    val dark = isNightTheme()

    Box(modifier = modifier) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap!!,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(R.drawable.topbar_bg),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }

        // 轻赛博雾面蒙层：夜间压得更重，避免白天插画在夜景里跳出来
        val bottomFade = if (dark) CyberAccent.NightDeep else scheme.background
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            scheme.surface.copy(alpha = if (dark) 0.94f else 0.80f),
                            scheme.surface.copy(alpha = if (dark) 0.88f else 0.62f),
                            bottomFade.copy(alpha = if (dark) 0.92f else 0.72f)
                        )
                    )
                )
        )

        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = scheme.onSurface,
                navigationIconContentColor = scheme.primary,
                actionIconContentColor = scheme.primary,
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
