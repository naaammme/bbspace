package com.naaammme.bbspace.core.domain.video

import com.naaammme.bbspace.core.model.VideoDetail
import com.naaammme.bbspace.core.model.VideoSrc

interface VideoDetailRepository {
    suspend fun fetchVideoDetail(
        aid: Long,
        bvid: String?,
        src: VideoSrc
    ): VideoDetail
}
