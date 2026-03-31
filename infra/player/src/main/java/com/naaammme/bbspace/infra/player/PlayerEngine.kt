package com.naaammme.bbspace.infra.player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

enum class DecoderMode {
    Hard,
    Soft
}

data class PlayerConfig(
    val minBufferMs: Int = 2_000,
    val maxBufferMs: Int = 15_000,
    val playBufferMs: Int = 250,
    val rebufferMs: Int = 500,
    val backBufferMs: Int = 5_000,
    val decoderMode: DecoderMode = DecoderMode.Hard,
    val decoderFallback: Boolean = true
)

interface PlayerEngine {
    val snapshot: StateFlow<PlaybackSnapshot>

    fun getPlayerForView(): Player

    fun updateConfig(config: PlayerConfig)
    fun setSource(source: EngineSource, startPositionMs: Long? = null)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun stopForReuse(resetPosition: Boolean = true)
    fun release()
}
