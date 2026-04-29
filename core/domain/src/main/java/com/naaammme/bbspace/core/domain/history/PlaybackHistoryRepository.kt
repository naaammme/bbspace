package com.naaammme.bbspace.core.domain.history

import com.naaammme.bbspace.core.model.PlaybackHistory
import kotlinx.coroutines.flow.Flow

interface PlaybackHistoryRepository {
    suspend fun upsertVideo(item: PlaybackHistory)

    suspend fun getVideo(
        uid: Long,
        key: String
    ): PlaybackHistory?

    fun observeVideoCount(): Flow<Int>

    fun observeVideos(): Flow<List<PlaybackHistory>>

    suspend fun deleteVideo(id: String)

    suspend fun clearVideos()
}
