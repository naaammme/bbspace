package com.naaammme.bbspace.feature.download.model

import com.naaammme.bbspace.core.model.VideoDownloadKind
import com.naaammme.bbspace.core.model.VideoDownloadTask

data class DownloadUiState(
    val tab: DownloadTab = DownloadTab.CONFIG,
    val loading: Boolean = false,
    val input: String = "",
    val kind: VideoDownloadKind = VideoDownloadKind.VIDEO,
    val videoQuality: Int = 80,
    val audioQuality: Int = 0,
    val hasTask: Boolean = false,
    val pendingTitle: String? = null,
    val tasks: List<VideoDownloadTask> = emptyList(),
    val error: String? = null
)

enum class DownloadTab(val title: String) {
    CONFIG("配置"),
    QUEUE("队列")
}
