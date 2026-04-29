package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

enum class PlaybackState {
    Idle,
    Buffering,
    Ready,
    Ended
}

@Immutable
data class PlaybackViewState(
    val isPreparing: Boolean = false,
    val playbackSource: PlaybackSource? = null,
    val currentStream: PlaybackStream? = null,
    val currentAudio: PlaybackAudio? = null,
    val cdnIndex: Int = 0,
    val error: PlaybackError? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val totalBufferedDurationMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val hasRenderedFirstFrame: Boolean = false,
    val seekEventId: Long = 0L,
    val playerError: String? = null
)
