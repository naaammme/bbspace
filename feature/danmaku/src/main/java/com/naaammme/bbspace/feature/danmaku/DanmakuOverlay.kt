package com.naaammme.bbspace.feature.danmaku

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.ui.PlayerView
import com.naaammme.bbspace.core.model.DanmakuConfig
import master.flame.danmaku.api.SegmentDanmakuSession
import master.flame.danmaku.controller.IDanmakuView
import master.flame.danmaku.ui.widget.DanmakuSurfaceView

@Composable
fun rememberDanmakuOverlayState(
    initialConfig: DanmakuConfig,
    initialPositionMs: Long,
    initialIsPlaying: Boolean,
    initialSpeed: Float,
    onDanmakuTick: (Long) -> Unit
): DanmakuOverlayState {
    val context = LocalContext.current
    return remember(context, onDanmakuTick) {
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
        val timeProvider = DanmakuPlayerTimeProvider(
            positionMs = initialPositionMs,
            isPlaying = initialIsPlaying,
            speed = initialSpeed
        )
        DanmakuOverlayState(
            danmakuView = danmakuView,
            danmakuCtrl = danmakuView,
            danmakuContext = danmakuContext,
            timeProvider = timeProvider,
            session = SegmentDanmakuSession(
                danmakuView,
                danmakuContext,
                DefaultDanmakuItemMapper(),
                timeProvider
            ),
            onDanmakuTick = onDanmakuTick
        )
    }
}

@Composable
fun DanmakuLayer(
    playerView: PlayerView,
    overlayState: DanmakuOverlayState,
    danmakuState: DanmakuSessionState,
    danmakuConfig: DanmakuConfig,
    positionMs: Long,
    isPlaying: Boolean,
    speed: Float,
    seekEventId: Long,
    hasSource: Boolean
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
        overlayState.danmakuView.visibility = if (hasSource) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
        overlayState.sync(
            danmakuState = danmakuState,
            config = danmakuConfig,
            positionMs = positionMs,
            isPlaying = isPlaying,
            speed = speed,
            seekEventId = seekEventId,
            hasSource = hasSource
        )
    }
}
