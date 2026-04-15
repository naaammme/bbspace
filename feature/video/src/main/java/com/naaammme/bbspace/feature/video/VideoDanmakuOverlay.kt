package com.naaammme.bbspace.feature.video

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.naaammme.bbspace.core.model.PlaybackViewState
import com.naaammme.bbspace.core.model.VideoDanmakuConfig
import com.naaammme.bbspace.feature.video.model.VideoDanmakuState
import com.naaammme.bbspace.feature.video.model.VideoViewModel
import master.flame.danmaku.controller.IDanmakuView
import master.flame.danmaku.api.SegmentDanmakuSession
import master.flame.danmaku.ui.widget.DanmakuSurfaceView

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
        val danmakuView = DanmakuSurfaceView(context).apply {
            // 绘制缓存 内存换cpu占用
            enableDanmakuDrawingCache(true)
            showFPS(false)
            setDrawingThreadType(IDanmakuView.THREAD_TYPE_HIGH_PRIORITY)
            setZOrderMediaOverlay(true)
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        val danmakuContext = createDanmakuContext().apply {
            applyConfig(initialConfig)
        }
        val timeProvider = PlayerSessionTimeProvider(
            positionMs = initialPositionMs,
            isPlaying = initialIsPlaying,
            speed = initialSpeed
        )
        VideoDanmakuOverlayState(
            danmakuView = danmakuView,
            danmakuCtrl = danmakuView,
            danmakuContext = danmakuContext,
            timeProvider = timeProvider,
            session = SegmentDanmakuSession(
                danmakuView,
                danmakuContext,
                BbspaceDanmakuMapper(),
                timeProvider
            ),
            onDanmakuTick = viewModel::onDanmakuTick
        )
    }
    return state
}

@Composable
internal fun VideoDanmakuLayer(
    playerView: PlayerView,
    viewModel: VideoViewModel,
    playbackState: PlaybackViewState,
    danmakuConfig: VideoDanmakuConfig
) {
    val videoId = playbackState.playbackSource?.videoId
    if (videoId == null) return

    val danmakuState by viewModel.danmakuState.collectAsStateWithLifecycle()
    val overlayState = rememberVideoDanmakuOverlayState(
        viewModel = viewModel,
        initialConfig = danmakuConfig,
        initialPositionMs = playbackState.positionMs,
        initialIsPlaying = playbackState.isPlaying,
        initialSpeed = playbackState.speed
    )
    VideoDanmakuOverlay(
        playerView = playerView,
        overlayState = overlayState,
        playbackState = playbackState,
        danmakuState = danmakuState,
        danmakuConfig = danmakuConfig
    )
}

@Composable
internal fun VideoDanmakuOverlay(
    playerView: PlayerView,
    overlayState: VideoDanmakuOverlayState,
    playbackState: PlaybackViewState,
    danmakuState: VideoDanmakuState,
    danmakuConfig: VideoDanmakuConfig
) {
    DisposableEffect(overlayState) {
        overlayState.prepare()
        onDispose {
            overlayState.release()
        }
    }

    DisposableEffect(playerView, overlayState) {
        val view = overlayState.danmakuView
        val host = playerView.overlayFrameLayout ?: playerView
        val parent = view.parent as? ViewGroup
        if (parent !== host) {
            parent?.removeView(view)
            host.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        } else {
            val lp = view.layoutParams as? FrameLayout.LayoutParams
            if (
                lp == null ||
                lp.width != ViewGroup.LayoutParams.MATCH_PARENT ||
                lp.height != ViewGroup.LayoutParams.MATCH_PARENT
            ) {
                view.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }
        onDispose {
            (view.parent as? ViewGroup)?.removeView(view)
        }
    }

    SideEffect {
        overlayState.danmakuView.visibility = if (playbackState.playbackSource != null) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
        overlayState.sync(
            danmakuState = danmakuState,
            config = danmakuConfig,
            playbackState = playbackState,
            hasSource = playbackState.playbackSource != null,
        )
    }
}
