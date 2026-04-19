package com.naaammme.bbspace.core.domain.live

import com.naaammme.bbspace.core.model.LivePlaybackSource

interface LiveRepository {
    suspend fun fetchPlaybackSource(
        roomId: Long,
        qn: Int = 0
    ): LivePlaybackSource
}
