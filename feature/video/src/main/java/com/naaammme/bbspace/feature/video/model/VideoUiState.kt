package com.naaammme.bbspace.feature.video.model

import com.naaammme.bbspace.core.model.PlaybackAudio
import com.naaammme.bbspace.core.model.PlaybackError
import com.naaammme.bbspace.core.model.PlaybackSource
import com.naaammme.bbspace.core.model.PlaybackStream
import com.naaammme.bbspace.core.model.VideoDetail
import com.naaammme.bbspace.infra.player.PlaybackSnapshot

data class VideoPageState(
    val detail: VideoDetail? = null,
    val detailLoading: Boolean = true,
    val detailError: String? = null,
    val curCid: Long? = null
)

data class VideoPlayerState(
    val isLoading: Boolean = true,
    val playbackSource: PlaybackSource? = null,
    val currentStream: PlaybackStream? = null,
    val currentAudio: PlaybackAudio? = null,
    val cdnIndex: Int = 0,
    val snapshot: PlaybackSnapshot = PlaybackSnapshot(),
    val error: PlaybackError? = null
)

data class VideoDanmakuConfig(
    val enabled: Boolean = true,
    val areaPercent: Int = 100,
    val opacity: Float = 1f,
    val textScale: Float = 1f,
    val speed: Float = 1f,
    val densityLevel: Int = 1,
    val mergeDuplicates: Boolean = false,
    val showTop: Boolean = true,
    val showBottom: Boolean = true,
    val showScrollRl: Boolean = true
)

data class VideoPlayerMenuState(
    val minBufferMs: Int = 2_000,
    val maxBufferMs: Int = 15_000,
    val playbackBufferMs: Int = 250,
    val rebufferMs: Int = 500,
    val backBufferMs: Int = 5_000,
    val backgroundPlayback: Boolean = false,
    val preferSoftwareDecode: Boolean = false,
    val decoderFallback: Boolean = true,
    val danmaku: VideoDanmakuConfig = VideoDanmakuConfig()
)
