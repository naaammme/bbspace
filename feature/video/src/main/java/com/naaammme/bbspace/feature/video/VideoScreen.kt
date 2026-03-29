package com.naaammme.bbspace.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import com.naaammme.bbspace.core.model.PlaybackError
import com.naaammme.bbspace.feature.video.model.VideoViewModel

@Suppress("UnsafeOptInUsageError")
@UnstableApi
@Composable
fun VideoScreen(
    onBack: () -> Unit,
    viewModel: VideoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()
    var showQualityDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.close()
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(scrollState)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = true
                        setKeepContentOnPlayerReset(true)
                        findViewById<PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                            ?.setTimeBarScrubbingEnabled(true)
                        player = viewModel.getPlayerForView()
                    }
                },
                update = {
                    it.player = viewModel.getPlayerForView()
                    it.findViewById<PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                        ?.setTimeBarScrubbingEnabled(true)
                },
                modifier = Modifier.fillMaxSize()
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                color = Color.Black.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small
            ) {
                IconButton(
                    onClick = {
                        viewModel.close()
                        onBack()
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.error != null) {
                val message = when (val error = uiState.error) {
                    is PlaybackError.NoPlayableStream -> error.message
                    is PlaybackError.RequestFailed -> error.message
                    null -> "未知错误"
                }
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (!uiState.isLoading && uiState.playbackSource != null) {
                val source = uiState.playbackSource!!

                Text(
                    text = "视频信息",
                    style = MaterialTheme.typography.titleLarge
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoRow("AV号", "av${source.videoId.aid}")
                        InfoRow("CID", source.videoId.cid.toString())
                        InfoRow("时长", formatDuration(source.durationMs))
                        uiState.currentStream?.let { stream ->
                            InfoRow("分辨率", "${stream.width}x${stream.height}")
                            if (stream is com.naaammme.bbspace.core.model.PlaybackStream.Dash) {
                                InfoRow("帧率", stream.frameRate ?: "未知")
                                InfoRow("编码", getCodecName(stream.codecId))
                                InfoRow("带宽", "${stream.bandwidth / 1000} kbps")
                            }
                        }
                        uiState.currentAudio?.let { audio ->
                            InfoRow("音频ID", audio.id.toString())
                            InfoRow("音频带宽", "${audio.bandwidth / 1000} kbps")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showQualityDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("切换画质")
                    }
                    if (source.audios.size > 1) {
                        Button(
                            onClick = { showAudioDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("切换音频")
                        }
                    }
                    Button(
                        onClick = viewModel::togglePlayPause,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (uiState.snapshot.isPlaying) "暂停视频" else "播放视频")
                    }
                }

                Text(
                    text = "可用画质",
                    style = MaterialTheme.typography.titleMedium
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        source.qualityOptions.forEachIndexed { index, option ->
                            QualityOptionItem(
                                option = option,
                                isSelected = option.quality == uiState.currentStream?.quality,
                                onClick = {
                                    viewModel.switchQuality(option.quality)
                                    showQualityDialog = false
                                }
                            )
                            if (index < source.qualityOptions.size - 1) {
                                HorizontalDivider()
                            }
                        }
                    }
                }

                Text(
                    text = "播放状态",
                    style = MaterialTheme.typography.titleMedium
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoRow("播放位置", formatDuration(uiState.snapshot.positionMs))
                        InfoRow("缓冲位置", formatDuration(uiState.snapshot.bufferedPositionMs))
                        InfoRow("播放状态", if (uiState.snapshot.isPlaying) "播放中" else "已暂停")
                    }
                }
            }
        }
    }

    if (showQualityDialog && uiState.playbackSource != null) {
        QualitySelectionDialog(
            options = uiState.playbackSource!!.qualityOptions,
            currentQuality = uiState.currentStream?.quality,
            onDismiss = { showQualityDialog = false },
            onSelect = { quality ->
                viewModel.switchQuality(quality)
                showQualityDialog = false
            }
        )
    }

    if (showAudioDialog && uiState.playbackSource != null) {
        AudioSelectionDialog(
            audios = uiState.playbackSource!!.audios,
            currentAudioId = uiState.currentAudio?.id,
            onDismiss = { showAudioDialog = false },
            onSelect = { audioId ->
                viewModel.switchAudio(audioId)
                showAudioDialog = false
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun QualityOptionItem(
    option: com.naaammme.bbspace.core.model.QualityOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (option.needVip) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "大会员",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (option.limit != null) {
                Text(
                    text = option.limit!!.message ?: "受限",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun QualitySelectionDialog(
    options: List<com.naaammme.bbspace.core.model.QualityOption>,
    currentQuality: Int?,
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
                        isSelected = option.quality == currentQuality,
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

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
        else -> String.format("%d:%02d", minutes, seconds % 60)
    }
}

private fun getCodecName(codecId: Int): String {
    return when (codecId) {
        7 -> "AVC/H.264"
        12 -> "HEVC/H.265"
        13 -> "AV1"
        else -> "未知 ($codecId)"
    }
}

private fun getAudioName(audioId: Int): String {
    return when (audioId) {
        30216 -> "64K"
        30232 -> "132K"
        30280 -> "192K"
        30250 -> "杜比全景声"
        30251 -> "Hi-Res 无损"
        else -> "音频 $audioId"
    }
}

@Composable
private fun AudioSelectionDialog(
    audios: List<com.naaammme.bbspace.core.model.PlaybackAudio>,
    currentAudioId: Int?,
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
                        if (audio.id == currentAudioId) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
