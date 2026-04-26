package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

enum class DownloadPlaybackStatus {
    IDLE,
    BUFFERING,
    READY,
    ENDED
}

@Immutable
data class DownloadDanmakuCache(
    val aid: Long,
    val cid: Long,
    val items: List<DanmakuItem>
)

@Immutable
data class DownloadPlaybackState(
    val taskId: Long? = null,
    val title: String = "",
    val subtitle: String? = null,
    val isPreparing: Boolean = false,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val playbackStatus: DownloadPlaybackStatus = DownloadPlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val seekEventId: Long = 0L,
    val error: String? = null
)
