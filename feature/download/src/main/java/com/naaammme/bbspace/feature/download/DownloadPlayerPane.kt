package com.naaammme.bbspace.feature.download

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.naaammme.bbspace.core.model.DanmakuConfig
import com.naaammme.bbspace.core.model.PlayerSettingsState
import com.naaammme.bbspace.feature.danmaku.DanmakuLayer
import com.naaammme.bbspace.feature.danmaku.DanmakuSettingsSection
import com.naaammme.bbspace.feature.danmaku.rememberDanmakuOverlayState
import com.naaammme.bbspace.feature.download.model.DownloadPlayerViewModel
import kotlinx.coroutines.delay
import java.util.Locale

private val noOpDanmakuTick: (Long) -> Unit = {}
private val downloadSpeedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Suppress("UnsafeOptInUsageError")
@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadPlayerPane(
    modifier: Modifier,
    viewModel: DownloadPlayerViewModel,
    isFull: Boolean,
    onToggleFull: () -> Unit,
    onBackClick: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val danmakuState by viewModel.danmakuState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val tapSource = remember { MutableInteractionSource() }
    var showControls by remember { mutableStateOf(true) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableStateOf<Long?>(null) }
    var livePositionMs by remember(player) { mutableStateOf(0L) }

    LaunchedEffect(showControls, state.isPlaying, dragPositionMs, showSpeedDialog, showSettingsSheet) {
        if (showControls && state.isPlaying && dragPositionMs == null && !showSpeedDialog && !showSettingsSheet) {
            delay(3_000)
            showControls = false
        }
    }

    LaunchedEffect(player, showControls, dragPositionMs, state.isPlaying, state.seekEventId) {
        val currentPlayer = player
        if (!showControls || dragPositionMs != null || currentPlayer == null) {
            livePositionMs = state.positionMs
            return@LaunchedEffect
        }
        do {
            livePositionMs = currentPlayer.currentPosition.coerceAtLeast(0L)
            if (!state.isPlaying) break
            delay(200)
        } while (showControls && dragPositionMs == null)
    }

    val durationMs = player?.duration?.takeIf { it > 0L } ?: state.durationMs.coerceAtLeast(0L)
    val barPositionMs = dragPositionMs ?: livePositionMs
    val sliderValue = if (durationMs > 0L) {
        (barPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val danmakuConfig = settingsState.danmaku
    val playerView = remember(context) {
        PlayerView(context).apply {
            useController = false
            setKeepContentOnPlayerReset(true)
            setEnableComposeSurfaceSyncWorkaround(true)
        }
    }
    val danmakuOverlayState = rememberDanmakuOverlayState(
        initialConfig = danmakuConfig,
        initialPositionMs = state.positionMs,
        initialIsPlaying = state.isPlaying,
        initialSpeed = state.speed,
        onDanmakuTick = noOpDanmakuTick
    )

    DisposableEffect(playerView) {
        onDispose {
            playerView.player = null
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { playerView },
            update = { view ->
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
            danmakuConfig = danmakuConfig,
            positionMs = state.positionMs,
            isPlaying = state.isPlaying,
            speed = state.speed,
            seekEventId = state.seekEventId,
            hasSource = state.taskId != null
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = tapSource,
                    indication = null
                ) {
                    showControls = !showControls
                }
        )

        if (showControls) {
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
                IconButton(
                    onClick = {
                        showControls = true
                        showSettingsSheet = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多设置",
                        tint = Color.White
                    )
                }
            }
        }

        if (showControls) {
            DownloadPlayerControls(
                playText = if (state.isPlaying) "暂停" else "播放",
                timeText = formatDownloadPlaybackTime(barPositionMs, durationMs),
                danmakuText = if (danmakuConfig.enabled) "弹幕" else "弹幕关",
                speedText = formatDownloadSpeed(state.speed),
                fullText = if (isFull) "还原" else "全屏",
                sliderValue = sliderValue,
                sliderEnabled = durationMs > 0L,
                onTogglePlay = viewModel::togglePlayPause,
                onToggleDanmaku = {
                    showControls = true
                    viewModel.updateDanmaku(danmakuConfig.copy(enabled = !danmakuConfig.enabled))
                },
                onSpeedClick = {
                    showControls = true
                    showSpeedDialog = true
                },
                onFullClick = {
                    showControls = true
                    onToggleFull()
                },
                onSeekChange = { fraction ->
                    showControls = true
                    dragPositionMs = (durationMs * fraction).toLong()
                },
                onSeekDone = {
                    val next = dragPositionMs ?: return@DownloadPlayerControls
                    viewModel.seekTo(next)
                    dragPositionMs = null
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }

    if (showSpeedDialog) {
        DownloadSpeedDialog(
            currentSpeed = state.speed,
            onDismiss = { showSpeedDialog = false },
            onSelect = { speed ->
                viewModel.setSpeed(speed)
                showSpeedDialog = false
            }
        )
    }

    if (showSettingsSheet) {
        DownloadPlayerSettingsSheet(
            settingsState = settingsState,
            onDismiss = { showSettingsSheet = false },
            onDanmakuConfigChange = viewModel::updateDanmaku,
            onBackgroundPlaybackChange = viewModel::updateBackgroundPlayback
        )
    }
}

@Composable
private fun DownloadPlayerControls(
    playText: String,
    timeText: String,
    danmakuText: String,
    speedText: String,
    fullText: String,
    sliderValue: Float,
    sliderEnabled: Boolean,
    onTogglePlay: () -> Unit,
    onToggleDanmaku: () -> Unit,
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
                value = sliderValue,
                onValueChange = onSeekChange,
                onValueChangeFinished = onSeekDone,
                enabled = sliderEnabled,
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
                DownloadControlButton(
                    text = playText,
                    onClick = onTogglePlay,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = timeText,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.widthIn(min = 88.dp)
                )
                DownloadControlButton(
                    text = danmakuText,
                    onClick = onToggleDanmaku,
                    modifier = Modifier.weight(1f)
                )
                DownloadControlButton(
                    text = speedText,
                    onClick = onSpeedClick,
                    modifier = Modifier.weight(1f)
                )
                DownloadControlButton(
                    text = fullText,
                    onClick = onFullClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DownloadControlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 28.dp)
            .background(Color.White.copy(alpha = 0.14f), MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DownloadSpeedDialog(
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onSelect: (Float) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放速度") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                downloadSpeedOptions.forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(speed) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDownloadSpeed(speed),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (speed == currentSpeed) {
                            Text(
                                text = "当前",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadPlayerSettingsSheet(
    settingsState: PlayerSettingsState,
    onDismiss: () -> Unit,
    onDanmakuConfigChange: (DanmakuConfig) -> Unit,
    onBackgroundPlaybackChange: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DownloadSwitchCard(
                title = "后台播放",
                subtitle = "退出页面或切到后台后继续播放",
                checked = settingsState.playback.backgroundPlayback,
                onCheckedChange = onBackgroundPlaybackChange
            )
            DanmakuSettingsSection(
                config = settingsState.danmaku,
                onConfigChange = onDanmakuConfigChange
            )
        }
    }
}

@Composable
private fun DownloadSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

internal fun formatDownloadPlaybackTime(
    positionMs: Long,
    durationMs: Long
): String {
    return "${formatDownloadDuration(positionMs)} / ${formatDownloadDuration(durationMs)}"
}

private fun formatDownloadDuration(valueMs: Long): String {
    val totalSec = (valueMs / 1_000L).coerceAtLeast(0L)
    val hour = totalSec / 3600L
    val minute = (totalSec % 3600L) / 60L
    val second = totalSec % 60L
    return if (hour > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hour, minute, second)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minute, second)
    }
}

internal fun formatDownloadSpeed(value: Float): String {
    val formatted = if (value % 1f == 0f) {
        String.format(Locale.ROOT, "%.0f", value)
    } else {
        String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
    }
    return "${formatted}x"
}
