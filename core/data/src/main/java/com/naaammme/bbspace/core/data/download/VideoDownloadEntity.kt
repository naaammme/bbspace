package com.naaammme.bbspace.core.data.download

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.model.VideoDownloadKind
import com.naaammme.bbspace.core.model.VideoDownloadProgress
import com.naaammme.bbspace.core.model.VideoDownloadRequest
import com.naaammme.bbspace.core.model.VideoDownloadTask
import com.naaammme.bbspace.core.model.VideoDownloadTaskStatus
import com.naaammme.bbspace.core.model.fallbackTitle

@Entity(
    tableName = "video_download_task",
    indices = [
        Index(
            value = ["biz", "aid", "cid", "ep_id", "season_id", "kind"],
            unique = true
        )
    ]
)
data class VideoDownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val cover: String?,
    @ColumnInfo(name = "owner_uid")
    val ownerUid: Long?,
    @ColumnInfo(name = "owner_name")
    val ownerName: String?,
    val biz: String,
    val aid: Long,
    val cid: Long,
    val bvid: String?,
    @ColumnInfo(name = "ep_id")
    val epId: Long,
    @ColumnInfo(name = "season_id")
    val seasonId: Long,
    val kind: String,
    @ColumnInfo(name = "video_quality")
    val videoQuality: Int,
    @ColumnInfo(name = "audio_quality")
    val audioQuality: Int,
    val status: String,
    @ColumnInfo(name = "progress_type")
    val progressType: String?,
    @ColumnInfo(name = "progress_label")
    val progressLabel: String?,
    @ColumnInfo(name = "done_bytes")
    val doneBytes: Long,
    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long,
    val error: String?,
    @ColumnInfo(name = "video_path")
    val videoPath: String?,
    @ColumnInfo(name = "audio_path")
    val audioPath: String?,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long
)

fun VideoDownloadEntity.toModel(): VideoDownloadTask {
    val resolvedKind = VideoDownloadKind.from(kind)
    return VideoDownloadTask(
        id = id,
        biz = PlayBiz.from(biz),
        aid = aid,
        cid = cid,
        bvid = bvid,
        epId = epId,
        seasonId = seasonId,
        kind = resolvedKind,
        videoQuality = videoQuality,
        audioQuality = audioQuality,
        title = title,
        cover = cover,
        ownerUid = ownerUid,
        ownerName = ownerName,
        status = VideoDownloadTaskStatus.valueOf(status),
        progress = toProgress(),
        error = error,
        videoPath = videoPath,
        audioPath = audioPath,
        durationMs = durationMs,
        createdAtMs = createdAtMs
    )
}

fun VideoDownloadRequest.toEntity(
    createdAtMs: Long
): VideoDownloadEntity {
    return VideoDownloadEntity(
        title = resolveTitle(),
        cover = meta.cover?.takeIf(String::isNotBlank),
        ownerUid = meta.ownerUid?.takeIf { it > 0L },
        ownerName = meta.ownerName?.takeIf(String::isNotBlank),
        biz = biz.name,
        aid = aid,
        cid = cid,
        bvid = bvid?.takeIf(String::isNotBlank),
        epId = epId,
        seasonId = seasonId,
        kind = kind.name,
        videoQuality = videoQuality,
        audioQuality = audioQuality,
        status = VideoDownloadTaskStatus.WAITING.name,
        progressType = null,
        progressLabel = null,
        doneBytes = 0L,
        totalBytes = 0L,
        error = null,
        videoPath = null,
        audioPath = null,
        durationMs = 0L,
        createdAtMs = createdAtMs
    )
}

private fun VideoDownloadEntity.toProgress(): VideoDownloadProgress? {
    return when (progressType) {
        null -> null
        ProgressType.PREPARING -> VideoDownloadProgress.Preparing
        ProgressType.DOWNLOADING -> VideoDownloadProgress.Downloading(
            label = progressLabel ?: "",
            doneBytes = doneBytes,
            totalBytes = totalBytes
        )
        ProgressType.DONE -> VideoDownloadProgress.Done
        else -> error("未知下载进度类型: $progressType")
    }
}

private fun VideoDownloadRequest.resolveTitle(): String {
    val raw = meta.title?.trim()
    return if (!raw.isNullOrBlank()) raw else fallbackTitle()
}

object ProgressType {
    const val PREPARING = "PREPARING"
    const val DOWNLOADING = "DOWNLOADING"
    const val DONE = "DONE"
}
