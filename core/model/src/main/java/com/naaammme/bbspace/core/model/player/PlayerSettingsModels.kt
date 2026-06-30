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
        minBufferMs = 10_000,
        maxBufferMs = 30_000,
        playBufferMs = 750,
        rebufferMs = 1_500,
        backBufferMs = 5_000
    )
}

@Immutable
data class PlayerBufferSettings(
    val profile: PlayerBufferProfile = PlayerBufferProfile.FastStart
)

@Immutable
enum class VideoCdnMode(
    val label: String,
    val description: String
) {
    BaseUrl("基础 URL（不推荐）", "直接使用服务端原始 base_url"),
    Backup1("备用 URL 1", "直接使用服务端第一个备用线路"),
    Backup2("备用 URL 2", "直接使用服务端第二个备用线路"),
    Ali("Ali", "阿里云 (推荐)"),
    Alib("Alib", "阿里云"),
    Alio1("Alio1", "阿里云"),
    Cos("Cos", "腾讯云"),
    Cosb("Cosb", "腾讯云，VOD 加速类型"),
    Coso1("Coso1", "腾讯云"),
    Hw("Hw", "华为云，融合 CDN"),
    Hwb("Hwb", "华为云，融合 CDN"),
    Hwo1("Hwo1", "华为云，融合 CDN"),
    Hw08C("08c", "华为云，融合 CDN"),
    Hw08H("08h", "华为云，融合 CDN"),
    Hw08Ct("08ct", "华为云，融合 CDN"),
    TfHw("TfHw", "华为云 TF 线路"),
    TfTx("TfTx", "腾讯云 TF 线路"),
    Akamai("Akamai", "Akamai 海外"),
    Aliov("Aliov", "阿里云海外"),
    Cosov("Cosov", "腾讯云海外"),
    Hwov("Hwov", "华为云海外"),
    HkBcache("HkBcache", "Bilibili 海外");

    val host: String?
        get() = when (this) {
            BaseUrl,
            Backup1,
            Backup2 -> null
            Ali -> "upos-sz-mirrorali.bilivideo.com"
            Alib -> "upos-sz-mirroralib.bilivideo.com"
            Alio1 -> "upos-sz-mirroralio1.bilivideo.com"
            Cos -> "upos-sz-mirrorcos.bilivideo.com"
            Cosb -> "upos-sz-mirrorcosb.bilivideo.com"
            Coso1 -> "upos-sz-mirrorcoso1.bilivideo.com"
            Hw -> "upos-sz-mirrorhw.bilivideo.com"
            Hwb -> "upos-sz-mirrorhwb.bilivideo.com"
            Hwo1 -> "upos-sz-mirrorhwo1.bilivideo.com"
            Hw08C -> "upos-sz-mirror08c.bilivideo.com"
            Hw08H -> "upos-sz-mirror08h.bilivideo.com"
            Hw08Ct -> "upos-sz-mirror08ct.bilivideo.com"
            TfHw -> "upos-tf-all-hw.bilivideo.com"
            TfTx -> "upos-tf-all-tx.bilivideo.com"
            Akamai -> "upos-hz-mirrorakam.akamaized.net"
            Aliov -> "upos-sz-mirroraliov.bilivideo.com"
            Cosov -> "upos-sz-mirrorcosov.bilivideo.com"
            Hwov -> "upos-sz-mirrorhwov.bilivideo.com"
            HkBcache -> "cn-hk-eq-bcache-01.bilivideo.com"
        }
}

@Immutable
data class PlayerPlaybackPrefs(
    val backgroundPlayback: Boolean = true,
    val inAppMiniPlayer: Boolean = true,
    val reportPlayback: Boolean = true,
    val preferSoftwareDecode: Boolean = false,
    val decoderFallback: Boolean = true,
    val autoRotateFullscreen: Boolean = false,
    val gestureSpeed: Float = 2f,
    val videoCdnMode: VideoCdnMode = VideoCdnMode.Backup2
)

@Immutable
data class PlayerSettingsState(
    val buffer: PlayerBufferSettings = PlayerBufferSettings(),
    val playback: PlayerPlaybackPrefs = PlayerPlaybackPrefs(),
    val danmaku: DanmakuConfig = DanmakuConfig()
)
