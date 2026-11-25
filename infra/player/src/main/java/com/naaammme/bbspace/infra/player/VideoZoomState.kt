package com.naaammme.bbspace.infra.player

import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3UiR

private const val MAX_VIDEO_ZOOM = 5f

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerView.rememberVideoContentFrame(): AspectRatioFrameLayout? = remember(this) {
    findViewById(Media3UiR.id.exo_content_frame)
}

@Composable
fun rememberVideoZoomState(
    videoFrame: View?,
    enabled: Boolean,
    player: Player?,
    mediaKey: Any?,
    resizeMode: Int? = null
): VideoZoomState? {
    val zoomState = remember(videoFrame, enabled, player, mediaKey, resizeMode) {
        if (enabled && player != null && videoFrame != null) {
            VideoZoomState(videoFrame)
        } else {
            null
        }
    }
    if (zoomState != null) {
        DisposableEffect(zoomState) {
            zoomState.attach()
            onDispose { zoomState.detach() }
        }
    }
    return zoomState
}

fun Modifier.resetVideoZoomOnSizeChanged(zoomState: VideoZoomState?): Modifier =
    if (zoomState == null) this else onSizeChanged(zoomState.onViewportSizeChanged)

fun Modifier.pinchVideoZoom(zoomState: VideoZoomState?): Modifier {
    if (zoomState == null) return this
    return pointerInput(zoomState) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var transforming = false
            var event = currentEvent
            while (true) {
                if (transforming || event.changes.count { it.pressed } >= 2) {
                    transforming = true
                    zoomState.update(size, event)
                    event.changes.forEach { it.consume() }
                }
                if (event.changes.none { it.pressed }) break
                event = awaitPointerEvent(PointerEventPass.Initial)
            }
        }
    }
}

class VideoZoomState internal constructor(private val videoFrame: View) {
    internal val onViewportSizeChanged: (IntSize) -> Unit = { reset() }
    private val onLayoutChange = View.OnLayoutChangeListener { _, left, top, right, bottom,
        oldLeft, oldTop, oldRight, oldBottom ->
        if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) reset()
    }

    internal fun attach() {
        videoFrame.addOnLayoutChangeListener(onLayoutChange)
    }

    internal fun detach() {
        videoFrame.removeOnLayoutChangeListener(onLayoutChange)
        reset()
    }

    private fun reset() {
        applyTransform(1f, Offset.Zero)
    }

    fun update(viewport: IntSize, event: PointerEvent) {
        if (event.changes.count { it.pressed && it.previousPressed } < 2) return
        val zoom = event.calculateZoom()
        val pan = event.calculatePan()
        if (zoom == 1f && pan == Offset.Zero) return
        if (videoFrame.width <= 0 || videoFrame.height <= 0 || viewport.width <= 0 || viewport.height <= 0) return

        val currentScale = videoFrame.scaleX
        val scale = (currentScale * zoom).coerceIn(1f, MAX_VIDEO_ZOOM)
        if (scale == 1f) {
            if (currentScale != 1f) reset()
            return
        }

        val limitX = ((videoFrame.width * scale - viewport.width) / 2f).coerceAtLeast(0f)
        val limitY = ((videoFrame.height * scale - viewport.height) / 2f).coerceAtLeast(0f)
        val anchor = event.calculateCentroid(useCurrent = false) -
            Offset(viewport.width / 2f, viewport.height / 2f)
        val translation = Offset(videoFrame.translationX, videoFrame.translationY)
        val offset = anchor + (translation - anchor) * (scale / currentScale) + pan
        applyTransform(
            scale,
            Offset(offset.x.coerceIn(-limitX, limitX), offset.y.coerceIn(-limitY, limitY))
        )
    }

    private fun applyTransform(scale: Float, offset: Offset) {
        videoFrame.scaleX = scale
        videoFrame.scaleY = scale
        videoFrame.translationX = offset.x
        videoFrame.translationY = offset.y
    }
}
