package com.naaammme.bbspace.playback

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.naaammme.bbspace.core.designsystem.component.BiliAsyncImage
import com.naaammme.bbspace.core.designsystem.component.BiliImageVariant
import com.naaammme.bbspace.core.designsystem.icon.AppIcons
import com.naaammme.bbspace.core.model.PlaybackHistoryMeta
import com.naaammme.bbspace.core.model.StreamPlaybackSessionState
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import com.naaammme.bbspace.infra.player.PlayerViewTargetBinder

@OptIn(UnstableApi::class)
@Composable
fun InAppMiniPlayer(
    player: Player?,
    target: StreamPlaybackTarget,
    sessionState: StreamPlaybackSessionState,
    pageMeta: PlaybackHistoryMeta?,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val playerView = remember(context) {
        PlayerView(context).apply {
            useController = false
            setEnableComposeSurfaceSyncWorkaround(true)
            setKeepContentOnPlayerReset(true)
        }
    }
    val title = when (target) {
        is StreamPlaybackTarget.Video -> {
            pageMeta?.title?.takeIf(String::isNotBlank) ?: "视频播放"
        }

        is StreamPlaybackTarget.Live -> {
            target.route.title?.takeIf(String::isNotBlank)
                ?: "直播间 ${target.route.roomId}"
        }
    }
    val subtitle = when (target) {
        is StreamPlaybackTarget.Video -> {
            pageMeta?.ownerName.orEmpty()
        }

        is StreamPlaybackTarget.Live -> {
            target.route.ownerName.orEmpty()
        }
    }
    val liveCover = (target as? StreamPlaybackTarget.Live)?.route?.cover

    DisposableEffect(playerView) {
        onDispose {
            PlayerViewTargetBinder.unbind(playerView)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onExpand),
        shape = MaterialTheme.shapes.large,
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { playerView },
                update = { view ->
                    PlayerViewTargetBinder.bind(view, player)
                    view.keepScreenOn = sessionState.playWhenReady
                },
                modifier = Modifier.fillMaxSize()
            )
            if (!sessionState.hasRenderedFirstFrame && !liveCover.isNullOrBlank()) {
                BiliAsyncImage(
                    url = liveCover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    variant = BiliImageVariant.Banner
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(end = 72.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (target is StreamPlaybackTarget.Live) {
                        Text(
                            text = "直播",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.84f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onTogglePlay) {
                        Icon(
                            imageVector = if (sessionState.isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (sessionState.isPlaying) "暂停" else "播放",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
