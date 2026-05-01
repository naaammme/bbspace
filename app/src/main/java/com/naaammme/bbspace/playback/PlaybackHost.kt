package com.naaammme.bbspace.playback

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import com.naaammme.bbspace.core.model.PlaybackHistoryMeta
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.StreamPlaybackSessionState
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import com.naaammme.bbspace.core.model.VideoDownloadRequest
import com.naaammme.bbspace.feature.live.LiveScreen
import com.naaammme.bbspace.feature.live.LiveViewModel
import com.naaammme.bbspace.feature.video.VideoScreen
import com.naaammme.bbspace.feature.video.VideoViewModel

@Composable
fun PlaybackHost(
    mode: PlaybackHostMode,
    target: StreamPlaybackTarget?,
    player: Player?,
    sessionState: StreamPlaybackSessionState,
    pageMeta: PlaybackHistoryMeta?,
    miniPlayerAvailable: Boolean,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit,
    onDismissExpanded: () -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onOpenDownloadCache: () -> Unit,
    onStartDownload: (VideoDownloadRequest) -> Unit,
    videoViewModel: VideoViewModel,
    liveViewModel: LiveViewModel,
    miniPlayerModifier: Modifier = Modifier
) {
    when {
        mode == PlaybackHostMode.Expanded && target is StreamPlaybackTarget.Video -> {
            VideoScreen(
                onBack = onDismissExpanded,
                onOpenSpace = onOpenSpace,
                onOpenDownloadCache = onOpenDownloadCache,
                onStartDownload = onStartDownload,
                viewModel = videoViewModel
            )
        }

        mode == PlaybackHostMode.Expanded && target is StreamPlaybackTarget.Live -> {
            LiveScreen(
                onBack = onDismissExpanded,
                viewModel = liveViewModel
            )
        }

        mode == PlaybackHostMode.Mini &&
            miniPlayerAvailable &&
            target != null -> {
            DraggableMiniPlayerHost(
                modifier = miniPlayerModifier
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
