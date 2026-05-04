package com.naaammme.bbspace.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.player.PlayerSettings
import com.naaammme.bbspace.core.domain.player.StreamPlaybackSession
import com.naaammme.bbspace.core.model.LivePlaybackError
import com.naaammme.bbspace.core.model.LivePlaybackViewState
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.PlayerSettingsState
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val playbackSession: StreamPlaybackSession,
    playerSettings: PlayerSettings
) : ViewModel() {

    val player = playbackSession.player
    private val route: StateFlow<LiveRoute?> = playbackSession.currentTarget
        .map { target -> (target as? StreamPlaybackTarget.Live)?.route }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )
    val playbackState: StateFlow<LivePlaybackViewState> = playbackSession.liveState
    val settingsState: StateFlow<PlayerSettingsState> = playerSettings.state
    val uiState: StateFlow<LiveUiState> = combine(
        route,
        playbackState
    ) { route, playbackState ->
        LiveUiState(
            route = route,
            playbackState = playbackState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LiveUiState()
    )

    private var startJob: Job? = null

    fun ensureStarted() {
        val target = route.value ?: return
        val state = playbackState.value
        if (
            state.playbackSource?.roomId == target.roomId ||
            (state.isPreparing && route.value?.roomId == target.roomId)
        ) {
            return
        }
        if (startJob?.isActive == true) return
        startJob = viewModelScope.launch {
            playbackSession.openLive(
                route = target,
                preferredQuality = state.playbackSource?.currentQn ?: 0
            )
        }
    }

    fun togglePlayPause() {
        if (playbackState.value.isPlaying) {
            playbackSession.pause()
        } else {
            playbackSession.play()
        }
    }

    fun switchQuality(qn: Int) {
        playbackSession.switchLiveQuality(qn)
    }

    fun retry() {
        val target = route.value ?: return
        startJob?.cancel()
        startJob = viewModelScope.launch {
            playbackSession.openLive(
                route = target,
                preferredQuality = playbackState.value.playbackSource?.currentQn ?: 0,
                reportEntry = false
            )
        }
    }

    override fun onCleared() {
        startJob?.cancel()
        startJob = null
        super.onCleared()
    }
}

internal fun LivePlaybackError.toUiMessage(): String {
    return when (this) {
        is LivePlaybackError.NoPlayableStream -> message
        is LivePlaybackError.RequestFailed -> message
    }
}
