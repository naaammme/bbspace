package com.naaammme.bbspace.infra.player

import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.naaammme.bbspace.core.model.PlaybackProgress
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
    val backBufferMs: Int = 2_000,
    val decoderMode: DecoderMode = DecoderMode.Hard,
    val decoderFallback: Boolean = true
)

interface PlayerEngine {
    val player: StateFlow<Player?>
    val currentSource: StateFlow<EngineSource?>
    val playbackState: StateFlow<PlayerPlaybackState>
    val playbackProgress: StateFlow<PlaybackProgress>

    fun updateConfig(config: PlayerConfig)
    fun setSource(
        source: EngineSource,
        startPositionMs: Long? = null,
        playWhenReady: Boolean = true,
        metadata: MediaMetadata? = null
    )
    fun play()
    fun pause()
    fun setSpeed(speed: Float)
    fun seekTo(positionMs: Long)
    fun setMediaMetadata(metadata: MediaMetadata)
    fun stopForReuse(resetPosition: Boolean = true)
    fun release()
}
