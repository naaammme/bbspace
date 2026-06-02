package com.naaammme.bbspace.infra.player.danmaku

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.naaammme.bbspace.core.model.DanmakuConfig
import com.naaammme.bbspace.core.model.DanmakuSessionState
import master.flame.danmaku.api.SegmentDanmakuSession
import master.flame.danmaku.controller.IDanmakuView
import master.flame.danmaku.ui.widget.DanmakuSurfaceView

enum class DanmakuRenderMode {
    Segmented,
    LiveAppend
}

// initial* 参数仅在首次创建时生效，后续状态通过 sync/syncLive 更新
@Composable
fun rememberDanmakuOverlayState(
    initialConfig: DanmakuConfig,
    initialPositionMs: Long,
    initialIsPlaying: Boolean,
    initialSpeed: Float
): DanmakuOverlayState {
    val context = LocalContext.current
    return remember(context) {
        val danmakuView = DanmakuSurfaceView(context).apply {
            // 绘制缓存 内存换cpu占用
            enableDanmakuDrawingCache(true)
            showFPS(false)
            setDrawingThreadType(IDanmakuView.THREAD_TYPE_LOW_PRIORITY)
            setZOrderMediaOverlay(true)
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        val danmakuContext = createDanmakuContext(context.resources.displayMetrics.density).apply {
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
            )
        )
    }
}

@Suppress("UnsafeOptInUsageError")
@UnstableApi
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
    hasSource: Boolean,
    renderMode: DanmakuRenderMode = DanmakuRenderMode.Segmented,
    manageLifecycle: Boolean = true
) {
    if (manageLifecycle) {
        DisposableEffect(overlayState) {
            overlayState.prepare()
            onDispose {
                overlayState.release()
            }
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

    when (renderMode) {
        DanmakuRenderMode.Segmented -> {
            LaunchedEffect(
                danmakuState.sourceKey,
                danmakuState.window?.id,
                danmakuConfig,
                isPlaying,
                speed,
                seekEventId,
                hasSource
            ) {
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

        DanmakuRenderMode.LiveAppend -> {
            LaunchedEffect(danmakuConfig, isPlaying, speed, hasSource) {
                overlayState.syncLive(
                    config = danmakuConfig,
                    isPlaying = isPlaying,
                    speed = speed,
                    hasSource = hasSource
                )
            }
        }
    }
}
