package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
enum class PlayerBufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val playBufferMs: Int,
    val rebufferMs: Int,
    val backBufferMs: Int
) {
    FastStart(
        minBufferMs = 2_000,
        maxBufferMs = 15_000,
        playBufferMs = 250,
        rebufferMs = 500,
        backBufferMs = 2_000
    ),
    Balanced(
        minBufferMs = 5_000,
        maxBufferMs = 20_000,
        playBufferMs = 500,
        rebufferMs = 1_000,
        backBufferMs = 5_000
    ),
    Stable(
        minBufferMs = 15_000,
        maxBufferMs = 50_000,
        playBufferMs = 500,
        rebufferMs = 1_000,
        backBufferMs = 5_000
    )
}

@Immutable
data class PlayerBufferSettings(
    val profile: PlayerBufferProfile = PlayerBufferProfile.FastStart
)

@Immutable
data class PlayerPlaybackPrefs(
    val backgroundPlayback: Boolean = false,
    val inAppMiniPlayer: Boolean = true,
    val reportPlayback: Boolean = true,
    val preferSoftwareDecode: Boolean = false,
    val decoderFallback: Boolean = true,
    val autoRotateFullscreen: Boolean = true,
    val gestureSpeed: Float = 2f
)

@Immutable
data class PlayerSettingsState(
    val buffer: PlayerBufferSettings = PlayerBufferSettings(),
    val playback: PlayerPlaybackPrefs = PlayerPlaybackPrefs(),
    val danmaku: DanmakuConfig = DanmakuConfig()
)
