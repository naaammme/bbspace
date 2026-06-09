package com.naaammme.bbspace.core.domain.live

import com.naaammme.bbspace.core.model.LiveRoomSessionState
import kotlinx.coroutines.flow.Flow

interface LiveRoomMessageRepository {
    fun observeRoomSession(roomId: Long): Flow<LiveRoomSessionState>
    suspend fun sendDanmaku(
        roomId: Long,
        content: String,
        jumpFrom: Int
    )
}
