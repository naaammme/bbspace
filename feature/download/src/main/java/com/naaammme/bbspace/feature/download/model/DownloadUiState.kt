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
    val export: DownloadExportState = DownloadExportState(),
    val error: String? = null
)

data class DownloadExportState(
    val taskId: Long? = null,
    val progress: Int? = null,
    val message: String? = null,
    val isError: Boolean = false
)

enum class DownloadTab(val title: String) {
    CONFIG("配置"),
    QUEUE("队列")
}
