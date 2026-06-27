package com.naaammme.bbspace.core.playback

import androidx.media3.common.Player
import com.naaammme.bbspace.core.model.LivePlaybackViewState
import com.naaammme.bbspace.core.model.LiveRoute
import kotlinx.coroutines.flow.StateFlow

interface LivePlaybackController {
    val player: StateFlow<Player?>
    val liveState: StateFlow<LivePlaybackViewState>

    suspend fun openLive(
        route: LiveRoute,
        preferredQuality: Int = 0,
        reportEntry: Boolean = true
    )

    fun play()
    fun pause()
    fun switchLiveQuality(quality: Int)
}
