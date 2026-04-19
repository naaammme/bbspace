package com.naaammme.bbspace.feature.live

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.naaammme.bbspace.core.model.LivePlaybackViewState
import com.naaammme.bbspace.core.model.LiveQualityOption
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.LiveStatus
import com.naaammme.bbspace.core.model.PlaybackState
import com.naaammme.bbspace.feature.live.model.toUiMessage
import kotlinx.coroutines.delay

@Suppress("UnsafeOptInUsageError")
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun LivePlayerPane(
    route: LiveRoute?,
    player: Player?,
    playbackState: LivePlaybackViewState,
    onTogglePlay: () -> Unit,
    onRetry: () -> Unit,
    onSwitchQuality: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tapSrc = remember { MutableInteractionSource() }
    var showCtrl by remember { mutableStateOf(true) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    val qualityText = playbackState.playbackSource?.currentDescription
        ?.takeIf(String::isNotBlank)
        ?: "画质"
    val playerView = remember(context) {
        PlayerView(context).apply {
            useController = false
            setKeepContentOnPlayerReset(true)
            setEnableComposeSurfaceSyncWorkaround(true)
        }
    }

    LaunchedEffect(
        showCtrl,
        playbackState.isPlaying,
        playbackState.error,
        showQualityDialog,
        showInfoDialog
    ) {
        if (
            showCtrl &&
            playbackState.isPlaying &&
            playbackState.error == null &&
            !showQualityDialog &&
            !showInfoDialog
        ) {
            delay(3_000)
            showCtrl = false
        }
    }

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
                if (view.player !== player) {
                    view.player = player
                }
                view.keepScreenOn = playbackState.playWhenReady
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!playbackState.hasRenderedFirstFrame && !route?.cover.isNullOrBlank()) {
            AsyncImage(
                model = route?.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

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

        if (playbackState.isPreparing) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (showCtrl) {
            LivePlayerCtrlBar(
                playText = if (playbackState.isPlaying) "暂停" else "播放",
                qualityText = qualityText,
                playOn = playbackState.playbackSource != null,
                qualityOn = (playbackState.playbackSource?.qualityOptions?.size ?: 0) > 1,
                onTogglePlay = {
                    showCtrl = true
                    onTogglePlay()
                },
                onQualityClick = {
                    showCtrl = true
                    showQualityDialog = true
                },
                onInfoClick = {
                    showCtrl = true
                    showInfoDialog = true
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

        if (playbackState.error != null && !playbackState.isPreparing) {
            Surface(
                color = Color.Black.copy(alpha = 0.42f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(MaterialTheme.shapes.small)
                    .clickable {
                        showCtrl = true
                        onRetry()
                    }
            ) {
                Text(
                    text = "重试",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }

    if (showQualityDialog) {
        playbackState.playbackSource?.let { source ->
            LiveQualitySelectionDialog(
                options = source.qualityOptions,
                curQuality = source.currentQn,
                onDismiss = { showQualityDialog = false },
                onSelect = { quality ->
                    onSwitchQuality(quality)
                    showQualityDialog = false
                }
            )
        }
    }

    if (showInfoDialog) {
        LivePlaybackInfoDialog(
            state = playbackState,
            onDismiss = { showInfoDialog = false }
        )
    }
}

@Composable
private fun LivePlayerCtrlBar(
    playText: String,
    qualityText: String,
    playOn: Boolean,
    qualityOn: Boolean,
    onTogglePlay: () -> Unit,
    onQualityClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.56f)
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiveCtrlBtn(
                text = playText,
                enabled = playOn,
                onClick = onTogglePlay,
                modifier = Modifier.weight(1f)
            )
            LiveCtrlBtn(
                text = qualityText,
                enabled = qualityOn,
                onClick = onQualityClick,
                modifier = Modifier.weight(1f)
            )
            LiveCtrlBtn(
                text = "信息",
                enabled = true,
                onClick = onInfoClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LiveCtrlBtn(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (enabled) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }
    val fg = if (enabled) {
        Color.White
    } else {
        Color.White.copy(alpha = 0.45f)
    }

    Box(
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
private fun LiveQualitySelectionDialog(
    options: List<LiveQualityOption>,
    curQuality: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择画质") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.qn) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option.description,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (option.qn == curQuality) {
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
                Text("关闭")
            }
        }
    )
}

@Composable
private fun LivePlaybackInfoDialog(
    state: LivePlaybackViewState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放信息") },
        text = {
            Text(
                text = buildLiveInfoText(state),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

internal fun liveStatusText(status: LiveStatus): String {
    return when (status) {
        LiveStatus.Offline -> "未开播"
        LiveStatus.Living -> "直播中"
        LiveStatus.Round -> "轮播中"
    }
}

private fun liveResolutionText(state: LivePlaybackViewState): String {
    val width = state.videoWidth.takeIf { it > 0 } ?: return "未知"
    val height = state.videoHeight.takeIf { it > 0 } ?: return "未知"
    return "${width}x$height"
}

private fun buildLiveInfoText(
    state: LivePlaybackViewState
): String {
    val source = state.playbackSource
    if (source == null) {
        return if (state.isPreparing) "正在加载播放信息" else "暂无播放信息"
    }

    val sections = buildList {
        state.error?.let { add("请求错误\n错误: ${it.toUiMessage()}") }
        state.playerError?.let { add("播放器错误\n错误: $it") }
        add(
            buildString {
                appendLine("直播")
                appendLine("状态: ${liveStatusText(source.liveStatus)}")
                append("画质: ${source.currentDescription}")
            }
        )
        add(
            buildString {
                appendLine("流信息")
                appendLine("协议: ${source.protocol}")
                appendLine("格式: ${source.format}")
                appendLine("编码: ${source.codec}")
                appendLine("分辨率: ${liveResolutionText(state)}")
                source.session?.takeIf(String::isNotBlank)?.let { appendLine("会话: $it") }
                appendLine("主地址: ${source.primaryUrl}")
                if (source.backupUrls.isNotEmpty()) {
                    append("备用地址:\n${source.backupUrls.joinToString("\n")}")
                }
            }
        )
        add(
            buildString {
                appendLine("播放器")
                appendLine("状态: ${livePlaybackStateText(state)}")
                appendLine("首帧: ${if (state.hasRenderedFirstFrame) "已渲染" else "未渲染"}")
                append("自动起播: ${if (state.playWhenReady) "开启" else "关闭"}")
            }
        )
    }

    return sections.joinToString("\n\n")
}

private fun livePlaybackStateText(state: LivePlaybackViewState): String {
    return when {
        state.isPlaying -> "播放中"
        state.isPreparing -> "准备中"
        else -> when (state.playbackState) {
            PlaybackState.Buffering -> "缓冲中"
            PlaybackState.Ready -> "已暂停"
            PlaybackState.Ended -> "已结束"
            PlaybackState.Idle -> "未开始"
        }
    }
}
