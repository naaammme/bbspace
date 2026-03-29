package com.naaammme.bbspace.infra.player

enum class EnginePlaybackState {
    Idle,
    Buffering,
    Ready,
    Ended
}

data class PlaybackSnapshot(
    val isPlaying: Boolean = false,
    val playbackState: EnginePlaybackState = EnginePlaybackState.Idle,
    val positionMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val errorMessage: String? = null
)
