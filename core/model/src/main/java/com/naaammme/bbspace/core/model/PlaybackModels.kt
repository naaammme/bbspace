package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class VideoPlaybackId(
    val aid: Long,
    val cid: Long,
    val bvid: String? = null
)

enum class PlaybackControlMode {
    Default,
    Simple
}

@Immutable
data class PlaybackRequest(
    val videoId: VideoPlaybackId,
    val fromSpmid: String? = null,
    val trackId: String? = null,
    val reportFlowData: String? = null,
    val extraContent: Map<String, String> = emptyMap(),
    val seekToMs: Long? = null,
    val preferredQuality: Int? = null,
    val controlMode: PlaybackControlMode = PlaybackControlMode.Default
)

@Immutable
data class StreamLimitInfo(
    val title: String?,
    val message: String?,
    val uri: String?
)

@Immutable
data class QualityOption(
    val quality: Int,
    val format: String,
    val description: String,
    val displayDescription: String?,
    val needVip: Boolean,
    val needLogin: Boolean,
    val vipFree: Boolean,
    val supportDrm: Boolean,
    val limit: StreamLimitInfo?
)

@Immutable
data class ProgressiveSegment(
    val url: String,
    val durationMs: Long?
)

@Immutable
sealed interface PlaybackStream {
    val quality: Int
    val format: String
    val description: String
    val width: Int?
    val height: Int?
    val mimeType: String?
    val needVip: Boolean
    val needLogin: Boolean
    val supportDrm: Boolean

    @Immutable
    data class Dash(
        override val quality: Int,
        override val format: String,
        override val description: String,
        override val width: Int?,
        override val height: Int?,
        override val mimeType: String?,
        override val needVip: Boolean,
        override val needLogin: Boolean,
        override val supportDrm: Boolean,
        val videoUrl: String,
        val videoBackupUrls: List<String>,
        val audioId: Int?,
        val bandwidth: Int,
        val codecId: Int,
        val frameRate: String?
    ) : PlaybackStream

    @Immutable
    data class Progressive(
        override val quality: Int,
        override val format: String,
        override val description: String,
        override val width: Int?,
        override val height: Int?,
        override val mimeType: String?,
        override val needVip: Boolean,
        override val needLogin: Boolean,
        override val supportDrm: Boolean,
        val segments: List<ProgressiveSegment>
    ) : PlaybackStream
}

@Immutable
data class PlaybackAudio(
    val id: Int,
    val url: String,
    val backupUrls: List<String>,
    val bandwidth: Int,
    val codecId: Int,
    val mimeType: String?
)

@Immutable
data class PlaybackSource(
    val videoId: VideoPlaybackId,
    val durationMs: Long,
    val streams: List<PlaybackStream>,
    val audios: List<PlaybackAudio>,
    val qualityOptions: List<QualityOption>,
    val resumePositionMs: Long?,
    val isPreview: Boolean,
    val supportProject: Boolean
)

sealed interface PlaybackError {
    data class RequestFailed(val message: String, val cause: Throwable? = null) : PlaybackError
    data class NoPlayableStream(val message: String) : PlaybackError
}

@Immutable
data class PlayerSessionState(
    val currentRequest: PlaybackRequest? = null,
    val playbackSource: PlaybackSource? = null,
    val currentStream: PlaybackStream? = null,
    val currentAudio: PlaybackAudio? = null,
    val isPreparing: Boolean = false,
    val error: PlaybackError? = null
)
