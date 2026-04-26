package com.naaammme.bbspace.core.domain.download

import com.naaammme.bbspace.core.model.VideoDownloadRequest
import com.naaammme.bbspace.core.model.VideoDownloadEnqueueResult
import com.naaammme.bbspace.core.model.VideoDownloadTask
import com.naaammme.bbspace.core.model.DownloadDanmakuCache
import kotlinx.coroutines.flow.Flow

interface VideoDownloadRepository {
    val tasks: Flow<List<VideoDownloadTask>>

    suspend fun enqueue(request: VideoDownloadRequest): VideoDownloadEnqueueResult
    suspend fun pause(taskId: Long)
    suspend fun resume(taskId: Long)
    suspend fun delete(taskId: Long)
    fun task(taskId: Long): Flow<VideoDownloadTask?>
    suspend fun getTask(taskId: Long): VideoDownloadTask?
    suspend fun loadDanmaku(taskId: Long): DownloadDanmakuCache?
}
