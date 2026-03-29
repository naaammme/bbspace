package com.naaammme.bbspace.feature.video.model

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.data.player.PlayerSessionManager
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.VideoPlaybackId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VideoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionManager: PlayerSessionManager
) : ViewModel() {

    private var closed = false

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

    init {
        val aid = savedStateHandle.get<Long>("aid") ?: 0L
        val cid = savedStateHandle.get<Long>("cid") ?: 0L
        if (aid > 0 && cid > 0) {
            viewModelScope.launch {
                sessionManager.start(
                        PlaybackRequest(
                            videoId = VideoPlaybackId(aid = aid, cid = cid)
                        )
                    )
            }
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
