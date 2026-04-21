package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

const val VOD_DANMAKU_SEGMENT_DURATION_MS = 360_000L

@Immutable
data class DanmakuItem(
    val id: Long,
    val idStr: String,
    val progressMs: Int,
    val mode: Int,
    val fontSize: Int,
    val color: Int,
    val midHash: String,
    val content: String,
    val createdAtEpochSecond: Long,
    val weight: Int,
    val action: String,
    val pool: Int,
    val attr: Int,
    val likeCount: Long,
    val animation: String,
    val extra: String,
    val colorfulType: Int,
    val type: Int,
    val oid: Long,
    val dmFromType: Int
)

@Immutable
data class VodDanmakuRequest(
    val videoId: VideoPlaybackId,
    val positionMs: Long,
    val durationMs: Long
) {
    val segmentIndex: Long
        get() = (positionMs.coerceAtLeast(0L) / VOD_DANMAKU_SEGMENT_DURATION_MS) + 1L

    val segmentStartMs: Long
        get() = (segmentIndex - 1L) * VOD_DANMAKU_SEGMENT_DURATION_MS
}

@Immutable
data class VodDanmakuSegment(
    val request: VodDanmakuRequest,
    val items: List<DanmakuItem>,
    val state: Int,
    val segmentRules: List<Long>,
    val colorfulSources: Map<Int, String>,
    val contextSrc: String
)
