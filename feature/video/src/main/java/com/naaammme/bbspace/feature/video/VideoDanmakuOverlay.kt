package com.naaammme.bbspace.feature.video

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.naaammme.bbspace.feature.video.model.VideoDanmakuState
import com.naaammme.bbspace.feature.video.model.VideoPlayerState
import com.naaammme.bbspace.feature.video.model.VideoViewModel
import master.flame.danmaku.api.SegmentDanmakuSession
import master.flame.danmaku.ui.widget.DanmakuView

@Composable
internal fun rememberVideoDanmakuOverlayState(
    viewModel: VideoViewModel
): VideoDanmakuOverlayState {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val state = remember(viewModel, context, density) {
        val danmakuView = DanmakuView(context).apply {
            enableDanmakuDrawingCache(true)
            showFPS(false)
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        VideoDanmakuOverlayState(
            danmakuView = danmakuView,
            session = SegmentDanmakuSession(
                danmakuView,
                createDanmakuContext(),
                BbspaceDanmakuMapper(density),
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
internal fun VideoDanmakuOverlay(
    modifier: Modifier,
    overlayState: VideoDanmakuOverlayState,
    playerState: VideoPlayerState,
    danmakuState: VideoDanmakuState,
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
