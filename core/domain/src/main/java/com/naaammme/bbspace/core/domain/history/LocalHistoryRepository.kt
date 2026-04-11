package com.naaammme.bbspace.core.domain.history

import com.naaammme.bbspace.core.model.VideoHistory

interface LocalHistoryRepository {
    suspend fun upsertVideo(item: VideoHistory)

    suspend fun getVideo(
        uid: Long,
        key: String
    ): VideoHistory?
}
