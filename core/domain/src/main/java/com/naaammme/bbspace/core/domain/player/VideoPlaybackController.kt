package com.naaammme.bbspace.core.domain.player

import androidx.media3.common.Player
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.PlaybackViewState
import com.naaammme.bbspace.core.model.VideoHistoryMeta
import kotlinx.coroutines.flow.StateFlow

interface VideoPlaybackController {
    suspend fun connect(): Session

    interface Session {
        val player: StateFlow<Player?>
        val state: StateFlow<PlaybackViewState>

        suspend fun open(request: PlaybackRequest)

        fun play()
        fun pause()
        fun seekTo(positionMs: Long)
        fun setSpeed(speed: Float)
        fun switchQuality(quality: Int)
        fun switchAudio(audioId: Int)
        fun switchCdn(index: Int)

        fun updateMeta(meta: VideoHistoryMeta?)
        fun release()
    }
}
