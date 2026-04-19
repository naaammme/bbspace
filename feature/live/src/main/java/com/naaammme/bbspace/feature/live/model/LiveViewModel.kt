package com.naaammme.bbspace.feature.live.model

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.live.LivePlaybackController
import com.naaammme.bbspace.core.model.LivePlaybackError
import com.naaammme.bbspace.core.model.LivePlaybackViewState
import com.naaammme.bbspace.core.model.LiveRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LiveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playbackController: LivePlaybackController
) : ViewModel() {

    val route: LiveRoute? = savedStateHandle.toLiveRoute()
    val player = playbackController.player
    val playbackState: StateFlow<LivePlaybackViewState> = playbackController.state

    private var startJob: Job? = null

    fun ensureStarted() {
        val target = route ?: return
        if (playbackState.value.playbackSource?.roomId == target.roomId) return
        if (startJob?.isActive == true) return
        startJob = viewModelScope.launch {
            playbackController.open(target.roomId)
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
        playbackController.switchQuality(qn)
    }

    fun retry() {
        val target = route ?: return
        startJob?.cancel()
        startJob = viewModelScope.launch {
            playbackController.open(
                roomId = target.roomId,
                preferredQuality = playbackState.value.playbackSource?.currentQn ?: 0
            )
        }
    }

    fun pause() {
        playbackController.pause()
    }

    fun closePage() {
        startJob?.cancel()
        startJob = null
        playbackController.release()
    }

    override fun onCleared() {
        closePage()
        super.onCleared()
    }
}

private fun SavedStateHandle.toLiveRoute(): LiveRoute? {
    val roomId = get<Long>("roomId")?.takeIf { it > 0L } ?: return null
    return LiveRoute(
        roomId = roomId,
        title = get<String>("title")?.takeIf(String::isNotBlank),
        cover = get<String>("cover")?.takeIf(String::isNotBlank),
        ownerName = get<String>("ownerName")?.takeIf(String::isNotBlank),
        onlineText = get<String>("onlineText")?.takeIf(String::isNotBlank)
    )
}

internal fun LivePlaybackError.toUiMessage(): String {
    return when (this) {
        is LivePlaybackError.NoPlayableStream -> message
        is LivePlaybackError.RequestFailed -> message
    }
}
