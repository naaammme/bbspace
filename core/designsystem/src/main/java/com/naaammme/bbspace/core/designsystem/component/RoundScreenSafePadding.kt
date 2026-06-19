package com.naaammme.bbspace.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

private val roundInscribedSquareInsetRatio = (1f - (1f / sqrt(2f))) / 2f

@Composable
fun roundScreenSafePadding(
    scale: Float = 1f
): PaddingValues {
    val isScreenRound = LocalConfiguration.current.isScreenRound
    if (!isScreenRound) {
        return PaddingValues(0.dp)
    }
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    return remember(density.density, containerSize, scale) {
        val minSide = with(density) {
            minOf(containerSize.width, containerSize.height).toDp()
        }
        // 圆屏内接正方形的单边留白，保证内容矩形不会落到圆角裁切区
        val inset: Dp = minSide * roundInscribedSquareInsetRatio * scale
        PaddingValues(inset)
    }
}
