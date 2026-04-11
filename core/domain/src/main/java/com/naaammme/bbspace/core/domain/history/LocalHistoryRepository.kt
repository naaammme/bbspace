package com.naaammme.bbspace.core.domain.history

import com.naaammme.bbspace.core.model.VideoHistory
import kotlinx.coroutines.flow.Flow

interface LocalHistoryRepository {
    suspend fun upsertVideo(item: VideoHistory)

    suspend fun getVideo(
        uid: Long,
        key: String
    ): VideoHistory?

    fun observeVideos(): Flow<List<VideoHistory>>

    suspend fun deleteVideo(id: String)

    suspend fun clearVideos()
}
