package com.naaammme.bbspace.core.domain.live

import androidx.media3.common.Player
import com.naaammme.bbspace.core.model.LivePlaybackViewState
import kotlinx.coroutines.flow.StateFlow

interface LivePlaybackController {
    val player: StateFlow<Player?>
    val state: StateFlow<LivePlaybackViewState>

    suspend fun open(
        roomId: Long,
        preferredQuality: Int = 0,
        jumpFrom: Int,
        reportEntry: Boolean = true
    )

    fun play()
    fun pause()
    fun switchQuality(quality: Int)
    fun release()
}
