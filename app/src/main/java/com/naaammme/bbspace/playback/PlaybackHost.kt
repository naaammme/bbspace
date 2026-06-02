package com.naaammme.bbspace.playback

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import com.naaammme.bbspace.core.model.VideoDownloadRequest
import com.naaammme.bbspace.feature.live.LiveScreen
import com.naaammme.bbspace.feature.live.LiveViewModel
import com.naaammme.bbspace.feature.video.VideoScreen
import com.naaammme.bbspace.feature.video.VideoViewModel

@OptIn(UnstableApi::class)
@Composable
fun PlaybackHost(
    mode: PlaybackHostMode,
    playbackHostViewModel: PlaybackHostViewModel,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onPauseInBackground: () -> Unit,
    onClose: () -> Unit,
    onDismissExpanded: () -> Unit,
    onGoHome: () -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onOpenDownloadCache: () -> Unit,
    onStartDownload: (VideoDownloadRequest) -> Unit,
    videoViewModel: VideoViewModel,
    liveViewModel: LiveViewModel,
    modifier: Modifier = Modifier
) {
    val procOwner = remember { ProcessLifecycleOwner.get() }
    val player by playbackHostViewModel.player.collectAsStateWithLifecycle()
    val target by playbackHostViewModel.currentTarget.collectAsStateWithLifecycle()
    val sessionState by playbackHostViewModel.sessionState.collectAsStateWithLifecycle()
    val backgroundPlaybackEnabled by playbackHostViewModel.backgroundPlaybackEnabled.collectAsStateWithLifecycle()
    val miniPlayerAvailable by playbackHostViewModel.miniPlayerAvailable.collectAsStateWithLifecycle()
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
                if (mode == PlaybackHostMode.Expanded) {
                    VideoScreen(
                        onBack = onDismissExpanded,
                        onGoHome = onGoHome,
                        onOpenSpace = onOpenSpace,
                        onOpenDownloadCache = onOpenDownloadCache,
                        onStartDownload = onStartDownload,
                        viewModel = videoViewModel,
                        hostExpanded = true
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

        val curTarget = target
        if (mode == PlaybackHostMode.Mini &&
            miniPlayerAvailable &&
            curTarget != null
        ) {
            DraggableMiniPlayerHost(
                modifier = modifier
            ) {
                InAppMiniPlayer(
                    player = player,
                    target = curTarget,
                    sessionState = sessionState,
                    onExpand = onExpand,
                    onTogglePlay = onTogglePlay,
                    onClose = onClose
                )
            }
        }
    }
}
