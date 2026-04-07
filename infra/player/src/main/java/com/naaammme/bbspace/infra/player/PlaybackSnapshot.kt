package com.naaammme.bbspace.infra.player

enum class EnginePlaybackState {
    Idle,
    Buffering,
    Ready,
    Ended
}

enum class EngineDiscontinuityReason {
    AutoTransition,
    Seek,
    SeekAdjustment,
    Skip,
    Remove,
    Internal
}

data class PlaybackSnapshot(
    val playerInstanceId: Int = 0,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val playbackState: EnginePlaybackState = EnginePlaybackState.Idle,
    val positionMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val totalBufferedDurationMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoDecoderName: String? = null,
    val audioDecoderName: String? = null,
    val discontinuitySeq: Long = 0,
    val discontinuityReason: EngineDiscontinuityReason? = null,
    val errorMessage: String? = null
)
