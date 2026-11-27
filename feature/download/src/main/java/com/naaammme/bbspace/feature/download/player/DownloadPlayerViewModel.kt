package com.naaammme.bbspace.feature.download.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.download.VideoDownloadRepository
import com.naaammme.bbspace.core.model.DanmakuConfig
import com.naaammme.bbspace.core.playback.DownloadPlaybackController
import com.naaammme.bbspace.core.settings.DanmakuPrefs
import com.naaammme.bbspace.core.settings.PlaybackPrefs
import com.naaammme.bbspace.core.settings.stateInWhileSubscribed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playbackController: DownloadPlaybackController,
    private val playbackPrefs: PlaybackPrefs,
    private val danmakuPrefs: DanmakuPrefs,
    downloadRepository: VideoDownloadRepository
) : ViewModel() {

    private val taskId = savedStateHandle.get<Long>("taskId") ?: 0L
    private val danmakuSession = OfflineDanmakuSession(
        scope = viewModelScope,
        repository = downloadRepository
    )

    val player = playbackController.player
    val state = playbackController.state
    val danmakuState = danmakuSession.state
    val autoRotateFullscreen: StateFlow<Boolean> =
        playbackPrefs.autoRotateFullscreen.stateIn(viewModelScope)
    val backgroundPlayback: StateFlow<Boolean> =
        playbackPrefs.backgroundPlayback.stateIn(viewModelScope)
    val danmakuConfig: StateFlow<DanmakuConfig> = danmakuPrefs.config
        .stateInWhileSubscribed(viewModelScope, danmakuPrefs.currentConfig())
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

    fun toggleDanmaku() {
        viewModelScope.launch {
            danmakuPrefs.enabled.update { !it }
        }
    }

    fun updateDanmaku(transform: (DanmakuConfig) -> DanmakuConfig) {
        viewModelScope.launch {
            danmakuPrefs.update(transform)
        }
    }

    fun updateBackgroundPlayback(enabled: Boolean) {
        viewModelScope.launch {
            playbackPrefs.backgroundPlayback.set(enabled)
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
