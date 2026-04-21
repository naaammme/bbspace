package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class PlayerBufferSettings(
    val minBufferMs: Int = 2_000,
    val maxBufferMs: Int = 15_000,
    val playbackBufferMs: Int = 250,
    val rebufferMs: Int = 500,
    val backBufferMs: Int = 5_000
)

@Immutable
data class PlayerPlaybackPrefs(
    val backgroundPlayback: Boolean = false,
    val reportPlayback: Boolean = true,
    val preferSoftwareDecode: Boolean = false,
    val decoderFallback: Boolean = true
)

@Immutable
data class PlayerSettingsState(
    val buffer: PlayerBufferSettings = PlayerBufferSettings(),
    val playback: PlayerPlaybackPrefs = PlayerPlaybackPrefs(),
    val danmaku: DanmakuConfig = DanmakuConfig()
)
