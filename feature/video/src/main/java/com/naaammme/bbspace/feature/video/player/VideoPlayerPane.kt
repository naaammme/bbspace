package com.naaammme.bbspace.feature.video.player

import android.media.AudioManager
import android.os.BatteryManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.R as Media3UiR
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.naaammme.bbspace.infra.player.danmaku.DanmakuLayer
import com.naaammme.bbspace.infra.player.danmaku.DanmakuOverlayState
import com.naaammme.bbspace.infra.player.danmaku.rememberDanmakuOverlayState
import com.naaammme.bbspace.infra.player.PlayerViewTargetBinder
import com.naaammme.bbspace.core.model.PlaybackAudio
import com.naaammme.bbspace.core.model.PlaybackStream
import com.naaammme.bbspace.core.model.QualityOption
import com.naaammme.bbspace.feature.video.detail.QualityOptionItem
import com.naaammme.bbspace.feature.video.VideoViewModel
import com.naaammme.bbspace.feature.video.formatPlaybackTime
import com.naaammme.bbspace.feature.video.formatSpeed
import com.naaammme.bbspace.feature.video.getAudioName
import com.naaammme.bbspace.feature.video.getQualityName
import com.naaammme.bbspace.feature.video.speedOps
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class PlayerVideoResizeMode {
    Fit,
    Zoom,
    Fill
}

internal val LocalVideoResizeModeState = compositionLocalOf<MutableState<PlayerVideoResizeMode>> {
    error("Missing player video resize mode state")
}

private const val HIGH_QUALITY_QN = 80
private const val AV1_CODEC_ID = 13
private const val HIGH_QUALITY_CODEC_TIP = "高画质下建议把首选编码改成 AV1"

