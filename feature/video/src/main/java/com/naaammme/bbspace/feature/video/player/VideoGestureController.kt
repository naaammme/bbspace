package com.naaammme.bbspace.feature.video.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.designsystem.icon.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Stable
class VideoGestureState {
    var dragType by mutableStateOf(DragType.None)
        internal set
    var dragFraction by mutableStateOf(0f)
        internal set
    var dragSeekPosMs by mutableStateOf<Long?>(null)
        internal set

    var seekLabel by mutableStateOf<String?>(null)
        internal set
    var showSpeedBadge by mutableStateOf(false)
        internal set
    var speedBadgeText by mutableStateOf("2x")
        internal set
    var doubleTapHint by mutableStateOf<DoubleTapHint?>(null)
        internal set

    internal var doubleTapToken by mutableStateOf(0L)
    internal var dragStartPosMs by mutableStateOf(0L)

    internal fun resetDrag() {
        dragType = DragType.None
        dragFraction = 0f
        dragSeekPosMs = null
        seekLabel = null
    }

    internal fun showDoubleTap(hint: DoubleTapHint) {
        doubleTapHint = hint
        doubleTapToken++
    }
}

enum class DragType { None, Seek, Brightness, Volume }

enum class DoubleTapHint(val text: String) {
    Play("播放"),
    Pause("暂停"),
    Rewind("-10s"),
    Forward("+10s")
}

private const val SEEK_MAX_MS = 60_000L
private const val DOUBLE_TAP_SEEK_MS = 10_000L
private const val DRAG_SENSITIVITY = 0.6f

@Composable
fun Modifier.videoGestures(
    state: VideoGestureState,
    onToggleControls: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onStartSpeedUp: () -> String,
    onStopSpeedUp: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onVolumeDelta: (Float) -> Unit,
    onDragStart: (DragType) -> Unit = {},
    onDragEnd: () -> Unit = {},
    isPlaying: () -> Boolean,
    positionMs: () -> Long,
    durationMs: () -> Long
): Modifier {
    val curToggleControls by rememberUpdatedState(onToggleControls)
    val curTogglePlay by rememberUpdatedState(onTogglePlay)
    val curSeekTo by rememberUpdatedState(onSeekTo)
    val curStartSpeedUp by rememberUpdatedState(onStartSpeedUp)
    val curStopSpeedUp by rememberUpdatedState(onStopSpeedUp)
    val curBrightnessDelta by rememberUpdatedState(onBrightnessDelta)
    val curVolumeDelta by rememberUpdatedState(onVolumeDelta)
    val curDragStart by rememberUpdatedState(onDragStart)
    val curDragEnd by rememberUpdatedState(onDragEnd)
    val curIsPlaying by rememberUpdatedState(isPlaying)
    val curPositionMs by rememberUpdatedState(positionMs)
    val curDurationMs by rememberUpdatedState(durationMs)

    return this.pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        val longPressTimeout = viewConfiguration.longPressTimeoutMillis.toLong()
        val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis.toLong()

        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downTime = down.uptimeMillis
                val downPos = down.position
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                if (w <= 0f || h <= 0f) continue

                val zoneX = downPos.x / w

                var phase = Phase.Press
                var isHorizontal: Boolean? = null
                var totalDelta = Offset.Zero
                var lastPos = downPos
                var longPressFired = false

                while (true) {
                    val event = if (phase == Phase.Press && !longPressFired) {
                        val remaining = (longPressTimeout - (SystemClock.uptimeMillis() - downTime))
                            .coerceAtLeast(0L)
                        withTimeoutOrNull(remaining) {
                            awaitPointerEvent(PointerEventPass.Main)
                        }
                    } else {
                        awaitPointerEvent(PointerEventPass.Main)
                    }
                    if (event == null) {
                        longPressFired = true
                        phase = Phase.LongPress
                        state.speedBadgeText = curStartSpeedUp()
                        state.showSpeedBadge = true
                        continue
                    }
                    val change = event.changes.firstOrNull() ?: break

                    if (!change.pressed) {
                        when (phase) {
                            Phase.Press -> {
                                if (longPressFired) {
                                    curStopSpeedUp()
                                    state.showSpeedBadge = false
                                } else {
                                    val second = withTimeoutOrNull(doubleTapTimeout) {
                                        awaitFirstDown(requireUnconsumed = false)
                                    }
                                    if (second != null) {
                                        val sndX = second.position.x / w
                                        when {
                                            sndX < 0.2f -> {
                                                val target = (curPositionMs() - DOUBLE_TAP_SEEK_MS).coerceAtLeast(0L)
                                                curSeekTo(target)
                                                state.showDoubleTap(DoubleTapHint.Rewind)
                                            }
                                            sndX > 0.7f -> {
                                                val target = (curPositionMs() + DOUBLE_TAP_SEEK_MS).coerceAtMost(curDurationMs())
                                                curSeekTo(target)
                                                state.showDoubleTap(DoubleTapHint.Forward)
                                            }
                                            else -> {
                                                curTogglePlay()
                                                state.showDoubleTap(
                                                    if (curIsPlaying()) DoubleTapHint.Play
                                                    else DoubleTapHint.Pause
                                                )
                                            }
                                        }
                                        waitForAllUp()
                                    } else {
                                        curToggleControls()
                                    }
                                }
                            }
                            Phase.Drag -> {
                                if (state.dragType == DragType.Seek) {
                                    curSeekTo(state.dragSeekPosMs ?: state.dragStartPosMs)
                                }
                                state.resetDrag()
                                curDragEnd()
                            }
                            Phase.LongPress -> {
                                curStopSpeedUp()
                                state.showSpeedBadge = false
                            }
                        }
                        break
                    }

                    val delta = change.position - lastPos
                    totalDelta += delta
                    lastPos = change.position

                    when (phase) {
                        Phase.Press -> {
                            if (totalDelta.getDistance() >= slop) {
                                isHorizontal = abs(totalDelta.x) > abs(totalDelta.y)
                                phase = Phase.Drag
                                val dragType = when {
                                    isHorizontal == true -> DragType.Seek
                                    zoneX < 0.2f -> DragType.Brightness
                                    zoneX > 0.7f -> DragType.Volume
                                    else -> DragType.None
                                }
                                state.dragStartPosMs = curPositionMs()
                                state.dragType = dragType
                                curDragStart(dragType)
                            }
                        }
                        Phase.Drag -> {
                            when (state.dragType) {
                                DragType.Seek -> {
                                    val frac = totalDelta.x / (w * DRAG_SENSITIVITY)
                                    val deltaMs = (frac * SEEK_MAX_MS).roundToLong()
                                    val newPos = (state.dragStartPosMs + deltaMs)
                                        .coerceIn(0L, curDurationMs())
                                    state.dragFraction = frac.coerceIn(-1f, 1f)
                                    state.dragSeekPosMs = newPos
                                    state.seekLabel = formatSeekDelta(deltaMs)
                                }
                                DragType.Brightness -> {
                                    val frac = (-totalDelta.y / (h * DRAG_SENSITIVITY)).coerceIn(-1f, 1f)
                                    state.dragFraction = frac
                                    curBrightnessDelta(frac)
                                }
                                DragType.Volume -> {
                                    val frac = (-totalDelta.y / (h * DRAG_SENSITIVITY)).coerceIn(-1f, 1f)
                                    state.dragFraction = frac
                                    curVolumeDelta(frac)
                                }
                                DragType.None -> {}
                            }
                        }
                        Phase.LongPress -> {
                        }
                    }

                    change.consume()
                }
            }
        }
    }
}

