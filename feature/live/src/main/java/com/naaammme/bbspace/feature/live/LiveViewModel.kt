package com.naaammme.bbspace.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.live.LiveRoomMessageRepository
import com.naaammme.bbspace.core.domain.player.PlayerSettings
import com.naaammme.bbspace.core.domain.player.StreamPlaybackSession
import com.naaammme.bbspace.core.model.LivePlaybackError
import com.naaammme.bbspace.core.model.LivePlaybackViewState
import com.naaammme.bbspace.core.model.LiveRoomPanelState
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.LiveRoomSessionState
import com.naaammme.bbspace.core.model.PlayerSettingsState
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveViewModel @Inject constructor(
    private val playbackSession: StreamPlaybackSession,
    liveRoomMessageRepository: LiveRoomMessageRepository,
    private val playerSettings: PlayerSettings
) : ViewModel() {
    val player = playbackSession.player
    val route: StateFlow<LiveRoute?> = playbackSession.currentTarget
        .map { target -> (target as? StreamPlaybackTarget.Live)?.route }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )
    val playbackState: StateFlow<LivePlaybackViewState> = playbackSession.liveState
    val settingsState: StateFlow<PlayerSettingsState> = playerSettings.state
    private val emptyRoomSession = MutableStateFlow(LiveRoomSessionState())
    val roomSession: StateFlow<LiveRoomSessionState> = route
        .flatMapLatest { curRoute ->
            val roomId = curRoute?.roomId ?: 0L
            if (roomId > 0L) {
                liveRoomMessageRepository.observeRoomSession(roomId)
            } else {
                emptyRoomSession
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000,
                replayExpirationMillis = 0
            ),
            initialValue = LiveRoomSessionState()
        )

    val roomPanel: StateFlow<LiveRoomPanelUiState> = roomSession
        .map { LiveRoomPanelUiState(popularCount = it.popularCount, panel = it.panel) }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000,
                replayExpirationMillis = 0
            ),
            initialValue = LiveRoomPanelUiState()
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

    private var danmakuUpdateJob: Job? = null

    fun setDanmakuEnabled(enabled: Boolean) {
        val cur = settingsState.value.danmaku
        if (cur.enabled == enabled) return
        danmakuUpdateJob?.cancel()
        danmakuUpdateJob = viewModelScope.launch {
            playerSettings.updateDanmaku(cur.copy(enabled = enabled))
        }
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

data class LiveRoomPanelUiState(
    val popularCount: Long = 0L,
    val panel: LiveRoomPanelState = LiveRoomPanelState()
)

internal fun LivePlaybackError.toUiMessage(): String {
    return when (this) {
        is LivePlaybackError.NoPlayableStream -> message
        is LivePlaybackError.RequestFailed -> message
    }
}
