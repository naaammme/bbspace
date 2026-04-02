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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.naaammme.bbspace.feature.video.model.VideoViewModel
import android.view.ViewGroup

@Suppress("UnsafeOptInUsageError")
@UnstableApi
@Composable
internal fun VideoPlayerPane(
    modifier: Modifier,
    playerView: PlayerView,
    engineReady: Boolean,
    viewModel: VideoViewModel,
    showCtrl: Boolean,
    barMs: Long,
    durMs: Long,
    sliderVal: Float,
    isFull: Boolean,
    isPlaying: Boolean,
    audioText: String,
    qualityText: String,
    speedText: String,
    audioOn: Boolean,
    qualityOn: Boolean,
    onToggleCtrl: () -> Unit,
    onTogglePlay: () -> Unit,
    onAudioClick: () -> Unit,
    onQualityClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onFullClick: () -> Unit,
    onBackClick: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekDone: () -> Unit
) {
    val tapSrc = remember { MutableInteractionSource() }

    Box(modifier = modifier) {
        if (engineReady) {
            AndroidView(
                factory = {
                    (playerView.parent as? ViewGroup)?.removeView(playerView)
                    playerView.apply {
                        useController = false
                        setKeepContentOnPlayerReset(true)
                        player = viewModel.getPlayerForView()
                        post { viewModel.ensureStarted() }
                    }
                },
                update = { view ->
                    if (view.parent == null) {
                        (playerView.parent as? ViewGroup)?.removeView(playerView)
                    }
                    view.useController = false
                    view.player = viewModel.getPlayerForView()
                },
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
                    onToggleCtrl()
                }
        )

        if (showCtrl) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
            }
        }

        if (showCtrl && engineReady) {
            PlayerCtrlBar(
                playText = if (isPlaying) "暂停" else "播放",
                timeText = formatPlaybackTime(barMs, durMs),
                audioText = audioText,
                qualityText = qualityText,
                speedText = speedText,
                fullText = if (isFull) "还原" else "全屏",
                sliderVal = sliderVal,
                sliderOn = durMs > 0,
                audioOn = audioOn,
                qualityOn = qualityOn,
                onTogglePlay = onTogglePlay,
                onAudioClick = onAudioClick,
                onQualityClick = onQualityClick,
                onSpeedClick = onSpeedClick,
                onFullClick = onFullClick,
                onSeekChange = onSeekChange,
                onSeekDone = onSeekDone,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
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
