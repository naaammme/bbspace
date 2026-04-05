package com.naaammme.bbspace.feature.video

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.feature.video.model.VideoDanmakuConfig
import com.naaammme.bbspace.feature.video.model.VideoDanmakuState
import com.naaammme.bbspace.feature.video.model.VideoPlayerState
import com.naaammme.bbspace.feature.video.model.VideoViewModel
import kotlinx.coroutines.delay
import master.flame.danmaku.api.SegmentDanmakuSession
import master.flame.danmaku.ui.widget.DanmakuView

@Composable
internal fun rememberVideoDanmakuOverlayState(
    viewModel: VideoViewModel,
    initialConfig: VideoDanmakuConfig
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
            applyConfig(initialConfig)
        }
        VideoDanmakuOverlayState(
            danmakuView = danmakuView,
            danmakuContext = danmakuContext,
            session = SegmentDanmakuSession(
                danmakuView,
                danmakuContext,
                BbspaceDanmakuMapper(),
                PlayerSessionTimeProvider { viewModel.getPlayerForView() }
            )
        )
    }

    DisposableEffect(state) {
        onDispose {
            state.release()
        }
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
    var showLayer by remember(videoId, danmakuConfig.enabled) { mutableStateOf(false) }

    LaunchedEffect(videoId, danmakuConfig.enabled) {
        showLayer = false
        if (videoId == null || !danmakuConfig.enabled) return@LaunchedEffect
        delay(DANMAKU_LAYER_DELAY_MS)
        showLayer = true
    }

    if (!showLayer) return

    val danmakuState by viewModel.danmakuState.collectAsStateWithLifecycle()
    val overlayState = rememberVideoDanmakuOverlayState(
        viewModel = viewModel,
        initialConfig = danmakuConfig
    )
    VideoDanmakuOverlay(
        modifier = modifier,
        overlayState = overlayState,
        playerState = playerState,
        danmakuState = danmakuState,
        danmakuConfig = danmakuConfig,
        positionMs = playerState.snapshot.positionMs,
        enabled = danmakuConfig.enabled
    )
}

@Composable
internal fun VideoDanmakuOverlay(
    modifier: Modifier,
    overlayState: VideoDanmakuOverlayState,
    playerState: VideoPlayerState,
    danmakuState: VideoDanmakuState,
    danmakuConfig: VideoDanmakuConfig,
    positionMs: Long,
    enabled: Boolean
) {
    AndroidView(
        factory = {
            overlayState.danmakuView.also { view ->
                (view.parent as? ViewGroup)?.removeView(view)
            }
        },
        update = { view ->
            view.visibility = if (playerState.playbackSource != null) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
        },
        modifier = modifier
    )

    LaunchedEffect(overlayState) {
        overlayState.prepare()
    }

    LaunchedEffect(overlayState, danmakuConfig) {
        overlayState.updateConfig(danmakuConfig)
    }

    LaunchedEffect(overlayState, danmakuState.videoId, danmakuState.loadedSegments, enabled) {
        overlayState.syncSegments(danmakuState)
        if (enabled && overlayState.hasPendingResync()) {
            overlayState.syncPosition(
                videoId = danmakuState.videoId,
                positionMs = positionMs,
                force = true
            )
        }
    }

    LaunchedEffect(overlayState, enabled, playerState.playbackSource, playerState.snapshot.isPlaying) {
        overlayState.updatePlaybackState(
            enabled = enabled,
            hasSource = playerState.playbackSource != null,
            isPlaying = playerState.snapshot.isPlaying
        )
    }

    LaunchedEffect(overlayState, danmakuState.videoId, positionMs, enabled) {
        overlayState.syncPosition(
            videoId = danmakuState.videoId,
            positionMs = positionMs,
            force = enabled && overlayState.hasPendingResync()
        )
    }
}

private const val DANMAKU_LAYER_DELAY_MS = 600L
