package com.naaammme.bbspace.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.player.PlayerSettings
import com.naaammme.bbspace.core.domain.player.StreamPlaybackSession
import com.naaammme.bbspace.core.model.LiveRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaybackHostViewModel @Inject constructor(
    private val playbackSession: StreamPlaybackSession,
    playerSettings: PlayerSettings
) : ViewModel() {

    val player = playbackSession.player
    val currentTarget = playbackSession.currentTarget
    val sessionState = playbackSession.sessionState
    val pageMeta = playbackSession.pageMeta
    val backgroundPlaybackEnabled = playerSettings.state
        .map { it.playback.backgroundPlayback }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )
    val miniPlayerAvailable = playerSettings.state
        .combine(playbackSession.currentTarget) { settings, target ->
            settings.playback.inAppMiniPlayer && target != null
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    private val _hostMode = mutableStateOf(PlaybackHostMode.Hidden)
    val hostMode: PlaybackHostMode
        get() = _hostMode.value

    init {
        viewModelScope.launch {
            combine(currentTarget, miniPlayerAvailable) { target, enabled ->
                target to enabled
            }.collect { (target, enabled) ->
                if (target == null) {
                    _hostMode.value = PlaybackHostMode.Hidden
                    return@collect
                }
                if (_hostMode.value == PlaybackHostMode.Mini && !enabled) {
                    _hostMode.value = PlaybackHostMode.Hidden
                }
            }
        }
    }

    fun expand() {
        _hostMode.value = PlaybackHostMode.Expanded
    }

    fun minimize() {
        if (!miniPlayerAvailable.value) return
        if (currentTarget.value == null) return
        _hostMode.value = PlaybackHostMode.Mini
    }

    fun openLive(route: LiveRoute) {
        viewModelScope.launch {
            playbackSession.openLive(route)
        }
    }

    fun togglePlayPause() {
        if (sessionState.value.isPlaying) {
            playbackSession.pause()
        } else {
            playbackSession.play()
        }
    }

    fun pause() {
        playbackSession.pause()
    }

    fun close() {
        _hostMode.value = PlaybackHostMode.Hidden
        playbackSession.close()
    }
}
