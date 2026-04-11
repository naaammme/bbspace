package com.naaammme.bbspace.core.domain.video

import com.naaammme.bbspace.core.model.VideoDetail
import com.naaammme.bbspace.core.model.VideoJump

interface VideoDetailRepository {
    suspend fun fetchVideoDetail(jump: VideoJump): VideoDetail
}
