package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

enum class LiveStatus {
    Offline,
    Living,
    Round;

    companion object {
        fun from(raw: Int): LiveStatus {
            return when (raw) {
                1 -> Living
                2 -> Round
                else -> Offline
            }
        }
    }
}

@Immutable
data class LiveQualityOption(
    val qn: Int,
    val description: String
)

@Immutable
data class LivePlaybackSource(
    val roomId: Long,
    val liveStatus: LiveStatus,
    val currentQn: Int,
    val currentDescription: String,
    val qualityOptions: List<LiveQualityOption>,
    val protocol: String,
    val format: String,
    val codec: String,
    val primaryUrl: String,
    val backupUrls: List<String>,
    val session: String?
)

sealed interface LivePlaybackError {
    data class RequestFailed(
        val message: String,
        val cause: Throwable? = null
    ) : LivePlaybackError

    data class NoPlayableStream(
        val message: String
    ) : LivePlaybackError
}

@Immutable
data class LivePlaybackViewState(
    val isPreparing: Boolean = false,
    val playbackSource: LivePlaybackSource? = null,
    val error: LivePlaybackError? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val hasRenderedFirstFrame: Boolean = false,
    val playerError: String? = null
)
