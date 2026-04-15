package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class VideoBufferSettings(
    val minBufferMs: Int = 2_000,
    val maxBufferMs: Int = 15_000,
    val playbackBufferMs: Int = 250,
    val rebufferMs: Int = 500,
    val backBufferMs: Int = 5_000
)

@Immutable
data class VideoPlaybackPrefs(
    val backgroundPlayback: Boolean = false,
    val preferSoftwareDecode: Boolean = false,
    val decoderFallback: Boolean = true
)

@Immutable
data class VideoPlaybackSettingsState(
    val buffer: VideoBufferSettings = VideoBufferSettings(),
    val playback: VideoPlaybackPrefs = VideoPlaybackPrefs(),
    val danmaku: VideoDanmakuConfig = VideoDanmakuConfig()
)
