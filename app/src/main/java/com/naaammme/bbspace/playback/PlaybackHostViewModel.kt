package com.naaammme.bbspace.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.settings.AppSettings
import com.naaammme.bbspace.core.playback.LivePlaybackController
import com.naaammme.bbspace.core.playback.StreamPlaybackSession
import com.naaammme.bbspace.core.model.LiveRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val livePlaybackController: LivePlaybackController,
    playerSettings: AppSettings
) : ViewModel() {

    val player = playbackSession.player
    val currentTarget = playbackSession.currentTarget
    val sessionState = playbackSession.sessionState
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

    private val _hostMode = MutableStateFlow(PlaybackHostMode.Hidden)
    val hostMode = _hostMode.asStateFlow()

    private var shouldResumeAfterBackgroundPause = false

    init {
        viewModelScope.launch {
            combine(currentTarget, miniPlayerAvailable) { target, enabled ->
                target to enabled
            }.collect { (target, enabled) ->
                if (target == null) {
                    shouldResumeAfterBackgroundPause = false
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
            livePlaybackController.openLive(route)
        }
    }

    fun togglePlayPause() {
        if (sessionState.value.isPlaying) {
            shouldResumeAfterBackgroundPause = false
            playbackSession.pause()
        } else {
            playbackSession.play()
        }
    }

    fun onEnterBackground() {
        if (!sessionState.value.playWhenReady) {
            shouldResumeAfterBackgroundPause = false
            return
        }
        shouldResumeAfterBackgroundPause = true
        playbackSession.pause()
    }

    fun onReturnForeground() {
        if (!shouldResumeAfterBackgroundPause) return
        shouldResumeAfterBackgroundPause = false
        if (currentTarget.value != null && !sessionState.value.playWhenReady) {
            playbackSession.play()
        }
    }

    fun close() {
        shouldResumeAfterBackgroundPause = false
        _hostMode.value = PlaybackHostMode.Hidden
        playbackSession.close()
    }
}
