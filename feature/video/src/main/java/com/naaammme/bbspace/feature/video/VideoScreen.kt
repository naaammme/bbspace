package com.naaammme.bbspace.feature.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.media3.common.util.UnstableApi
import androidx.window.core.layout.WindowWidthSizeClass
import com.naaammme.bbspace.core.model.PlaybackSource
import com.naaammme.bbspace.core.model.PlaybackStream
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.VideoRoute
import com.naaammme.bbspace.feature.video.model.VideoViewModel
import java.util.Locale

internal val speedOps = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Suppress("UnsafeOptInUsageError")
@UnstableApi
@Composable
fun VideoScreen(
    onBack: () -> Unit,
    onOpenVideo: (VideoRoute) -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    viewModel: VideoViewModel = hiltViewModel()
) {
    val pageState by viewModel.pageState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val procOwner = remember { ProcessLifecycleOwner.get() }
    val ctx = LocalContext.current
    val act = remember(ctx) { ctx.findActivity() }
    val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    var isFull by rememberSaveable { mutableStateOf(false) }

    val toggleFull = { isFull = !isFull }
    val handleBack = {
        if (isFull) {
            isFull = false
        } else {
            viewModel.closePage()
            onBack()
        }
    }

    BackHandler {
        handleBack()
    }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.closePage()
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

    DisposableEffect(owner, viewModel) {
        val lifecycle = owner.lifecycle
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.ensureStarted()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.ensureStarted()
        }
        onDispose {
            lifecycle.removeObserver(obs)
        }
    }

    DisposableEffect(procOwner, viewModel, settingsState.playback.backgroundPlayback) {
        val lifecycle = procOwner.lifecycle
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !settingsState.playback.backgroundPlayback) {
                viewModel.pause()
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val isExpanded = widthClass == WindowWidthSizeClass.EXPANDED
        val playerTopPad = 16.dp
        val playerGap = 16.dp
        val compactVideoH = maxWidth * (9f / 16f)
        val compactPlayerSpaceH = statusTop + compactVideoH
        val expandedContentW = (maxWidth - (playerTopPad * 2) - playerGap).coerceAtLeast(0.dp)
        val expandedPlayerW = expandedContentW * 0.54f
        val expandedPlayerH = (maxHeight - statusTop - (playerTopPad * 2)).coerceAtLeast(0.dp)

        if (!isFull) {
            VideoDetailPage(
                pageState = pageState,
                commentSubject = viewModel.commentSubject,
                isExpanded = isExpanded,
                playerSpaceWidth = expandedPlayerW,
                playerSpaceHeight = if (isExpanded) expandedPlayerH else compactPlayerSpaceH,
                onOpenVideo = onOpenVideo,
                onOpenSpace = onOpenSpace,
                onOpenEpisode = { onOpenVideo(it) },
                onSwitchPage = viewModel::switchPage
            )
        }

        val playerHostMod = when {
            isFull -> Modifier.fillMaxSize()
            isExpanded -> Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(playerTopPad)
            else -> Modifier
                .fillMaxWidth()
                .height(compactPlayerSpaceH)
                .background(Color.Black)
        }

        val playerPaneMod = when {
            isFull -> Modifier.fillMaxSize()
            isExpanded -> Modifier
                .width(expandedPlayerW)
                .height(expandedPlayerH)
                .clip(MaterialTheme.shapes.extraLarge)
            else -> Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(compactVideoH)
        }

        Box(modifier = playerHostMod) {
            VideoPlayerPane(
                modifier = playerPaneMod,
                viewModel = viewModel,
                isFull = isFull,
                onToggleFull = toggleFull,
                onBackClick = handleBack
            )
        }
    }
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

internal fun formatPlaybackTime(
    posMs: Long,
    durMs: Long
): String {
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

internal fun getAudioName(
    audioId: Int,
    short: Boolean = false
): String {
    return when (audioId) {
        30216 -> "64K"
        30232 -> "132K"
        30280 -> "192K"
        30250 -> if (short) "杜比" else "杜比全景声"
        30251 -> if (short) "无损" else "Hi-Res 无损"
        else -> if (short) "音频" else "音频 $audioId"
    }
}

internal fun getQualityName(
    source: PlaybackSource?,
    stream: PlaybackStream?
): String {
    source ?: return "画质"
    stream ?: return "画质"
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
