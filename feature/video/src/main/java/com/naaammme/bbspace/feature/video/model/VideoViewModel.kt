package com.naaammme.bbspace.feature.video.model

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.data.player.PlayerSessionManager
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.VideoPlaybackId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VideoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    appSettings: AppSettings,
    private val sessionManager: PlayerSessionManager
) : ViewModel() {

    private var closed = false
    private var started = false
    private val _engineReady = MutableStateFlow(false)
    val engineReady = _engineReady.asStateFlow()
    private val req = run {
        val aid = savedStateHandle.get<Long>("aid") ?: 0L
        val cid = savedStateHandle.get<Long>("cid") ?: 0L
        if (aid > 0 && cid > 0) {
            PlaybackRequest(
                videoId = VideoPlaybackId(aid = aid, cid = cid)
            )
        } else {
            null
        }
    }

    fun getPlayerForView() = sessionManager.playerEngine.getPlayerForView()

    val uiState = combine(
        sessionManager.state,
        sessionManager.playerEngine.snapshot
    ) { session, snapshot ->
        VideoUiState(
            isLoading = session.isPreparing,
            playbackSource = session.playbackSource,
            currentStream = session.currentStream,
            currentAudio = session.currentAudio,
            snapshot = snapshot,
            error = session.error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VideoUiState()
    )

    val backgroundPlayback = appSettings.backgroundPlayback.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    init {
        viewModelScope.launch {
            sessionManager.prepareEngine()
            _engineReady.value = true
        }
    }

    fun ensureStarted() {
        if (started || !engineReady.value) return
        val request = req ?: return
        started = true
        viewModelScope.launch {
            sessionManager.start(request)
        }
    }

    fun togglePlayPause() {
        if (uiState.value.snapshot.isPlaying) {
            sessionManager.pause()
        } else {
            sessionManager.play()
        }
    }

    fun switchQuality(quality: Int) {
        sessionManager.switchQuality(quality)
    }

    fun switchAudio(audioId: Int) {
        sessionManager.switchAudio(audioId)
    }

    fun pause() {
        sessionManager.pause()
    }

    fun close() {
        if (closed) return
        closed = true
        sessionManager.closeCurrentSession()
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }
}
