package com.naaammme.bbspace.feature.video

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.feature.video.model.VideoDanmakuConfig
import com.naaammme.bbspace.feature.video.model.VideoDanmakuState
import com.naaammme.bbspace.feature.video.model.VideoPlayerState
import com.naaammme.bbspace.feature.video.model.VideoViewModel
import master.flame.danmaku.api.SegmentDanmakuSession
import master.flame.danmaku.ui.widget.DanmakuView

@Composable
internal fun rememberVideoDanmakuOverlayState(
    viewModel: VideoViewModel,
    initialConfig: VideoDanmakuConfig,
    initialPositionMs: Long,
    initialIsPlaying: Boolean,
    initialSpeed: Float
): VideoDanmakuOverlayState {
    val context = LocalContext.current
    val state = remember(viewModel, context) {
        val danmakuView = DanmakuView(context).apply {
            enableDanmakuDrawingCache(true)
            showFPS(false)
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        val danmakuContext = createDanmakuContext().apply {
            applyConfig(initialConfig, initialSpeed)
        }
        val timeProvider = PlayerSessionTimeProvider(
            positionMs = initialPositionMs,
            isPlaying = initialIsPlaying,
            speed = initialSpeed
        )
        VideoDanmakuOverlayState(
            danmakuView = danmakuView,
            danmakuContext = danmakuContext,
            timeProvider = timeProvider,
            session = SegmentDanmakuSession(
                danmakuView,
                danmakuContext,
                BbspaceDanmakuMapper(),
                timeProvider
            )
        )
    }
    return state
}

@Composable
internal fun VideoDanmakuLayer(
    modifier: Modifier,
    viewModel: VideoViewModel,
    playerState: VideoPlayerState,
    danmakuConfig: VideoDanmakuConfig
) {
    val videoId = playerState.playbackSource?.videoId
    if (videoId == null) return

    val danmakuState by viewModel.danmakuState.collectAsStateWithLifecycle()
    val overlayState = rememberVideoDanmakuOverlayState(
        viewModel = viewModel,
        initialConfig = danmakuConfig,
        initialPositionMs = playerState.snapshot.positionMs,
        initialIsPlaying = playerState.snapshot.isPlaying,
        initialSpeed = playerState.snapshot.speed
    )
    VideoDanmakuOverlay(
        modifier = modifier,
        overlayState = overlayState,
        playerState = playerState,
        danmakuState = danmakuState,
        danmakuConfig = danmakuConfig
    )
}

@Composable
internal fun VideoDanmakuOverlay(
    modifier: Modifier,
    overlayState: VideoDanmakuOverlayState,
    playerState: VideoPlayerState,
    danmakuState: VideoDanmakuState,
    danmakuConfig: VideoDanmakuConfig
) {
    DisposableEffect(overlayState) {
        overlayState.prepare()
        onDispose {
            overlayState.release()
        }
    }

    AndroidView(
        factory = { overlayState.danmakuView },
        update = { view ->
            view.visibility = if (playerState.playbackSource != null) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
        },
        modifier = modifier
    )

    SideEffect {
        overlayState.sync(
            danmakuState = danmakuState,
            config = danmakuConfig,
            snapshot = playerState.snapshot,
            hasSource = playerState.playbackSource != null,
        )
    }
}
