package com.naaammme.bbspace.core.domain.download

import com.naaammme.bbspace.core.model.VideoDownloadProgress
import com.naaammme.bbspace.core.model.VideoDownloadRequest
import kotlinx.coroutines.flow.Flow

interface VideoDownloadRepository {
    fun download(request: VideoDownloadRequest): Flow<VideoDownloadProgress>
}
