package com.naaammme.bbspace.feature.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.naaammme.bbspace.core.model.PlaybackAudio
import com.naaammme.bbspace.core.model.QualityOption
import com.naaammme.bbspace.feature.video.model.VideoUiState
import com.naaammme.bbspace.feature.video.model.VideoViewModel
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal val speedOps = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Suppress("UnsafeOptInUsageError")
@UnstableApi
@Composable
fun VideoScreen(
    onBack: () -> Unit,
    viewModel: VideoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val engineReady by viewModel.engineReady.collectAsStateWithLifecycle()
    val bgPlay by viewModel.backgroundPlayback.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val ctx = LocalContext.current
    val act = remember(ctx) { ctx.findActivity() }
    val pv = remember(ctx) {
        PlayerView(ctx).apply {
            useController = false
            setKeepContentOnPlayerReset(true)
        }
    }
    var showQ by remember { mutableStateOf(false) }
    var showA by remember { mutableStateOf(false) }
    var showSp by remember { mutableStateOf(false) }
    var showCtrl by remember { mutableStateOf(true) }
    var isFull by rememberSaveable { mutableStateOf(false) }
    var posMs by remember { mutableStateOf(0L) }
    var durMs by remember { mutableStateOf(0L) }
    var dragMs by remember { mutableStateOf<Long?>(null) }

    BackHandler(enabled = isFull) {
        isFull = false
    }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.close()
        }
    }

    DisposableEffect(act, isFull) {
        val a = act
        if (a == null) {
            onDispose { }
        } else {
            val win = a.window
            val ctrl = WindowInsetsControllerCompat(win, win.decorView)
            if (isFull) {
                ctrl.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                ctrl.show(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                ctrl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(owner, viewModel, bgPlay) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !bgPlay) {
                viewModel.pause()
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
        }
    }

    LaunchedEffect(uiState.snapshot.positionMs, uiState.snapshot.durationMs, dragMs) {
        if (dragMs == null) {
            posMs = uiState.snapshot.positionMs
            durMs = uiState.snapshot.durationMs
        }
    }

    LaunchedEffect(engineReady, uiState.snapshot.isPlaying, dragMs, uiState.snapshot.durationMs) {
        while (isActive && engineReady) {
            if (dragMs == null) {
                val player = viewModel.getPlayerForView()
                posMs = player.currentPosition.coerceAtLeast(0L)
                durMs = (player.duration.takeIf { it > 0 } ?: uiState.snapshot.durationMs)
                    .coerceAtLeast(0L)
            }
            delay(if (uiState.snapshot.isPlaying) 500 else 1_000)
        }
    }

    LaunchedEffect(showCtrl, uiState.snapshot.isPlaying, dragMs, showA, showQ, showSp) {
        if (
            showCtrl &&
            uiState.snapshot.isPlaying &&
            dragMs == null &&
            !showA &&
            !showQ &&
            !showSp
        ) {
            delay(3_000)
            showCtrl = false
        }
    }

    val barMs = dragMs ?: posMs
    val sliderVal = if (durMs > 0) {
        (barMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val pane: @Composable (Modifier) -> Unit = { mod ->
        VideoPlayerPane(
            modifier = mod,
            playerView = pv,
            engineReady = engineReady,
            viewModel = viewModel,
            showCtrl = showCtrl,
            barMs = barMs,
            durMs = durMs,
            sliderVal = sliderVal,
            isFull = isFull,
            isPlaying = uiState.snapshot.isPlaying,
            audioText = uiState.currentAudio?.let { getAudioName(it.id, short = true) } ?: "音频",
            qualityText = getQualityName(uiState),
            speedText = formatSpeed(uiState.snapshot.speed),
            audioOn = (uiState.playbackSource?.audios?.size ?: 0) > 1,
            qualityOn = (uiState.playbackSource?.qualityOptions?.size ?: 0) > 1,
            onToggleCtrl = { showCtrl = !showCtrl },
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
                isFull = !isFull
            },
            onBackClick = {
                if (isFull) {
                    isFull = false
                } else {
                    onBack()
                }
            },
            onSeekChange = { frac ->
                showCtrl = true
                dragMs = (durMs * frac).toLong()
            },
            onSeekDone = {
                val next = dragMs ?: return@VideoPlayerPane
                viewModel.seekTo(next)
                posMs = next
                dragMs = null
            }
        )
    }

    if (isFull) {
        VideoFullPage(playerPane = pane)
    } else {
        VideoDetailPage(
            uiState = uiState,
            playerPane = pane
        )
    }

    if (showQ && uiState.playbackSource != null) {
        QualitySelectionDialog(
            options = uiState.playbackSource!!.qualityOptions,
            curQuality = uiState.currentStream?.quality,
            onDismiss = { showQ = false },
            onSelect = { quality ->
                viewModel.switchQuality(quality)
                showQ = false
            }
        )
    }

    if (showA && uiState.playbackSource != null) {
        AudioSelectionDialog(
            audios = uiState.playbackSource!!.audios,
            curAudioId = uiState.currentAudio?.id,
            onDismiss = { showA = false },
            onSelect = { audioId ->
                viewModel.switchAudio(audioId)
                showA = false
            }
        )
    }

    if (showSp) {
        SpeedSelectionDialog(
            curSpeed = uiState.snapshot.speed,
            onDismiss = { showSp = false },
            onSelect = { speed ->
                viewModel.setSpeed(speed)
                showSp = false
            }
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

internal fun formatDuration(ms: Long): String {
    val sec = ms / 1000
    val min = sec / 60
    val hour = min / 60
    return if (hour > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hour, min % 60, sec % 60)
    } else {
        String.format(Locale.ROOT, "%d:%02d", min, sec % 60)
    }
}

internal fun formatPlaybackTime(posMs: Long, durMs: Long): String {
    return "${formatDuration(posMs)} / ${formatDuration(durMs)}"
}

internal fun formatSpeed(speed: Float): String {
    val num = if (speed % 1f == 0f) {
        String.format(Locale.ROOT, "%.1f", speed)
    } else {
        String.format(Locale.ROOT, "%.2f", speed).trimEnd('0').trimEnd('.')
    }
    return "${num}x"
}

internal fun getCodecName(codecId: Int): String {
    return when (codecId) {
        7 -> "AVC/H.264"
        12 -> "HEVC/H.265"
        13 -> "AV1"
        else -> "未知 $codecId"
    }
}

internal fun getAudioName(audioId: Int, short: Boolean = false): String {
    return when (audioId) {
        30216 -> "64K"
        30232 -> "132K"
        30280 -> "192K"
        30250 -> if (short) "杜比" else "杜比全景声"
        30251 -> if (short) "无损" else "Hi-Res 无损"
        else -> if (short) "音频" else "音频 $audioId"
    }
}

internal fun getQualityName(uiState: VideoUiState): String {
    val source = uiState.playbackSource ?: return "画质"
    val stream = uiState.currentStream ?: return "画质"
    val option = source.qualityOptions.firstOrNull { it.quality == stream.quality }
    val label = option?.displayDescription?.takeIf(String::isNotBlank)
        ?: option?.description?.takeIf(String::isNotBlank)
        ?: stream.description.takeIf(String::isNotBlank)
        ?: "画质"
    return label.substringBefore(' ').ifBlank { label }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
