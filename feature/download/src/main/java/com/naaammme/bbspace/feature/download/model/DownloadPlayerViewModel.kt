package com.naaammme.bbspace.feature.download.model

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.download.VideoDownloadRepository
import com.naaammme.bbspace.core.domain.player.DownloadPlaybackController
import com.naaammme.bbspace.core.domain.player.PlayerSettings
import com.naaammme.bbspace.core.model.DanmakuConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playbackController: DownloadPlaybackController,
    private val playerSettings: PlayerSettings,
    downloadRepository: VideoDownloadRepository
) : ViewModel() {

    private val taskId = savedStateHandle.get<Long>("taskId") ?: 0L
    private val danmakuSession = OfflineDanmakuSession(
        scope = viewModelScope,
        repository = downloadRepository
    )

    val player = playbackController.player
    val state = playbackController.state
    val settingsState = playerSettings.state
    val danmakuState = danmakuSession.state
    private var closed = false

    init {
        danmakuSession.bind(
            taskId = taskId,
            playbackStateFlow = state
        )
        viewModelScope.launch {
            playbackController.open(taskId)
        }
    }

    fun togglePlayPause() {
        if (state.value.isPlaying) {
            playbackController.pause()
        } else {
            playbackController.play()
        }
    }

    fun seekTo(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    fun pause() {
        playbackController.pause()
    }

    fun setSpeed(speed: Float) {
        playbackController.setSpeed(speed)
    }

    fun updateDanmaku(config: DanmakuConfig) {
        viewModelScope.launch {
            playerSettings.updateDanmaku(config)
        }
    }

    fun updateBackgroundPlayback(enabled: Boolean) {
        viewModelScope.launch {
            playerSettings.updatePlayback(
                settingsState.value.playback.copy(backgroundPlayback = enabled)
            )
        }
    }

    fun close() {
        if (closed) return
        closed = true
        danmakuSession.clear()
        playbackController.release()
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }
}
