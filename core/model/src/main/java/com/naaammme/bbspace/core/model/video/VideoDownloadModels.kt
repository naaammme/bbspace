package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class VideoDownloadRequest(
    val biz: PlayBiz = PlayBiz.UGC,
    val aid: Long = 0L,
    val cid: Long = 0L,
    val bvid: String? = null,
    val epId: Long = 0L,
    val seasonId: Long = 0L,
    val kind: VideoDownloadKind,
    val videoQuality: Int,
    val audioQuality: Int,
    val meta: VideoDownloadMeta = VideoDownloadMeta()
)

@Immutable
data class VideoDownloadMeta(
    val title: String? = null,
    val cover: String? = null,
    val ownerUid: Long? = null,
    val ownerName: String? = null
)

fun VideoDownloadRequest.fallbackTitle(): String {
    return when {
        epId > 0L -> "ep$epId"
        !bvid.isNullOrBlank() -> bvid
        aid > 0L -> "av$aid"
        seasonId > 0L -> "ss$seasonId"
        else -> "下载任务"
    }
}

enum class VideoDownloadKind {
    VIDEO,
    AUDIO;

    companion object {
        fun from(raw: String?): VideoDownloadKind {
            return values().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: VIDEO
        }
    }
}

@Immutable
data class VideoDownloadOption(
    val value: Int,
    val label: String
)

object VideoDownloadOptions {
    val videoQualities = listOf(
        VideoDownloadOption(16, "360P"),
        VideoDownloadOption(32, "480P"),
        VideoDownloadOption(64, "720P"),
        VideoDownloadOption(80, "1080P"),
        VideoDownloadOption(112, "1080P+"),
        VideoDownloadOption(116, "1080P 60"),
        VideoDownloadOption(120, "4K"),
        VideoDownloadOption(125, "HDR"),
        VideoDownloadOption(126, "杜比视界"),
        VideoDownloadOption(127, "8K")
    )

    val audioQualities = listOf(
        VideoDownloadOption(0, "自动"),
        VideoDownloadOption(30216, "64K"),
        VideoDownloadOption(30232, "132K"),
        VideoDownloadOption(30280, "192K"),
        VideoDownloadOption(30250, "杜比全景声"),
        VideoDownloadOption(30251, "Hi-Res")
    )

    fun videoLabel(quality: Int): String {
        return videoQualities.firstOrNull { it.value == quality }?.label ?: "画质 $quality"
    }

    fun audioLabel(quality: Int): String {
        return audioQualities.firstOrNull { it.value == quality }?.label ?: "音频 $quality"
    }
}

@Immutable
data class VideoDownloadTask(
    val id: Long,
    val biz: PlayBiz,
    val aid: Long,
    val cid: Long,
    val bvid: String? = null,
    val epId: Long = 0L,
    val seasonId: Long = 0L,
    val kind: VideoDownloadKind,
    val videoQuality: Int,
    val audioQuality: Int,
    val title: String,
    val cover: String? = null,
    val ownerUid: Long? = null,
    val ownerName: String? = null,
    val status: VideoDownloadTaskStatus = VideoDownloadTaskStatus.WAITING,
    val progress: VideoDownloadProgress? = null,
    val error: String? = null,
    val videoPath: String? = null,
    val audioPath: String? = null,
    val durationMs: Long = 0L,
    val createdAtMs: Long = 0L
) {
    val isPlayable: Boolean
        get() = status == VideoDownloadTaskStatus.DONE &&
                (!videoPath.isNullOrBlank() || !audioPath.isNullOrBlank())
}

enum class VideoDownloadTaskStatus {
    WAITING,
    RUNNING,
    PAUSED,
    DONE,
    FAILED
}

sealed interface VideoDownloadEnqueueResult {
    @Immutable
    data class Enqueued(
        val taskId: Long
    ) : VideoDownloadEnqueueResult

    @Immutable
    data class AlreadyExists(
        val taskId: Long
    ) : VideoDownloadEnqueueResult
}

sealed interface VideoDownloadProgress {
    data object Preparing : VideoDownloadProgress
    data class Downloading(
        val label: String,
        val doneBytes: Long,
        val totalBytes: Long
    ) : VideoDownloadProgress
    data object Done : VideoDownloadProgress
}

fun VideoDownloadRequest.summaryLabel(): String {
    return summaryLabel(kind = kind, videoQuality = videoQuality, audioQuality = audioQuality)
}

fun VideoDownloadTask.summaryLabel(): String {
    return summaryLabel(kind = kind, videoQuality = videoQuality, audioQuality = audioQuality)
}

private fun summaryLabel(
    kind: VideoDownloadKind,
    videoQuality: Int,
    audioQuality: Int
): String {
    val kindLabel = if (kind == VideoDownloadKind.VIDEO) "视频" else "音频"
    val video = if (kind == VideoDownloadKind.VIDEO) {
        VideoDownloadOptions.videoLabel(videoQuality)
    } else {
        null
    }
    val audio = VideoDownloadOptions.audioLabel(audioQuality)
    return listOfNotNull(kindLabel, video, audio).joinToString(" · ")
}
