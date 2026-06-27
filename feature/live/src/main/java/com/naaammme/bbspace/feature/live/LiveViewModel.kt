package com.naaammme.bbspace.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.settings.AppSettings
import com.naaammme.bbspace.core.live.LiveRoomMessageRepository
import com.naaammme.bbspace.core.playback.LivePlaybackController
import com.naaammme.bbspace.core.model.LivePlaybackError
import com.naaammme.bbspace.core.model.LivePlaybackViewState
import com.naaammme.bbspace.core.model.LiveRoomPanelState
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.LiveRouteTool
import com.naaammme.bbspace.core.model.LiveRoomSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
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
    private val playbackController: LivePlaybackController,
    private val liveRoomMessageRepository: LiveRoomMessageRepository,
    private val playerSettings: AppSettings
) : ViewModel() {
    val player = playbackController.player
    private val _route = MutableStateFlow<LiveRoute?>(null)
    val route: StateFlow<LiveRoute?> = _route
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )
    val playbackState: StateFlow<LivePlaybackViewState> = playbackController.liveState
    val settingsState = playerSettings.state
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
    val popularCount: StateFlow<Long> = roomSession
        .map { it.popularCount }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000,
                replayExpirationMillis = 0
            ),
            initialValue = 0L
        )
    val roomPanel: StateFlow<LiveRoomPanelState> = roomSession
        .map { it.panel }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000,
                replayExpirationMillis = 0
            ),
            initialValue = LiveRoomPanelState()
        )

    private var startJob: Job? = null

    fun openRoute(route: LiveRoute) {
        _route.value = route
    }

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
            playbackController.openLive(
                route = target,
                preferredQuality = state.playbackSource?.currentQn ?: 0
            )
        }
    }

    fun togglePlayPause() {
        if (playbackState.value.isPlaying) {
            playbackController.pause()
        } else {
            playbackController.play()
        }
    }

    fun switchQuality(qn: Int) {
        playbackController.switchLiveQuality(qn)
    }

    private var danmakuUpdateJob: Job? = null

    fun setDanmakuEnabled(enabled: Boolean) {
        danmakuUpdateJob?.cancel()
        danmakuUpdateJob = viewModelScope.launch {
            val cur = playerSettings.state.first().danmaku
            if (cur.enabled == enabled) return@launch
            playerSettings.setDanmaku(cur.copy(enabled = enabled))
        }
    }

    fun retry() {
        val target = route.value ?: return
        startJob?.cancel()
        startJob = viewModelScope.launch {
            playbackController.openLive(
                route = target,
                preferredQuality = playbackState.value.playbackSource?.currentQn ?: 0,
                reportEntry = false
            )
        }
    }

    suspend fun sendDanmaku(content: String) {
        val curRoute = route.value
        val roomId = curRoute?.roomId ?: 0L
        val jumpFrom = curRoute?.jumpFrom ?: LiveRouteTool.JUMP_FROM_UNKNOWN
        liveRoomMessageRepository.sendDanmaku(
            roomId = roomId,
            content = content,
            jumpFrom = jumpFrom
        )
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
