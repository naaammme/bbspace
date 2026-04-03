package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable
import java.net.URI
import java.net.URLDecoder

@Immutable
data class VideoJump(
    val aid: Long,
    val cid: Long,
    val src: VideoSrc = VideoJumpTool.feed()
)

@Immutable
data class VideoSrc(
    val from: String = VideoJumpTool.FROM_FEED,
    val fromSpmid: String = VideoJumpTool.FROM_SPMID_FEED,
    val trackId: String? = null,
    val reportFlowData: String? = null
)

object VideoJumpTool {
    const val SPMID = "united.player-video-detail.0.0"
    const val FROM_FEED = "7"
    const val FROM_SEARCH = "3"
    const val FROM_RELATE = "2"
    const val FROM_SPMID_FEED = "tm.recommend.0.0"
    const val FROM_SPMID_SEARCH = "search.search-result.0.0"
    private const val RELATE_SPMID_PRE = "united.player-video-detail.relatedvideo"

    fun feed(
        trackId: String? = null,
        reportFlowData: String? = null
    ): VideoSrc {
        return VideoSrc(
            from = FROM_FEED,
            fromSpmid = FROM_SPMID_FEED,
            trackId = trackId.blankToNull(),
            reportFlowData = reportFlowData.blankToNull()
        )
    }

    fun search(
        uri: String,
        fallbackTrackId: String? = null,
        fallbackReportFlowData: String? = null
    ): VideoSrc {
        return VideoSrc(
            from = FROM_SEARCH,
            fromSpmid = FROM_SPMID_SEARCH,
            trackId = arg(uri, "trackid")
                ?: arg(uri, "track_id")
                ?: fallbackTrackId.blankToNull(),
            reportFlowData = arg(uri, "report_flow_data")
                ?: fallbackReportFlowData.blankToNull()
        )
    }

    fun relate(
        trackId: String? = null,
        reportFlowData: String? = null,
        fromSpmidSuffix: String? = null
    ): VideoSrc {
        val suffix = fromSpmidSuffix.blankToNull() ?: "0"
        return VideoSrc(
            from = FROM_RELATE,
            fromSpmid = "$RELATE_SPMID_PRE.$suffix",
            trackId = trackId.blankToNull(),
            reportFlowData = reportFlowData.blankToNull()
        )
    }

    fun custom(
        from: String?,
        fromSpmid: String?,
        trackId: String? = null,
        reportFlowData: String? = null
    ): VideoSrc {
        return VideoSrc(
            from = from.blankToNull() ?: FROM_FEED,
            fromSpmid = fromSpmid.blankToNull() ?: FROM_SPMID_FEED,
            trackId = trackId.blankToNull(),
            reportFlowData = reportFlowData.blankToNull()
        )
    }

    fun aid(uri: String): Long? {
        val path = runCatching { URI(uri).path }
            .getOrNull()
            .orEmpty()
            .trimEnd('/')
        return path.substringAfterLast('/')
            .blankToNull()
            ?.toLongOrNull()
    }

    fun cid(uri: String): Long? {
        return arg(uri, "cid")?.toLongOrNull()
    }

    fun arg(
        uri: String,
        key: String
    ): String? {
        val raw = runCatching { URI(uri).rawQuery }.getOrNull().orEmpty()
        if (raw.isBlank()) return null
        return raw.split('&')
            .asSequence()
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                val name = if (idx >= 0) part.substring(0, idx) else part
                if (name.isBlank()) return@mapNotNull null
                val value = if (idx >= 0) part.substring(idx + 1) else ""
                URLDecoder.decode(name, UTF_8) to URLDecoder.decode(value, UTF_8)
            }
            .firstOrNull { it.first == key }
            ?.second
            .blankToNull()
    }

    private fun String?.blankToNull(): String? {
        return this?.takeIf { it.isNotBlank() }
    }

    private const val UTF_8 = "UTF-8"
}
