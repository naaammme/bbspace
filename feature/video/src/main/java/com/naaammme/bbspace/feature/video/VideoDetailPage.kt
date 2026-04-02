package com.naaammme.bbspace.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.model.PlaybackError
import com.naaammme.bbspace.core.model.PlaybackStream
import com.naaammme.bbspace.core.model.QualityOption
import com.naaammme.bbspace.feature.video.model.VideoUiState

@Composable
internal fun VideoDetailPage(
    uiState: VideoUiState,
    playerPane: @Composable (Modifier) -> Unit
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(scroll)
    ) {
        playerPane(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                uiState.error != null -> {
                    val msg = when (val err = uiState.error) {
                        is PlaybackError.NoPlayableStream -> err.message
                        is PlaybackError.RequestFailed -> err.message
                        else -> "未知错误"
                    }
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                !uiState.isLoading && uiState.playbackSource != null -> {
                    val src = uiState.playbackSource!!

                    Text(
                        text = "视频信息",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            InfoRow("AV号", "av${src.videoId.aid}")
                            InfoRow("CID", src.videoId.cid.toString())
                            InfoRow("时长", formatDuration(src.durationMs))
                            uiState.currentStream?.let { stream ->
                                InfoRow("分辨率", "${stream.width ?: 0}x${stream.height ?: 0}")
                                if (stream is PlaybackStream.Dash) {
                                    InfoRow("帧率", stream.frameRate ?: "未知")
                                    InfoRow("编码", getCodecName(stream.codecId))
                                    InfoRow("带宽", "${stream.bandwidth / 1000} kbps")
                                }
                            }
                            InfoRow("视频解码器", uiState.snapshot.videoDecoderName ?: "未初始化")
                            uiState.currentAudio?.let { audio ->
                                InfoRow("音频ID", audio.id.toString())
                                InfoRow("音频带宽", "${audio.bandwidth / 1000} kbps")
                            }
                            InfoRow("音频解码器", uiState.snapshot.audioDecoderName ?: "未初始化")
                        }
                    }

                    Text(
                        text = "可用画质",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            src.qualityOptions.forEachIndexed { index, option ->
                                QualityOptionItem(
                                    option = option,
                                    isSelected = option.quality == uiState.currentStream?.quality
                                )
                                if (index < src.qualityOptions.size - 1) {
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
                            InfoRow("缓冲到位置", formatDuration(uiState.snapshot.bufferedPositionMs))
                            InfoRow("实际缓冲时长", formatDuration(uiState.snapshot.totalBufferedDurationMs))
                            InfoRow("播放速度", formatSpeed(uiState.snapshot.speed))
                            InfoRow("播放状态", if (uiState.snapshot.isPlaying) "播放中" else "已暂停")
                        }
                    }
                }
            }
        }
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
internal fun QualityOptionItem(
    option: QualityOption,
    isSelected: Boolean,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
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
            option.limit?.message?.let { msg ->
                Text(
                    text = msg,
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
