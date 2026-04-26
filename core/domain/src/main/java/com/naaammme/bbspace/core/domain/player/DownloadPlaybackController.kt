package com.naaammme.bbspace.core.domain.player

import androidx.media3.common.Player
import com.naaammme.bbspace.core.model.DownloadPlaybackState
import kotlinx.coroutines.flow.StateFlow

interface DownloadPlaybackController {
    val player: StateFlow<Player?>
    val state: StateFlow<DownloadPlaybackState>

    suspend fun open(taskId: Long)

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun release()
}