@Suppress("UnsafeOptInUsageError")
@UnstableApi
@Composable
internal fun VideoPlayerPane(
    modifier: Modifier,
    viewModel: VideoViewModel,
    videoTitle: String?,
    isFull: Boolean,
    onToggleFull: () -> Unit,
    onBackClick: () -> Unit,
    danmakuOverlayState: DanmakuOverlayState? = null
) {
    val context = LocalContext.current
    val timeFmt = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val danmakuState by viewModel.danmakuState.collectAsStateWithLifecycle()
    val gestureState = remember { VideoGestureState() }
    var dragStartBrightness by remember { mutableFloatStateOf(0.5f) }
    var dragStartVolumeFrac by remember { mutableFloatStateOf(0f) }
    var showQ by remember { mutableStateOf(false) }
    var showA by remember { mutableStateOf(false) }
    var showSp by remember { mutableStateOf(false) }
    var showPlaybackSheet by remember { mutableStateOf(false) }
    var showCtrl by remember { mutableStateOf(true) }
    var dragMs by remember { mutableStateOf<Long?>(null) }
    val videoResizeMode = rememberSaveable { mutableStateOf(PlayerVideoResizeMode.Fit) }
    val topMetaText = remember(showCtrl, isFull, context, timeFmt) {
        if (showCtrl && isFull) {
            readPlayerTopMetaText(context, timeFmt)
        } else {
            null
        }
    }
    var speedBeforeGesture by remember { mutableFloatStateOf(1f) }
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    var lastGestureBrightness by remember { mutableFloatStateOf(Float.NaN) }
    var lastGestureVolumeFrac by remember { mutableFloatStateOf(Float.NaN) }

    LaunchedEffect(showCtrl, state.isPlaying, dragMs, gestureState.dragType, gestureState.showSpeedBadge, showA, showQ, showSp, showPlaybackSheet) {
        if (
            showCtrl &&
            state.isPlaying &&
            dragMs == null &&
            gestureState.dragType == DragType.None &&
            !gestureState.showSpeedBadge &&
            !showA &&
            !showQ &&
            !showSp &&
            !showPlaybackSheet
        ) {
            delay(3_000)
            showCtrl = false
        }
    }

    val durationMs = player?.duration
        ?.takeIf { it > 0L }
        ?: state.durationMs
        .takeIf { it > 0 }
        ?: state.playbackSource?.durationMs?.coerceAtLeast(0L)
        ?: 0L
    val danmakuOn = settingsState.danmaku.enabled
    val gestureSeekMs = gestureState.dragSeekPosMs
    val barMs = dragMs ?: gestureSeekMs ?: state.positionMs
    val sliderVal = if (durationMs > 0) {
        (barMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val videoAspect = remember(state.currentStream) {
        val width = state.currentStream?.width?.takeIf { it > 0 } ?: return@remember null
        val height = state.currentStream?.height?.takeIf { it > 0 } ?: return@remember null
        width.toFloat() / height.toFloat()
    }
    val resizeMode = remember(isFull, videoResizeMode.value) {
        if (!isFull) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        } else {
            when (videoResizeMode.value) {
                PlayerVideoResizeMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                PlayerVideoResizeMode.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                PlayerVideoResizeMode.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
        }
    }
    val playerView = remember(context) {
        PlayerView(context).apply {
            useController = false
            setEnableComposeSurfaceSyncWorkaround(true)
            setKeepContentOnPlayerReset(true)
        }
    }
    val externalOverlay = danmakuOverlayState
    val localOverlayState = if (danmakuOn && externalOverlay == null) {
        rememberDanmakuOverlayState(
            initialConfig = settingsState.danmaku,
            initialPositionMs = state.positionMs,
            initialIsPlaying = state.isPlaying,
            initialSpeed = state.speed
        )
    } else {
        null
    }
    val activeOverlayState = externalOverlay ?: localOverlayState
    var lastWarmAspect by remember(playerView) { mutableStateOf<Float?>(null) }

    LaunchedEffect(state.playWhenReady) {
        playerView.keepScreenOn = state.playWhenReady
    }

    DisposableEffect(playerView) {
        onDispose {
            PlayerViewTargetBinder.unbind(playerView)
        }
    }

    CompositionLocalProvider(LocalVideoResizeModeState provides videoResizeMode) {
        Box(
            modifier = modifier
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { playerView },
                update = { view ->
                    val content = view.findViewById<AspectRatioFrameLayout>(Media3UiR.id.exo_content_frame)
                    if (content != null) {
                        content.resizeMode = resizeMode
                        if (lastWarmAspect != videoAspect) {
                            content.setAspectRatio(videoAspect ?: 0f)
                            lastWarmAspect = videoAspect
                        }
                    }
                    PlayerViewTargetBinder.bind(view, player)
                },
                modifier = Modifier.fillMaxSize()
            )

            if (activeOverlayState != null) {
                DanmakuLayer(
                    playerView = playerView,
                    overlayState = activeOverlayState,
                    danmakuState = danmakuState,
                    danmakuConfig = settingsState.danmaku,
                    positionMs = state.positionMs,
                    isPlaying = state.isPlaying,
                    speed = state.speed,
                    seekEventId = state.seekEventId,
                    hasSource = state.playbackSource != null,
                    manageLifecycle = externalOverlay == null
                )
            }

            val act = remember(context) { context.findActivity() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .videoGestures(
                        state = gestureState,
                        onToggleControls = {
                            showCtrl = !showCtrl
                        },
                        onTogglePlay = viewModel::togglePlayPause,
                        onSeekTo = viewModel::seekTo,
                        onStartSpeedUp = {
                            speedBeforeGesture = state.speed
                            val speed = settingsState.playback.gestureSpeed
                            viewModel.setSpeed(speed)
                            formatSpeed(speed)
                        },
                        onStopSpeedUp = { viewModel.setSpeed(speedBeforeGesture) },
                        onBrightnessDelta = { deltaFrac ->
                            val next = (dragStartBrightness + deltaFrac).coerceIn(0.01f, 1f)
                            if (abs(next - lastGestureBrightness) >= 0.02f || lastGestureBrightness.isNaN()) {
                                adjustWindowBrightness(act, next)
                                lastGestureBrightness = next
                            }
                            next
                        },
                        onVolumeDelta = { deltaFrac ->
                            val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
                            if (maxVol <= 0) {
                                0f
                            } else {
                                val next = (dragStartVolumeFrac + deltaFrac).coerceIn(0f, 1f)
                                if (abs(next - lastGestureVolumeFrac) >= (1f / maxVol.toFloat()) || lastGestureVolumeFrac.isNaN()) {
                                    val newVol = (next * maxVol).roundToInt()
                                    adjustStreamVolume(audioManager, newVol)
                                    lastGestureVolumeFrac = next
                                }
                                next
                            }
                        },
                        onDragStart = { dragType ->
                            when (dragType) {
                                DragType.Brightness -> {
                                    dragStartBrightness = act?.window?.attributes?.screenBrightness
                                        ?.takeIf { it in 0f..1f }
                                        ?.coerceIn(0.01f, 1f)
                                        ?: 0.5f
                                    lastGestureBrightness = Float.NaN
                                }
                                DragType.Volume -> {
                                    val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
                                    val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                                    dragStartVolumeFrac = if (maxVol > 0) {
                                        curVol.toFloat() / maxVol.toFloat()
                                    } else {
                                        0f
                                    }
                                    lastGestureVolumeFrac = Float.NaN
                                }
                                else -> {}
                            }
                        },
                        isPlaying = { state.isPlaying },
                        positionMs = { barMs },
                        durationMs = { durationMs }
                    )
            ) {
                VideoGestureFeedback(gestureState)
            }

            if (showCtrl) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.54f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White
                            )
                        }
                        if (isFull) {
                            Column(modifier = Modifier.weight(1f)) {
                                videoTitle?.takeIf(String::isNotBlank)?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                topMetaText?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.8f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (danmakuOn) "弹幕" else "弹幕关",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier
                                .clickable {
                                    showCtrl = true
                                    viewModel.updateDanmaku(
                                        settingsState.danmaku.copy(enabled = !settingsState.danmaku.enabled)
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                        IconButton(
                            onClick = {
                                showCtrl = true
                                showPlaybackSheet = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多信息",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            if (showCtrl) {
                PlayerCtrlBar(
                    playText = if (state.isPlaying) "暂停" else "播放",
                    timeText = formatPlaybackTime(barMs, durationMs),
                    audioText = state.currentAudio?.let { getAudioName(it.id, short = true) } ?: "音频",
                    qualityText = getQualityName(state.playbackSource, state.currentStream),
                    speedText = formatSpeed(state.speed),
                    fullText = if (isFull) "还原" else "全屏",
                    sliderVal = sliderVal,
                    sliderOn = durationMs > 0,
                    audioOn = (state.playbackSource?.audios?.size ?: 0) > 1,
                    qualityOn = (state.playbackSource?.qualityOptions?.size ?: 0) > 1,
                    onTogglePlay = viewModel::togglePlayPause,
                    onAudioClick = {
                        showCtrl = true
                        showA = true
                    },
                    onQualityClick = {
                        showCtrl = true
                        showQ = true
                    },
                    onSpeedClick = {
                        showCtrl = true
                        showSp = true
                    },
                    onFullClick = {
                        showCtrl = true
                        onToggleFull()
                    },
                    onSeekChange = { frac ->
                        showCtrl = true
                        dragMs = (durationMs * frac).toLong()
                    },
                    onSeekDone = {
                        val next = dragMs ?: return@PlayerCtrlBar
                        viewModel.seekTo(next)
                        dragMs = null
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            if (isFull && showPlaybackSheet) {
                VideoPlaybackSidebar(
                    state = state,
                    viewModel = viewModel,
                    onDismiss = { showPlaybackSheet = false },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxSize()
                )
            }
        }

        if (showQ) {
            val src = state.playbackSource
            if (src != null) {
                QualitySelectionDialog(
                    options = src.qualityOptions,
                    curQuality = state.currentStream?.quality,
                    onDismiss = { showQ = false },
                    onSelect = { quality ->
                        val codecId = (src.streams.firstOrNull { it.quality == quality } as? PlaybackStream.Dash)?.codecId
                        viewModel.switchQuality(quality)
                        if (quality > HIGH_QUALITY_QN && codecId != null && codecId != AV1_CODEC_ID) {
                            Toast.makeText(context, HIGH_QUALITY_CODEC_TIP, Toast.LENGTH_SHORT).show()
                        }
                        showQ = false
                    }
                )
            }
        }

        if (showA) {
            val src = state.playbackSource
            if (src != null) {
                AudioSelectionDialog(
                    audios = src.audios,
                    curAudioId = state.currentAudio?.id,
                    onDismiss = { showA = false },
                    onSelect = { audioId ->
                        viewModel.switchAudio(audioId)
                        showA = false
                    }
                )
            }
        }

        if (showSp) {
            SpeedSelectionDialog(
                curSpeed = state.speed,
                onDismiss = { showSp = false },
                onSelect = { speed ->
                    viewModel.setSpeed(speed)
                    showSp = false
                }
            )
        }

        if (!isFull && showPlaybackSheet) {
            VideoPlaybackSheet(
                state = state,
                viewModel = viewModel,
                limitUnderPlayer = true,
                onDismiss = { showPlaybackSheet = false }
            )
        }
    }
}

@Composable
private fun PlayerCtrlBar(
    playText: String,
    timeText: String,
    audioText: String,
    qualityText: String,
    speedText: String,
    fullText: String,
    sliderVal: Float,
    sliderOn: Boolean,
    audioOn: Boolean,
    qualityOn: Boolean,
    onTogglePlay: () -> Unit,
    onAudioClick: () -> Unit,
    onQualityClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onFullClick: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.54f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Slider(
                value = sliderVal,
                onValueChange = onSeekChange,
                onValueChangeFinished = onSeekDone,
                enabled = sliderOn,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                    disabledThumbColor = Color.White.copy(alpha = 0.24f),
                    disabledActiveTrackColor = Color.White.copy(alpha = 0.16f),
                    disabledInactiveTrackColor = Color.White.copy(alpha = 0.1f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .heightIn(min = 24.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CtrlBtn(
                    text = playText,
                    on = true,
                    onClick = onTogglePlay,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = timeText,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.widthIn(min = 64.dp)
                )
                CtrlBtn(
                    text = audioText,
                    on = audioOn,
                    onClick = onAudioClick,
                    modifier = Modifier.weight(1f)
                )
                CtrlBtn(
                    text = qualityText,
                    on = qualityOn,
                    onClick = onQualityClick,
                    modifier = Modifier.weight(1f)
                )
                CtrlBtn(
                    text = speedText,
                    on = true,
                    onClick = onSpeedClick,
                    modifier = Modifier.weight(1f)
                )
                CtrlBtn(
                    text = fullText,
                    on = true,
                    onClick = onFullClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CtrlBtn(
    text: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (on) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }
    val fg = if (on) {
        Color.White
    } else {
        Color.White.copy(alpha = 0.45f)
    }

    Box(
        modifier = modifier
            .heightIn(min = 28.dp)
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .clickable(enabled = on, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QualitySelectionDialog(
    options: List<QualityOption>,
    curQuality: Int?,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择画质") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(options, key = { it.quality }) { option ->
                    QualityOptionItem(
                        option = option,
                        isSelected = option.quality == curQuality,
                        onClick = { onSelect(option.quality) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun AudioSelectionDialog(
    audios: List<PlaybackAudio>,
    curAudioId: Int?,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择音频") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(audios, key = { it.id }) { audio ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(audio.id) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = getAudioName(audio.id),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (audio.id == curAudioId) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun SpeedSelectionDialog(
    curSpeed: Float,
    onDismiss: () -> Unit,
    onSelect: (Float) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放速度") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(speedOps, key = { it }) { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(speed) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatSpeed(speed),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (speed == curSpeed) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun readPlayerTopMetaText(
    context: android.content.Context,
    timeFmt: java.text.DateFormat
): String {
    val time = timeFmt.format(System.currentTimeMillis())
    val battery = context.getSystemService(BatteryManager::class.java)
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 }
    return if (battery != null) "$time  ${battery}%" else time
}

