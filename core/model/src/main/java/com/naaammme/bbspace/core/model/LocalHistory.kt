package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable
import java.util.Locale

@Immutable
data class VideoHistory(
    val uid: Long,
    val key: String,
    val biz: String,
    val aid: Long,
    val cid: Long,
    val bvid: String? = null,
    val epId: Long? = null,
    val seasonId: Long? = null,
    val title: String = "",
    val cover: String? = null,
    val part: Int? = null,
    val partTitle: String? = null,
    val ownerUid: Long? = null,
    val ownerName: String? = null,
    val durationMs: Long = 0L,
    val progressMs: Long = 0L,
    val watchMs: Long = 0L,
    val watchAt: Long = 0L,
    val updatedAt: Long = 0L,
    val finished: Boolean = false
)

@Immutable
data class VideoHistoryMeta(
    val title: String = "",
    val cover: String? = null,
    val ownerUid: Long? = null,
    val ownerName: String? = null,
    val part: Int? = null,
    val partTitle: String? = null
)

object LocalHistoryKey {
    const val KIND_VIDEO = "video"

    fun video(report: PlayReportParams): String {
        val biz = report.biz.name.lowercase(Locale.ROOT)
        val mainId = when {
            report.biz == PlayBiz.PGC && (report.epId ?: 0L) > 0L -> report.epId ?: 0L
            else -> report.aid
        }
        return "$biz:$mainId:${report.cid}"
    }

    fun videoId(
        uid: Long,
        key: String
    ): String {
        return "$uid:$KIND_VIDEO:$key"
    }
}
