package com.naaammme.bbspace.feature.download.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.settings.AppSettings
import com.naaammme.bbspace.core.download.VideoDownloadRepository
import com.naaammme.bbspace.core.playback.DownloadPlaybackController
import com.naaammme.bbspace.core.model.DanmakuConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playbackController: DownloadPlaybackController,
    private val playerSettings: AppSettings,
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
        danmakuSession.bind(taskId)
        viewModelScope.launch {
            state.collect { playback ->
                danmakuSession.onTick(playback.positionMs)
            }
        }
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
            playerSettings.setDanmaku(config)
        }
    }

    fun updateBackgroundPlayback(enabled: Boolean) {
        viewModelScope.launch {
            playerSettings.setBackgroundPlayback(enabled)
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
