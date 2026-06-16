package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

enum class PlaybackState {
    Idle,
    Buffering,
    Ready,
    Ended
}

@Immutable
data class VideoPlaybackState(
    val biz: PlayBiz,
    val ids: ResolvedVideoIds = ResolvedVideoIds(),
    val detail: VideoDetail? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val isPreparing: Boolean = false,
    val playbackSource: PlaybackSource? = null,
    val currentStream: PlaybackStream? = null,
    val currentAudio: PlaybackAudio? = null,
    val error: PlaybackError? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val speed: Float = 1f,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoDecoderName: String? = null,
    val audioDecoderName: String? = null,
    val hasRenderedFirstFrame: Boolean = false,
    val seekEventId: Long = 0L,
    val playerError: String? = null
)
