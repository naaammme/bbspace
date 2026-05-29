package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

enum class StreamPlaybackKind {
    Video,
    Live
}

sealed interface StreamPlaybackTarget {
    val kind: StreamPlaybackKind

    @Immutable
    data class Video(
        val target: VideoTarget
    ) : StreamPlaybackTarget {
        override val kind: StreamPlaybackKind = StreamPlaybackKind.Video
    }

    @Immutable
    data class Live(
        val route: LiveRoute
    ) : StreamPlaybackTarget {
        override val kind: StreamPlaybackKind = StreamPlaybackKind.Live
    }
}

@Immutable
data class StreamPlaybackSessionState(
    val target: StreamPlaybackTarget? = null,
    val isPreparing: Boolean = false,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val title: String = "",
    val subtitle: String? = null,
    val cover: String? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val hasRenderedFirstFrame: Boolean = false,
    val playerError: String? = null
)
