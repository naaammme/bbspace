package com.naaammme.bbspace.playback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val miniPlayerDefaultWidth = 192.dp
private val miniPlayerMinWidth = 120.dp
private const val miniPlayerDefaultAspectRatio = 16f / 9f
private const val miniPlayerAspectRatioMin = 1f / 3f
private const val miniPlayerAspectRatioMax = 3f
private val miniPlayerStashPeek = 28.dp
private const val miniPlayerStashTriggerFraction = 0.5f
private const val miniPlayerSettleVelocityThreshold = 1800f
private val miniPlayerBottomGap = 12.dp
private val miniPlayerEdgePullHandle = 32.dp

/**
 * 进程级记忆小窗几何状态：小窗关闭/回到播放页后再次出现时，
 * 恢复上一次的宽度和位置（坐标为像素，方向切换等场景由夹取逻辑兜底）
 */
private object MiniPlayerGeometryMemory {
    val widthDp = mutableFloatStateOf(miniPlayerDefaultWidth.value)
    val offsetX = mutableFloatStateOf(Float.NaN)
    val offsetY = mutableFloatStateOf(Float.NaN)
}

private class SettleJobHolder {
    var job: Job? = null
}

@Composable
internal fun DraggableMiniPlayerHost(
    videoAspectRatio: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // 小窗比例跟随视频实际画面比例（未渲染首帧或异常值时回退 16:9）
    val aspectRatio = if (videoAspectRatio.isFinite() && videoAspectRatio > 0f) {
        videoAspectRatio.coerceIn(miniPlayerAspectRatioMin, miniPlayerAspectRatioMax)
    } else {
        miniPlayerDefaultAspectRatio
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val density = LocalDensity.current
        val layoutDir = LocalLayoutDirection.current
        val scope = rememberCoroutineScope()
        val decay = rememberSplineBasedDecay<Float>()
        // 小窗宽度可双指缩放调整；宽高位置在进程内保留，小窗重新出现时恢复
        var playerWidth by MiniPlayerGeometryMemory.widthDp
        var offsetX by MiniPlayerGeometryMemory.offsetX
        var offsetY by MiniPlayerGeometryMemory.offsetY
        // 上限随屏幕方向与视频比例重算：宽、高都不能超过宿主（屏幕）尺寸
        val maxPlayerWidthDp = minOf(maxWidth, maxHeight * aspectRatio)
        val playerWidthDp = playerWidth.dp.coerceIn(miniPlayerMinWidth, maxPlayerWidthDp)
        val playerWidthPx = with(density) { playerWidthDp.roundToPx() }
        val playerHeightPx = remember(playerWidthPx, aspectRatio) {
            (playerWidthPx / aspectRatio).roundToInt()
        }
        val hostWidthPx = with(density) { maxWidth.roundToPx() }
        val hostHeightPx = with(density) { maxHeight.roundToPx() }
        val safeInsets = WindowInsets.safeDrawing
        val safeLeftPx = with(density) { safeInsets.getLeft(this, layoutDir) }
        val safeRightPx = with(density) { safeInsets.getRight(this, layoutDir) }
        val safeTopPx = with(density) { safeInsets.getTop(this) }
        val safeBottomPx = with(density) { safeInsets.getBottom(this) }
        val bottomGapPx = with(density) { miniPlayerBottomGap.roundToPx() }
        val minVisibleXPx = safeLeftPx.toFloat()
        val maxVisibleXPx = remember(hostWidthPx, safeRightPx, playerWidthPx, minVisibleXPx) {
            (hostWidthPx - safeRightPx - playerWidthPx).toFloat().coerceAtLeast(minVisibleXPx)
        }
        val minYPx = safeTopPx.toFloat()
        val maxYPxFloat = remember(hostHeightPx, safeBottomPx, bottomGapPx, playerHeightPx, minYPx) {
            (hostHeightPx - safeBottomPx - bottomGapPx - playerHeightPx).toFloat().coerceAtLeast(minYPx)
        }
        val initialYOffsetPx = remember(maxYPxFloat, minYPx, density) {
            (maxYPxFloat - with(density) { 60.dp.roundToPx().toFloat() }).coerceAtLeast(minYPx)
        }
        val stashPeekPx = with(density) { miniPlayerStashPeek.roundToPx() }
        val edgePullHandlePx = with(density) { miniPlayerEdgePullHandle.roundToPx().toFloat() }
        val stashOffsetPx = remember(playerWidthPx, stashPeekPx) {
            (playerWidthPx - stashPeekPx).coerceAtLeast(0).toFloat()
        }
        val stashTriggerPx = playerWidthPx * miniPlayerStashTriggerFraction
        val minXPx = minVisibleXPx - stashOffsetPx
        val maxStashedXPx = maxVisibleXPx + stashOffsetPx
        var isSettling by remember { mutableStateOf(false) }
        val settleJobHolder = remember { SettleJobHolder() }
        val dragState = rememberDraggable2DState { delta ->
            offsetX = (offsetX + delta.x).coerceIn(minXPx, maxStashedXPx)
            offsetY = (offsetY + delta.y).coerceIn(minYPx, maxYPxFloat)
        }

        LaunchedEffect(minVisibleXPx, maxVisibleXPx, minYPx, maxYPxFloat, minXPx, maxStashedXPx) {
            settleJobHolder.job?.cancel()
            isSettling = false
            offsetX = if (offsetX.isNaN()) {
                maxVisibleXPx
            } else {
                offsetX.coerceIn(minXPx, maxStashedXPx)
            }
            offsetY = if (offsetY.isNaN()) {
                initialYOffsetPx
            } else {
                offsetY.coerceIn(minYPx, maxYPxFloat)
            }
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = if (offsetX.isNaN()) maxVisibleXPx else offsetX
                    translationY = if (offsetY.isNaN()) initialYOffsetPx else offsetY
                }
                .systemGestureExclusion { coords ->
                    val width = coords.size.width.toFloat()
                    val height = coords.size.height.toFloat()
                    when {
                        offsetX <= minVisibleXPx -> {
                            val hiddenWidth = (minVisibleXPx - offsetX).coerceIn(0f, width)
                            val visibleWidth = (width - hiddenWidth).coerceAtLeast(0f)
                            val handleWidth = visibleWidth.coerceAtMost(edgePullHandlePx)
                            Rect(
                                left = hiddenWidth,
                                top = 0f,
                                right = hiddenWidth + handleWidth,
                                bottom = height
                            )
                        }

                        offsetX >= maxVisibleXPx -> {
                            val hiddenWidth = (offsetX - maxVisibleXPx).coerceIn(0f, width)
                            val visibleWidth = (width - hiddenWidth).coerceAtLeast(0f)
                            val handleWidth = visibleWidth.coerceAtMost(edgePullHandlePx)
                            Rect(
                                left = width - hiddenWidth - handleWidth,
                                top = 0f,
                                right = width - hiddenWidth,
                                bottom = height
                            )
                        }

                        else -> Rect.Zero
                    }
                }
                .width(playerWidthDp)
                .aspectRatio(aspectRatio)
                .pointerInput(maxPlayerWidthDp) {
                    // 双指捏合缩放小窗（视频比例不变），以两指中心为锚点
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var zooming = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount < 2) {
                                if (zooming && pressedCount > 0) {
                                    // 缩放结束但仍有手指残留，吞掉事件避免触发拖动
                                    event.changes.forEach { it.consume() }
                                }
                                if (pressedCount == 0) break
                                continue
                            }
                            val zoom = event.calculateZoom()
                            if (zooming || zoom != 1f) {
                                zooming = true
                                val minPx = miniPlayerMinWidth.toPx()
                                val maxPx = maxPlayerWidthDp.toPx()
                                // 注意读取实时状态，避免闭包捕获旧尺寸
                                val oldWidthPx = playerWidth.dp
                                    .coerceIn(miniPlayerMinWidth, maxPlayerWidthDp)
                                    .toPx()
                                val oldHeightPx = oldWidthPx / aspectRatio
                                val newWidthPx = (oldWidthPx * zoom).coerceIn(minPx, maxPx)
                                val scale = newWidthPx / oldWidthPx
                                offsetX = (offsetX - oldWidthPx * (scale - 1f) / 2f)
                                    .coerceIn(minXPx, maxStashedXPx)
                                offsetY = (offsetY - oldHeightPx * (scale - 1f) / 2f)
                                    .coerceIn(minYPx, maxYPxFloat)
                                playerWidth = newWidthPx.toDp().value
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .draggable2D(
                    state = dragState,
                    startDragImmediately = isSettling,
                    onDragStarted = {
                        settleJobHolder.job?.cancel()
                        isSettling = false
                    },
                    onDragStopped = { velocity ->
                        settleJobHolder.job?.cancel()
                        settleJobHolder.job = scope.launch {
                            isSettling = true
                            try {
                                val startX = offsetX
                                val startY = offsetY
                                val velX = velocity.x * 0.35f
                                val velY = velocity.y * 0.2f
                                val shouldDecay = abs(velocity.x) >= miniPlayerSettleVelocityThreshold ||
                                    abs(velocity.y) >= miniPlayerSettleVelocityThreshold

                                fun targetX(curX: Float): Float = when {
                                    maxVisibleXPx <= minVisibleXPx -> minVisibleXPx
                                    curX < minVisibleXPx -> {
                                        val hiddenWidth = minVisibleXPx - curX
                                        if (hiddenWidth >= stashTriggerPx) minXPx else minVisibleXPx
                                    }
                                    curX > maxVisibleXPx -> {
                                        val hiddenWidth = curX - maxVisibleXPx
                                        if (hiddenWidth >= stashTriggerPx) maxStashedXPx else maxVisibleXPx
                                    }
                                    else -> {
                                        val distLeft = curX - minVisibleXPx
                                        val distRight = maxVisibleXPx - curX
                                        if (distLeft <= distRight) minVisibleXPx else maxVisibleXPx
                                    }
                                }

                                coroutineScope {
                                    launch {
                                        var curX = startX
                                        var endVelX = velX
                                        if (shouldDecay) {
                                            val animX = Animatable(curX)
                                            animX.updateBounds(lowerBound = minXPx, upperBound = maxStashedXPx)
                                            val result = animX.animateDecay(
                                                initialVelocity = velX,
                                                animationSpec = decay
                                            ) { offsetX = value }
                                            curX = animX.value
                                            endVelX = result.endState.velocity
                                        }
                                        Animatable(curX).animateTo(
                                            targetValue = targetX(curX),
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessMediumLow,
                                                dampingRatio = Spring.DampingRatioNoBouncy
                                            ),
                                            initialVelocity = endVelX
                                        ) { offsetX = value }
                                    }
                                    launch {
                                        var curY = startY
                                        var endVelY = velY
                                        if (shouldDecay) {
                                            val animY = Animatable(curY)
                                            animY.updateBounds(lowerBound = minYPx, upperBound = maxYPxFloat)
                                            val result = animY.animateDecay(
                                                initialVelocity = velY,
                                                animationSpec = decay
                                            ) { offsetY = value }
                                            curY = animY.value
                                            endVelY = result.endState.velocity
                                        }
                                        Animatable(curY).animateTo(
                                            targetValue = curY.coerceIn(minYPx, maxYPxFloat),
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessMediumLow,
                                                dampingRatio = Spring.DampingRatioNoBouncy
                                            ),
                                            initialVelocity = endVelY
                                        ) { offsetY = value }
                                    }
                                }
                            } finally {
                                isSettling = false
                            }
                        }
                    }
                )
        ) {
            content()
        }
    }
}
