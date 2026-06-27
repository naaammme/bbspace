package com.naaammme.bbspace.core.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.naaammme.bbspace.core.model.PlaybackHistory
import com.naaammme.bbspace.core.model.PlaybackHistoryKey

@Entity(
    tableName = "playback_history",
    indices = [
        Index(value = ["uid", "updatedAt"])
    ]
)
data class PlaybackHistoryEntity(
    @PrimaryKey val id: String,
    val uid: Long,
    val biz: String,
    val aid: Long,
    val cid: Long,
    val epId: Long?,
    val seasonId: Long?,
    val durationMs: Long,
    val progressMs: Long,
    val watchMs: Long,
    val updatedAt: Long,
    val finished: Boolean
)

internal fun PlaybackHistory.toEntity() = PlaybackHistoryEntity(
    id = PlaybackHistoryKey.videoId(uid, key),
    uid = uid,
    biz = biz,
    aid = aid,
    cid = cid,
    epId = epId,
    seasonId = seasonId,
    durationMs = durationMs,
    progressMs = progressMs,
    watchMs = watchMs,
    updatedAt = updatedAt,
    finished = finished
)

internal fun PlaybackHistoryEntity.toModel() = PlaybackHistory(
    uid = uid,
    key = PlaybackHistoryKey.video(
        biz = biz,
        aid = aid,
        cid = cid,
        epId = epId
    ),
    biz = biz,
    aid = aid,
    cid = cid,
    epId = epId,
    seasonId = seasonId,
    durationMs = durationMs,
    progressMs = progressMs,
    watchMs = watchMs,
    updatedAt = updatedAt,
    finished = finished
)
