package com.naaammme.bbspace.feature.video.model

import com.naaammme.bbspace.core.model.VideoDetail

data class VideoPageState(
    val detail: VideoDetail? = null,
    val detailLoading: Boolean = true,
    val detailError: String? = null,
    val curCid: Long? = null
)
