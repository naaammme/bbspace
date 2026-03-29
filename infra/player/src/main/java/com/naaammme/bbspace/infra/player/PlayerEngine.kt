package com.naaammme.bbspace.infra.player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

interface PlayerEngine {
    val snapshot: StateFlow<PlaybackSnapshot>

    fun getPlayerForView(): Player

    fun setSource(source: EngineSource, startPositionMs: Long? = null)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun stopForReuse(resetPosition: Boolean = true)
    fun release()
}
