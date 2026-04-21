package com.naaammme.bbspace.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.naaammme.bbspace.feature.danmaku.DanmakuLayer
import com.naaammme.bbspace.feature.danmaku.rememberDanmakuOverlayState
import com.naaammme.bbspace.core.model.PlaybackAudio
import com.naaammme.bbspace.core.model.PlaybackViewState
import com.naaammme.bbspace.core.model.QualityOption
import com.naaammme.bbspace.feature.video.model.VideoViewModel
import kotlinx.coroutines.delay

@Suppress("UnsafeOptInUsageError")
@UnstableApi
@Composable
internal fun VideoPlayerPane(
    modifier: Modifier,
    viewModel: VideoViewModel,
    isFull: Boolean,
    onToggleFull: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val danmakuState by viewModel.danmakuState.collectAsStateWithLifecycle()
    val tapSrc = remember { MutableInteractionSource() }
    var showQ by remember { mutableStateOf(false) }
    var showA by remember { mutableStateOf(false) }
    var showSp by remember { mutableStateOf(false) }
    var showPlaybackSheet by remember { mutableStateOf(false) }
    var showCtrl by remember { mutableStateOf(true) }
    var dragMs by remember { mutableStateOf<Long?>(null) }
    var livePosMs by remember(player) {
        mutableStateOf(0L)
    }

    LaunchedEffect(showCtrl, state.isPlaying, dragMs, showA, showQ, showSp, showPlaybackSheet) {
        if (
            showCtrl &&
            state.isPlaying &&
            dragMs == null &&
            !showA &&
            !showQ &&
            !showSp &&
            !showPlaybackSheet
        ) {
            delay(3_000)
            showCtrl = false
        }
    }

    LaunchedEffect(
        player,
        showCtrl,
        dragMs,
        state.isPlaying,
        state.seekEventId,
        state.hasRenderedFirstFrame
    ) {
        val curPlayer = player
        if (!showCtrl || dragMs != null || curPlayer == null) {
            livePosMs = state.positionMs
            return@LaunchedEffect
        }
        do {
            livePosMs = curPlayer.currentPosition.coerceAtLeast(0L)
            if (!state.isPlaying) break
            delay(200)
        } while (showCtrl && dragMs == null)
    }

    val durationMs = player?.duration
        ?.takeIf { it > 0L }
        ?: state.durationMs
        .takeIf { it > 0 }
        ?: state.playbackSource?.durationMs?.coerceAtLeast(0L)
        ?: 0L
    val danmakuOn = settingsState.danmaku.enabled
    val barMs = dragMs ?: livePosMs
    val sliderVal = if (durationMs > 0) {
        (barMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val playerView = remember(context) {
        PlayerView(context).apply {
            useController = false
            setKeepContentOnPlayerReset(true)
            setEnableComposeSurfaceSyncWorkaround(true)
        }
    }
    val danmakuOverlayState = rememberDanmakuOverlayState(
        initialConfig = settingsState.danmaku,
        initialPositionMs = state.positionMs,
        initialIsPlaying = state.isPlaying,
        initialSpeed = state.speed,
        onDanmakuTick = viewModel::onDanmakuTick
    )
    var lastWarmAspect by remember(playerView) { mutableStateOf<Float?>(null) }

    DisposableEffect(playerView) {
        onDispose {
            playerView.player = null
        }
    }

    Box(
        modifier = modifier.background(Color.Black)
    ) {
        AndroidView(
            factory = { playerView },
            update = { view ->
                val content = view.findViewById<AspectRatioFrameLayout>(Media3UiR.id.exo_content_frame)
                val nextWarmAspect = state.expectedVideoAspect()
                if (content != null && lastWarmAspect != nextWarmAspect) {
                    content.setAspectRatio(nextWarmAspect ?: 0f)
                    lastWarmAspect = nextWarmAspect
                }
                if (view.player !== player) {
                    view.player = player
                }
                view.keepScreenOn = state.playWhenReady
            },
            modifier = Modifier.fillMaxSize()
        )

        DanmakuLayer(
            playerView = playerView,
            overlayState = danmakuOverlayState,
            danmakuState = danmakuState,
            danmakuConfig = settingsState.danmaku,
            positionMs = state.positionMs,
            isPlaying = state.isPlaying,
            speed = state.speed,
            seekEventId = state.seekEventId,
            hasSource = state.playbackSource != null
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = tapSrc,
                    indication = null
                ) {
                    showCtrl = !showCtrl
                }
        )

        if (showCtrl) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = if (settingsState.danmaku.enabled) 0.42f else 0.26f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                showCtrl = true
                                viewModel.updateDanmaku(
                                    settingsState.danmaku.copy(enabled = !settingsState.danmaku.enabled)
                                )
                            }
                    ) {
                        Text(
                            text = if (danmakuOn) "弹幕" else "弹幕关",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
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
    }

    if (showQ) {
        val src = state.playbackSource
        if (src != null) {
            QualitySelectionDialog(
                options = src.qualityOptions,
                curQuality = state.currentStream?.quality,
                onDismiss = { showQ = false },
                onSelect = { quality ->
                    viewModel.switchQuality(quality)
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

    if (showPlaybackSheet) {
        VideoPlaybackSheet(
            state = state,
            viewModel = viewModel,
            limitUnderPlayer = !isFull,
            onDismiss = { showPlaybackSheet = false }
        )
    }
}

private fun PlaybackViewState.expectedVideoAspect(): Float? {
    val width = videoWidth.takeIf { it > 0 }
        ?: currentStream?.width?.takeIf { it > 0 }
    val height = videoHeight.takeIf { it > 0 }
        ?: currentStream?.height?.takeIf { it > 0 }
    if (width == null || height == null || width <= 0 || height <= 0) return null
    return width.toFloat() / height.toFloat()
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
            Column(modifier = Modifier.fillMaxWidth()) {
                options.forEach { option ->
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
            Column(modifier = Modifier.fillMaxWidth()) {
                audios.forEach { audio ->
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
            Column(modifier = Modifier.fillMaxWidth()) {
                speedOps.forEach { speed ->
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

