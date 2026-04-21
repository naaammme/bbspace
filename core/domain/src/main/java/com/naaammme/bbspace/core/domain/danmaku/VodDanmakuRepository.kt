package com.naaammme.bbspace.core.domain.danmaku

import com.naaammme.bbspace.core.model.VodDanmakuRequest
import com.naaammme.bbspace.core.model.VodDanmakuSegment

interface VodDanmakuRepository {
    suspend fun fetchSegment(request: VodDanmakuRequest): VodDanmakuSegment
}
