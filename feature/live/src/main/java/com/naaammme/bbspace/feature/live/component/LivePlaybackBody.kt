package com.naaammme.bbspace.feature.live.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.model.LivePlaybackViewState
import com.naaammme.bbspace.core.model.LiveRoomSessionState
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.PlayerSettingsState
import com.naaammme.bbspace.feature.live.player.LivePlayerPane
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun LivePlaybackBody(
    route: LiveRoute?,
    playbackState: LivePlaybackViewState,
    roomSessionState: StateFlow<LiveRoomSessionState>,
    player: androidx.media3.common.Player?,
    isExpanded: Boolean,
    playerSpaceWidth: Dp,
    playerSpaceHeight: Dp,
    onToggleFull: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleDanmaku: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onSwitchQuality: (Int) -> Unit,
    onSendDanmaku: suspend (String) -> Unit,
    settingsState: PlayerSettingsState,
    modifier: Modifier = Modifier
) {
    if (isExpanded) {
        Row(
            modifier = modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(playerSpaceWidth)
                    .height(playerSpaceHeight)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(Color.Black)
            ) {
                LivePlayerPane(
                    route = route,
                    player = player,
                    playbackState = playbackState,
                    roomSessionState = roomSessionState,
                    isFull = false,
                    onToggleFull = onToggleFull,
                    onTogglePlay = onTogglePlay,
                    onToggleDanmaku = onToggleDanmaku,
                    onRetry = onRetry,
                    onSwitchQuality = onSwitchQuality,
                    settingsState = settingsState,
                    modifier = Modifier.fillMaxSize()
                )
            }

            LiveDetailPane(
                route = route,
                playbackState = playbackState,
                roomSessionState = roomSessionState,
                onSendDanmaku = onSendDanmaku,
                showHeader = true,
                horizontalPad = 0.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    } else {
        Column(modifier = modifier) {
            LivePlayerPane(
                route = route,
                player = player,
                playbackState = playbackState,
                roomSessionState = roomSessionState,
                isFull = false,
                onToggleFull = onToggleFull,
                onTogglePlay = onTogglePlay,
                onToggleDanmaku = onToggleDanmaku,
                onRetry = onRetry,
                onSwitchQuality = onSwitchQuality,
                settingsState = settingsState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(playerSpaceHeight)
            )

            LiveDetailPane(
                route = route,
                playbackState = playbackState,
                roomSessionState = roomSessionState,
                onSendDanmaku = onSendDanmaku,
                showHeader = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}