@Composable
fun VideoGestureFeedback(
    state: VideoGestureState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (state.dragType == DragType.Brightness || state.dragType == DragType.Volume) {
            val isBrightness = state.dragType == DragType.Brightness
            val label = if (isBrightness) "亮度" else "音量"
            val barH = (state.dragFraction * 0.5f + 0.5f).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(MaterialTheme.shapes.large)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(100.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(Color.White.copy(alpha = 0.24f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height((100 * barH).dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(Color.White)
                        )
                    }
                }
            }
        }

        if (state.dragType == DragType.Seek && state.seekLabel != null) {
            Text(
                text = state.seekLabel.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        val hint = state.doubleTapHint
        if (hint != null) {
            val token = state.doubleTapToken
            var visible by remember(token) { mutableStateOf(true) }
            LaunchedEffect(token) {
                visible = true
                delay(400)
                visible = false
                state.doubleTapHint = null
            }
            if (visible) {
                if (hint == DoubleTapHint.Play || hint == DoubleTapHint.Pause) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (hint == DoubleTapHint.Pause) AppIcons.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (hint == DoubleTapHint.Pause) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else {
                    Text(
                        text = hint.text,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        if (state.showSpeedBadge) {
            Text(
                text = state.speedBadgeText,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

private enum class Phase { Press, Drag, LongPress }

private suspend fun AwaitPointerEventScope.waitForAllUp() {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        if (event.changes.all { !it.pressed }) return
    }
}

private fun formatSeekDelta(ms: Long): String {
    val totalSec = ms / 1000
    if (totalSec == 0L) return "0s"
    val sign = if (totalSec > 0) "+" else ""
    return "$sign${totalSec}s"
}

internal fun Context.findActivity(): Activity? {
    var ctx = this
    while (true) {
        if (ctx is Activity) return ctx
        ctx = (ctx as? ContextWrapper)?.baseContext ?: return null
    }
}

internal fun adjustWindowBrightness(activity: Activity?, fraction: Float) {
    val win = activity?.window ?: return
    win.attributes = win.attributes.apply {
        screenBrightness = fraction.coerceIn(0.01f, 1f)
    }
}

internal fun adjustStreamVolume(context: Context, fraction: Float, startVolume: Int, maxVolume: Int) {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    val newVol = (startVolume + fraction * maxVolume).roundToInt().coerceIn(0, maxVolume)
    am.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
}
