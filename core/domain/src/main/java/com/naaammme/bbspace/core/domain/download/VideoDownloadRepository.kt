package com.naaammme.bbspace.core.domain.download

import com.naaammme.bbspace.core.model.VideoDownloadRequest
import com.naaammme.bbspace.core.model.VideoDownloadTask
import kotlinx.coroutines.flow.StateFlow

interface VideoDownloadRepository {
    val tasks: StateFlow<List<VideoDownloadTask>>

    fun enqueue(request: VideoDownloadRequest): Long

    suspend fun runPending()
}
