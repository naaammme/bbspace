package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable
import java.net.URI
import java.net.URLDecoder

sealed interface VideoTarget {
    val src: VideoSrc

    @Immutable
    data class Ugc(
        val aid: Long,
        val cid: Long,
        val bvid: String? = null,
        override val src: VideoSrc = VideoTargetTool.feed()
    ) : VideoTarget

    @Immutable
    data class Pgc(
        val aid: Long = 0L,
        val cid: Long = 0L,
        val epId: Long = 0L,
        val seasonId: Long? = null,
        val subType: Int? = null,
        override val src: VideoSrc = VideoTargetTool.feed()
    ) : VideoTarget

    @Immutable
    data class Pugv(
        val aid: Long = 0L,
        val epId: Long = 0L,
        val seasonId: Long? = null,
        override val src: VideoSrc = VideoTargetTool.feed()
    ) : VideoTarget
}

fun VideoTarget.isSameEntry(other: VideoTarget?): Boolean {
    other ?: return false
    return when (this) {
        is VideoTarget.Ugc -> other is VideoTarget.Ugc && aid == other.aid
        is VideoTarget.Pgc -> other is VideoTarget.Pgc &&
            (
                epId > 0L && other.epId > 0L && epId == other.epId ||
                    aid > 0L && other.aid > 0L && aid == other.aid ||
                    seasonId != null && seasonId == other.seasonId
                )
        is VideoTarget.Pugv -> other is VideoTarget.Pugv &&
            (
                epId > 0L && other.epId > 0L && epId == other.epId ||
                    aid > 0L && other.aid > 0L && aid == other.aid ||
                    seasonId != null && seasonId == other.seasonId
                )
    }
}

fun VideoTarget.toPlaybackRequest(): PlaybackRequest {
    val ids = when (this) {
        is VideoTarget.Ugc -> VideoRequestIds(
            aid = aid,
            cid = cid,
            bvid = bvid
        )

        is VideoTarget.Pgc -> VideoRequestIds(
            aid = aid,
            cid = cid,
            epId = epId,
            seasonId = seasonId ?: 0L
        )

        is VideoTarget.Pugv -> VideoRequestIds(
            aid = aid,
            epId = epId,
            seasonId = seasonId ?: 0L
        )
    }
    val biz = when (this) {
        is VideoTarget.Ugc -> PlayBizInfo(biz = PlayBiz.UGC)
        is VideoTarget.Pgc -> PlayBizInfo(
            biz = PlayBiz.PGC,
            subType = subType,
            seasonId = seasonId?.takeIf { it > 0L },
            epId = epId.takeIf { it > 0L }
        )

        is VideoTarget.Pugv -> PlayBizInfo(
            biz = PlayBiz.PUGV,
            seasonId = seasonId?.takeIf { it > 0L },
            epId = epId.takeIf { it > 0L }
        )
    }
    return PlaybackRequest(
        ids = ids,
        src = src,
        biz = biz
    )
}

@Immutable
data class VideoSrc(
    val from: String = VideoTargetTool.FROM_FEED,
    val fromSpmid: String = VideoTargetTool.FROM_SPMID_FEED,
    val trackId: String? = null,
    val reportFlowData: String? = null
)

object VideoTargetTool {
    const val SPMID = "united.player-video-detail.0.0"
    const val FROM_FEED = "7"
    const val FROM_HISTORY = "64"
    const val FROM_SPACE = "66"
    const val FROM_SEARCH = "3"
    const val FROM_RELATE = "2"
    const val FROM_DYNAMIC = "6"
    const val FROM_DEFAULT = "6" //60
    const val FROM_SPMID_FEED = "tm.recommend.0.0"
    const val FROM_SPMID_HISTORY = "main.my-history.0.0"
    const val FROM_SPMID_WATCH_LATER = "main.later-watch.0.0"
    const val FROM_SPMID_FAVORITE = "main.my-fav.0.0"
    const val FROM_SPMID_SPACE = "main.space-contribution.0.0"
    const val FROM_SPMID_SEARCH = "search.search-result.0.0"
    const val FROM_SPMID_DYNAMIC = "dt.dt.video.0"
    const val FROM_SPMID_DEFAULT = "default-value"
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

    fun default(
        trackId: String? = null,
        reportFlowData: String? = null
    ): VideoSrc {
        return VideoSrc(
            from = FROM_DEFAULT,
            fromSpmid = FROM_SPMID_DEFAULT,
            trackId = trackId.blankToNull(),
            reportFlowData = reportFlowData.blankToNull()
        )
    }

    fun dynamic(
        trackId: String? = null,
        reportFlowData: String? = null
    ): VideoSrc {
        return VideoSrc(
            from = FROM_DYNAMIC,
            fromSpmid = FROM_SPMID_DYNAMIC,
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

    fun watchLater(
        trackId: String? = null,
        reportFlowData: String? = null
    ): VideoSrc {
        return VideoSrc(
            from = FROM_HISTORY,
            fromSpmid = FROM_SPMID_WATCH_LATER,
            trackId = trackId.blankToNull(),
            reportFlowData = reportFlowData.blankToNull()
        )
    }

    fun favorite(
        trackId: String? = null,
        reportFlowData: String? = null
    ): VideoSrc {
        return VideoSrc(
            from = FROM_DYNAMIC,
            fromSpmid = FROM_SPMID_FAVORITE,
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
