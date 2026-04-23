package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable
import java.net.URI
import java.net.URLDecoder

sealed interface VideoRoute {
    val src: VideoSrc

    @Immutable
    data class Ugc(
        val aid: Long,
        val cid: Long,
        val bvid: String? = null,
        override val src: VideoSrc = VideoRouteTool.feed()
    ) : VideoRoute

    @Immutable
    data class Pgc(
        val epId: Long,
        val seasonId: Long? = null,
        val subType: Int? = null,
        override val src: VideoSrc = VideoRouteTool.feed()
    ) : VideoRoute

    @Immutable
    data class Pugv(
        val epId: Long,
        val seasonId: Long? = null,
        override val src: VideoSrc = VideoRouteTool.feed()
    ) : VideoRoute
}

fun VideoRoute.toPlayableParams(): PlayableParams? {
    return when (this) {
        is VideoRoute.Ugc -> PlayableParams(
            videoId = VideoPlaybackId(
                aid = aid,
                cid = cid,
                bvid = bvid
            ),
            src = src
        )

        is VideoRoute.Pgc -> PlayableParams(
            videoId = VideoPlaybackId(
                aid = 0L,
                cid = 0L
            ),
            src = src,
            biz = PlayBizInfo(
                biz = PlayBiz.PGC,
                subType = subType,
                seasonId = seasonId,
                epId = epId
            )
        )

        is VideoRoute.Pugv -> PlayableParams(
            videoId = VideoPlaybackId(
                aid = 0L,
                cid = 0L
            ),
            src = src,
            biz = PlayBizInfo(
                biz = PlayBiz.PUGV,
                seasonId = seasonId,
                epId = epId
            )
        )
    }
}

@Immutable
data class VideoSrc(
    val from: String = VideoRouteTool.FROM_FEED,
    val fromSpmid: String = VideoRouteTool.FROM_SPMID_FEED,
    val trackId: String? = null,
    val reportFlowData: String? = null
)

object VideoRouteTool {
    const val SPMID = "united.player-video-detail.0.0"
    const val FROM_FEED = "7"
    const val FROM_HISTORY = "64"
    const val FROM_SPACE = "66"
    const val FROM_SEARCH = "3"
    const val FROM_RELATE = "2"
    const val FROM_SPMID_FEED = "tm.recommend.0.0"
    const val FROM_SPMID_HISTORY = "main.my-history.0.0"
    const val FROM_SPMID_SPACE = "main.space-contribution.0.0"
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

    fun history(
        trackId: String? = null,
        reportFlowData: String? = null
    ): VideoSrc {
        return VideoSrc(
            from = FROM_HISTORY,
            fromSpmid = FROM_SPMID_HISTORY,
            trackId = trackId.blankToNull(),
            reportFlowData = reportFlowData.blankToNull()
        )
    }

    fun space(
        trackId: String? = null,
        reportFlowData: String? = null
    ): VideoSrc {
        return VideoSrc(
            from = FROM_SPACE,
            fromSpmid = FROM_SPMID_SPACE,
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

    fun bvid(uri: String): String? {
        val path = runCatching { URI(uri).path }
            .getOrNull()
            .orEmpty()
        val bv = BV_REGEX.find(path)?.value
            ?: arg(uri, "bvid")
            ?: arg(uri, "BVID")
        return bv.blankToNull()
    }

    fun cid(uri: String): Long? {
        return arg(uri, "cid")?.toLongOrNull()
    }

    fun epId(uri: String): Long? {
        val path = runCatching { URI(uri).path }
            .getOrNull()
            .orEmpty()
            .trimEnd('/')
        val last = path.substringAfterLast('/')
        return when {
            last.startsWith("ep") -> last.removePrefix("ep").toLongOrNull()
            else -> arg(uri, "ep_id")?.toLongOrNull()
        }
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
    private val BV_REGEX = Regex("BV[0-9A-Za-z]+")
}
