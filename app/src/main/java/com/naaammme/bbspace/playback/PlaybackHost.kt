package com.naaammme.bbspace.playback

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.naaammme.bbspace.core.model.DanmakuConfig
import com.naaammme.bbspace.core.model.PlaybackHistoryMeta
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.StreamPlaybackSessionState
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import com.naaammme.bbspace.core.model.VideoDownloadRequest
import com.naaammme.bbspace.feature.live.LiveScreen
import com.naaammme.bbspace.feature.live.LiveViewModel
import com.naaammme.bbspace.feature.video.VideoScreen
import com.naaammme.bbspace.feature.video.VideoViewModel
import com.naaammme.bbspace.infra.player.danmaku.rememberDanmakuOverlayState

@OptIn(UnstableApi::class)
@Composable
fun PlaybackHost(
    mode: PlaybackHostMode,
    target: StreamPlaybackTarget?,
    player: Player?,
    sessionState: StreamPlaybackSessionState,
    pageMeta: PlaybackHistoryMeta?,
    miniPlayerAvailable: Boolean,
    backgroundPlaybackEnabled: Boolean,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onPauseInBackground: () -> Unit,
    onClose: () -> Unit,
    onDismissExpanded: () -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onOpenDownloadCache: () -> Unit,
    onStartDownload: (VideoDownloadRequest) -> Unit,
    videoViewModel: VideoViewModel,
    liveViewModel: LiveViewModel,
    modifier: Modifier = Modifier
) {
    val procOwner = remember { ProcessLifecycleOwner.get() }
    DisposableEffect(procOwner, target, backgroundPlaybackEnabled) {
        if (target == null || backgroundPlaybackEnabled) {
            return@DisposableEffect onDispose { }
        }
        val lifecycle = procOwner.lifecycle
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                onPauseInBackground()
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        when (target) {
            is StreamPlaybackTarget.Video -> {
                val danmakuOverlayState = key(target) {
                    rememberDanmakuOverlayState(
                        initialConfig = DanmakuConfig(),
                        initialPositionMs = 0L,
                        initialIsPlaying = false,
                        initialSpeed = 1f
                    )
                }
                DisposableEffect(danmakuOverlayState) {
                    danmakuOverlayState.prepare()
                    onDispose {
                        danmakuOverlayState.release()
                    }
                }
                if (mode == PlaybackHostMode.Expanded) {
                    VideoScreen(
                        onBack = onDismissExpanded,
                        onOpenSpace = onOpenSpace,
                        onOpenDownloadCache = onOpenDownloadCache,
                        onStartDownload = onStartDownload,
                        viewModel = videoViewModel,
                        hostExpanded = true,
                        danmakuOverlayState = danmakuOverlayState
                    )
                }
            }

            is StreamPlaybackTarget.Live -> {
                if (mode == PlaybackHostMode.Expanded) {
                    LiveScreen(
                        onBack = onDismissExpanded,
                        viewModel = liveViewModel,
                        hostExpanded = true
                    )
                }
            }

            null -> {}
        }

        if (mode == PlaybackHostMode.Mini &&
            miniPlayerAvailable &&
            target != null
        ) {
            DraggableMiniPlayerHost(
                modifier = modifier
            ) {
                InAppMiniPlayer(
                    player = player,
                    target = target,
                    sessionState = sessionState,
                    pageMeta = pageMeta,
                    onExpand = onExpand,
                    onTogglePlay = onTogglePlay,
                    onClose = onClose
                )
            }
        }
    }
}
