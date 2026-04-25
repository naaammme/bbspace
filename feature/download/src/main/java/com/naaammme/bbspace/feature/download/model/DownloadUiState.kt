package com.naaammme.bbspace.feature.download.model

import com.naaammme.bbspace.core.model.VideoDownloadKind
import com.naaammme.bbspace.core.model.VideoDownloadProgress

data class DownloadUiState(
    val loading: Boolean = false,
    val input: String = "",
    val kind: VideoDownloadKind = VideoDownloadKind.VIDEO,
    val videoQuality: Int = 80,
    val audioQuality: Int = 0,
    val hasTask: Boolean = false,
    val progress: VideoDownloadProgress? = null,
    val error: String? = null
) {
    val downloading: Boolean
        get() = progress != null && progress !is VideoDownloadProgress.Done
}
