package com.naaammme.bbspace.core.published

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.naaammme.bbspace.core.model.PublishedRecord

@Entity(
    tableName = "published_record",
    indices = [
        Index(value = ["kind", "ctime", "item_id"]),
        Index(value = ["ctime", "item_id"])
    ]
)
data class PublishedRecordEntity(
    @PrimaryKey val key: String,
    val kind: Int,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "target_id")
    val targetId: Long,
    @ColumnInfo(name = "target_type")
    val targetType: Long,
    @ColumnInfo(name = "sender_mid")
    val senderMid: Long,
    @ColumnInfo(name = "sender_name")
    val senderName: String,
    @ColumnInfo(name = "sender_avatar")
    val senderAvatar: String,
    val content: String,
    val ctime: Long,
    @ColumnInfo(name = "root_id")
    val rootId: Long,
    @ColumnInfo(name = "parent_id")
    val parentId: Long,
    @ColumnInfo(name = "image_list_json")
    val imageListJson: String? = null
)

internal fun PublishedRecordEntity.toModel() = PublishedRecord(
    key = key,
    kind = kind,
    itemId = itemId,
    targetId = targetId,
    targetType = targetType,
    senderMid = senderMid,
    senderName = senderName,
    senderAvatar = senderAvatar,
    content = content,
    ctime = ctime,
    rootId = rootId,
    parentId = parentId,
    imageListJson = imageListJson
)

internal fun PublishedRecord.toEntity() = PublishedRecordEntity(
    key = key,
    kind = kind,
    itemId = itemId,
    targetId = targetId,
    targetType = targetType,
    senderMid = senderMid,
    senderName = senderName,
    senderAvatar = senderAvatar,
    content = content,
    ctime = ctime,
    rootId = rootId,
    parentId = parentId,
    imageListJson = imageListJson
)
