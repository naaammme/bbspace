package com.naaammme.bbspace.core.playback

import androidx.media3.common.Player
import com.naaammme.bbspace.core.model.DanmakuSessionState
import com.naaammme.bbspace.core.model.PlaybackProgress
import com.naaammme.bbspace.core.model.VideoPlaybackState
import com.naaammme.bbspace.core.model.VideoTarget
import kotlinx.coroutines.flow.StateFlow

interface VideoPlaybackController {
    val player: StateFlow<Player?>
    val videoState: StateFlow<VideoPlaybackState>
    val playbackProgress: StateFlow<PlaybackProgress>
    val danmakuState: StateFlow<DanmakuSessionState>

    fun openVideo(target: VideoTarget)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun switchVideoQuality(quality: Int)
    fun switchVideoAudio(audioId: Int)
}
