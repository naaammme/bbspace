package com.naaammme.bbspace.core.data.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_history",
    indices = [
        Index(value = ["uid", "updatedAt"])
    ]
)
data class LocalHistoryEntity(
    @PrimaryKey val id: String,
    val uid: Long,
    val biz: String,
    val aid: Long,
    val cid: Long,
    val bvid: String?,
    val epId: Long?,
    val seasonId: Long?,
    val title: String,
    val cover: String?,
    val part: Int?,
    val partTitle: String?,
    val ownerUid: Long?,
    val ownerName: String?,
    val durationMs: Long,
    val progressMs: Long,
    val watchMs: Long,
    val updatedAt: Long,
    val finished: Boolean
)
