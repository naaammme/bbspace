package com.naaammme.bbspace.core.playback

import androidx.media3.common.Player
import com.naaammme.bbspace.core.model.StreamPlaybackSessionState
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import kotlinx.coroutines.flow.StateFlow

interface StreamPlaybackSession {
    val player: StateFlow<Player?>
    val currentTarget: StateFlow<StreamPlaybackTarget?>
    val sessionState: StateFlow<StreamPlaybackSessionState>

    suspend fun prepare()

    fun play()
    fun pause()
    fun close()
}
