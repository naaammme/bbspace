package com.naaammme.bbspace.playback

import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val miniPlayerWidth = 220.dp
private const val miniPlayerAspectRatio = 16f / 9f

@Composable
internal fun DraggableMiniPlayerHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val density = LocalDensity.current
        val playerWidthPx = with(density) { miniPlayerWidth.roundToPx() }
        val playerHeightPx = remember(playerWidthPx) {
            (playerWidthPx / miniPlayerAspectRatio).roundToInt()
        }
        val hostWidthPx = with(density) { maxWidth.roundToPx() }
        val hostHeightPx = with(density) { maxHeight.roundToPx() }
        val maxXPx = remember(hostWidthPx, playerWidthPx) {
            (hostWidthPx - playerWidthPx).coerceAtLeast(0)
        }
        val maxYPx = remember(hostHeightPx, playerHeightPx) {
            (hostHeightPx - playerHeightPx).coerceAtLeast(0)
        }
        var offsetX by rememberSaveable { mutableStateOf(Float.NaN) }
        var offsetY by rememberSaveable { mutableStateOf(Float.NaN) }
        val dragState = rememberDraggable2DState { delta ->
            offsetX = (offsetX + delta.x).coerceIn(0f, maxXPx.toFloat())
            offsetY = (offsetY + delta.y).coerceIn(0f, maxYPx.toFloat())
        }

        LaunchedEffect(maxXPx, maxYPx) {
            offsetX = if (offsetX.isNaN()) {
                maxXPx.toFloat()
            } else {
                offsetX.coerceIn(0f, maxXPx.toFloat())
            }
            offsetY = if (offsetY.isNaN()) {
                maxYPx.toFloat()
            } else {
                offsetY.coerceIn(0f, maxYPx.toFloat())
            }
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = if (offsetX.isNaN()) maxXPx.toFloat() else offsetX
                    translationY = if (offsetY.isNaN()) maxYPx.toFloat() else offsetY
                }
                .width(miniPlayerWidth)
                .aspectRatio(miniPlayerAspectRatio)
                .draggable2D(state = dragState)
        ) {
            content()
        }
    }
}
