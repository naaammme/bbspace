package com.naaammme.bbspace.core.domain.danmaku

import com.naaammme.bbspace.core.model.DanmakuRequest
import com.naaammme.bbspace.core.model.DanmakuSegment

interface DanmakuRepository {
    suspend fun fetchSegment(request: DanmakuRequest): DanmakuSegment
}
