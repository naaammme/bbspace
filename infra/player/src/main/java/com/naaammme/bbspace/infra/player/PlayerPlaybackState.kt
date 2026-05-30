package com.naaammme.bbspace.infra.player

import com.naaammme.bbspace.core.model.PlaybackState

data class PlayerPlaybackState(
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val speed: Float = 1f,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val firstFrameSeq: Long = 0L,
    val videoDecoderName: String? = null,
    val audioDecoderName: String? = null,
    val seekEventSeq: Long = 0L,
    val errorMessage: String? = null
)
