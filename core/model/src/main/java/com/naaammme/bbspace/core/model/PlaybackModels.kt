package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

enum class PlayBiz(
    val reportType: Int
) {
    UGC(3),
    PGC(4),
    PUGV(10);

    companion object {
        fun from(raw: String?): PlayBiz {
            return values().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UGC
        }
    }
}

@Immutable
data class PlayBizInfo(
    val biz: PlayBiz = PlayBiz.UGC,
    val type: Int? = null,
    val playType: Int? = null,
    val subType: Int? = null,
    val seasonId: Long? = null,
    val epId: Long? = null,
    val roomId: Long? = null,
    val inlineScene: String? = null,
    val materialNo: String? = null,
    val securityLevel: String? = null
)

@Immutable
data class PlayReportParams(
    val biz: PlayBiz,
    val aid: Long,
    val cid: Long,
    val bvid: String? = null,
    val seasonId: Long? = null,
    val epId: Long? = null,
    val subType: Int? = null
) {
    val type: Int
        get() = biz.reportType
}

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
data class PlayableParams(
    val videoId: VideoPlaybackId,
    val src: VideoSrc = VideoJumpTool.feed(),
    val biz: PlayBizInfo = PlayBizInfo(),
    val fromScene: String = DEFAULT_FROM_SCENE,
    val adExtra: String? = null,
    val extraResolve: Map<String, String> = emptyMap()
) {
    fun getResolveParams(
        seekToMs: Long? = null,
        preferredQuality: Int? = null,
        controlMode: PlaybackControlMode = PlaybackControlMode.Default
    ): PlaybackRequest {
        return PlaybackRequest(
            playable = this,
            seekToMs = seekToMs,
            preferredQuality = preferredQuality,
            controlMode = controlMode
        )
    }

    fun getResolveExtraContent(): Map<String, String> {
        return buildMap {
            src.trackId?.takeIf(String::isNotBlank)?.let { put("track_id", it) }
            src.reportFlowData?.takeIf(String::isNotBlank)?.let { put("report_flow_data", it) }
            when (biz.biz) {
                PlayBiz.UGC -> Unit
                PlayBiz.PGC -> {
                    biz.epId?.let { put("ep_id", it.toString()) }
                    biz.seasonId?.let { put("season_id", it.toString()) }
                    biz.roomId?.let { put("room_id", it.toString()) }
                    biz.inlineScene?.takeIf(String::isNotBlank)?.let { put("inline_scene", it) }
                    biz.materialNo?.takeIf(String::isNotBlank)?.let { put("material_no", it) }
                    biz.securityLevel?.takeIf(String::isNotBlank)?.let { put("security_level", it) }
                }
                PlayBiz.PUGV -> {
                    biz.seasonId?.let { put("season_id", it.toString()) }
                }
            }
            putAll(extraResolve)
        }
    }

    fun getReportCommonParams(): PlayReportParams {
        return PlayReportParams(
            biz = biz.biz,
            aid = videoId.aid,
            cid = videoId.cid,
            bvid = videoId.bvid,
            seasonId = biz.seasonId,
            epId = biz.epId,
            subType = biz.subType
        )
    }

    fun getDanmakuResolveParams(
        positionMs: Long,
        durationMs: Long
    ): DanmakuRequest {
        return DanmakuRequest(
            videoId = videoId,
            positionMs = positionMs,
            durationMs = durationMs
        )
    }

    private companion object {
        const val DEFAULT_FROM_SCENE = "normal"
    }
}

@Immutable
data class PlaybackRequest(
    val playable: PlayableParams,
    val seekToMs: Long? = null,
    val preferredQuality: Int? = null,
    val controlMode: PlaybackControlMode = PlaybackControlMode.Default
) {
    val videoId: VideoPlaybackId
        get() = playable.videoId
}

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
    val biz: PlayBiz,
    val report: PlayReportParams,
    val durationMs: Long,
    val streams: List<PlaybackStream>,
    val audios: List<PlaybackAudio>,
    val qualityOptions: List<QualityOption>,
    val resumePositionMs: Long?,
    val isPreview: Boolean,
    val supportProject: Boolean,
    val supplementType: String?
)

@Immutable
data class PlaybackCdn(
    val label: String,
    val videoUrl: String,
    val audioUrl: String?
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
    val cdnIndex: Int = 0,
    val isPreparing: Boolean = false,
    val error: PlaybackError? = null
)

fun buildPlaybackCdns(
    stream: PlaybackStream?,
    audio: PlaybackAudio?
): List<PlaybackCdn> {
    val dash = stream as? PlaybackStream.Dash ?: return emptyList()

    fun urls(primaryUrl: String?, backupUrls: List<String>): List<String> {
        return buildList {
            addAll(backupUrls.filter(String::isNotBlank))
            primaryUrl?.takeIf(String::isNotBlank)?.let(::add)
        }.distinct()
    }

    fun host(url: String): String {
        val value = url
            .substringAfter("://", "")
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore(":")
        if (value.isBlank()) return ""
        return value
            .removePrefix("upos-sz-")
            .removePrefix("upos-hz-")
            .substringBefore('.')
    }

    val videoUrls = urls(dash.videoUrl, dash.videoBackupUrls)
    if (videoUrls.isEmpty()) return emptyList()
    val audioUrls = urls(audio?.url, audio?.backupUrls ?: emptyList())
    val backupCount = maxOf(
        dash.videoBackupUrls.filter(String::isNotBlank).distinct().size,
        audio?.backupUrls?.filter(String::isNotBlank)?.distinct()?.size ?: 0
    )

    return List(maxOf(videoUrls.size, audioUrls.size)) { index ->
        val videoUrl = videoUrls[index.coerceAtMost(videoUrls.lastIndex)]
        val audioUrl = if (audioUrls.isEmpty()) {
            null
        } else {
            audioUrls[index.coerceAtMost(audioUrls.lastIndex)]
        }
        val hosts = listOf(host(videoUrl), audioUrl?.let(::host).orEmpty())
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" + ")
        val prefix = if (index < backupCount) "Backup ${index + 1}" else "Base"
        PlaybackCdn(
            label = if (hosts.isBlank()) prefix else "$prefix $hosts",
            videoUrl = videoUrl,
            audioUrl = audioUrl
        )
    }
}
