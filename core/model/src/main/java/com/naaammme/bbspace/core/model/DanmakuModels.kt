package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable
import kotlin.math.min

const val VIDEO_DANMAKU_SEGMENT_DURATION_MS = 360_000L

@Immutable
data class DanmakuRequest(
    val videoId: VideoPlaybackId,
    val positionMs: Long,
    val durationMs: Long
) {
    val segmentIndex: Long
        get() = (positionMs.coerceAtLeast(0L) / VIDEO_DANMAKU_SEGMENT_DURATION_MS) + 1L

    val segmentStartMs: Long
        get() = (segmentIndex - 1L) * VIDEO_DANMAKU_SEGMENT_DURATION_MS

    val segmentEndMsExclusive: Long
        get() = min(durationMs.coerceAtLeast(0L), segmentStartMs + VIDEO_DANMAKU_SEGMENT_DURATION_MS)
}

@Immutable
data class DanmakuElem(
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
data class DanmakuSegment(
    val request: DanmakuRequest,
    val elems: List<DanmakuElem>,
    val state: Int,
    val segmentRules: List<Long>,
    val colorfulSources: Map<Int, String>,
    val contextSrc: String
)
