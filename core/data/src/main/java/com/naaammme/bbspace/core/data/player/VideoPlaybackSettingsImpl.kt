package com.naaammme.bbspace.core.data.player

import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.domain.player.VideoPlaybackSettings
import com.naaammme.bbspace.core.model.VideoBufferSettings
import com.naaammme.bbspace.core.model.VideoDanmakuConfig
import com.naaammme.bbspace.core.model.VideoPlaybackPrefs
import com.naaammme.bbspace.core.model.VideoPlaybackSettingsState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Singleton
class VideoPlaybackSettingsImpl @Inject constructor(
    private val appSettings: AppSettings
) : VideoPlaybackSettings {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val state: StateFlow<VideoPlaybackSettingsState> = combine(
        combine(
            appSettings.playerMinBufferMs,
            appSettings.playerMaxBufferMs,
            appSettings.playerPlaybackBufferMs,
            appSettings.playerRebufferMs,
            appSettings.playerBackBufferMs
        ) { minBufferMs, maxBufferMs, playbackBufferMs, rebufferMs, backBufferMs ->
            VideoBufferSettings(
                minBufferMs = minBufferMs,
                maxBufferMs = maxBufferMs,
                playbackBufferMs = playbackBufferMs,
                rebufferMs = rebufferMs,
                backBufferMs = backBufferMs
            )
        },
        combine(
            appSettings.backgroundPlayback,
            appSettings.preferSoftwareDecode,
            appSettings.decoderFallback
        ) { backgroundPlayback, preferSoftwareDecode, decoderFallback ->
            VideoPlaybackPrefs(
                backgroundPlayback = backgroundPlayback,
                preferSoftwareDecode = preferSoftwareDecode,
                decoderFallback = decoderFallback
            )
        },
        combine(
            combine(
                appSettings.danmakuEnabled,
                appSettings.danmakuAreaPercent,
                appSettings.danmakuOpacity,
                appSettings.danmakuTextScale,
                appSettings.danmakuSpeed
            ) { enabled, areaPercent, opacity, textScale, speed ->
                DanmakuDisplay(
                    enabled = enabled,
                    areaPercent = areaPercent,
                    opacity = opacity,
                    textScale = textScale,
                    speed = speed
                )
            },
            combine(
                appSettings.danmakuDensity,
                appSettings.danmakuMergeDuplicates,
                appSettings.danmakuShowTop,
                appSettings.danmakuShowBottom,
                appSettings.danmakuShowScrollRl
            ) { densityLevel, mergeDuplicates, showTop, showBottom, showScrollRl ->
                DanmakuBehavior(
                    densityLevel = densityLevel,
                    mergeDuplicates = mergeDuplicates,
                    showTop = showTop,
                    showBottom = showBottom,
                    showScrollRl = showScrollRl
                )
            }
        ) { display, behavior ->
            VideoDanmakuConfig(
                enabled = display.enabled,
                areaPercent = display.areaPercent,
                opacity = display.opacity,
                textScale = display.textScale,
                speed = display.speed,
                densityLevel = behavior.densityLevel,
                mergeDuplicates = behavior.mergeDuplicates,
                showTop = behavior.showTop,
                showBottom = behavior.showBottom,
                showScrollRl = behavior.showScrollRl
            )
        }
    ) { buffer, playback, danmaku ->
        VideoPlaybackSettingsState(
            buffer = buffer,
            playback = playback,
            danmaku = danmaku
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = VideoPlaybackSettingsState()
    )

    override suspend fun updateBuffer(settings: VideoBufferSettings) {
        appSettings.updatePlayerMinBufferMs(settings.minBufferMs)
        appSettings.updatePlayerMaxBufferMs(settings.maxBufferMs)
        appSettings.updatePlayerPlaybackBufferMs(settings.playbackBufferMs)
        appSettings.updatePlayerRebufferMs(settings.rebufferMs)
        appSettings.updatePlayerBackBufferMs(settings.backBufferMs)
    }

    override suspend fun updatePlayback(settings: VideoPlaybackPrefs) {
        appSettings.updateBackgroundPlayback(settings.backgroundPlayback)
        appSettings.updatePreferSoftwareDecode(settings.preferSoftwareDecode)
        appSettings.updateDecoderFallback(settings.decoderFallback)
    }

    override suspend fun updateDanmaku(config: VideoDanmakuConfig) {
        appSettings.updateDanmakuEnabled(config.enabled)
        appSettings.updateDanmakuAreaPercent(config.areaPercent)
        appSettings.updateDanmakuOpacity(config.opacity)
        appSettings.updateDanmakuTextScale(config.textScale)
        appSettings.updateDanmakuSpeed(config.speed)
        appSettings.updateDanmakuDensity(config.densityLevel)
        appSettings.updateDanmakuMergeDuplicates(config.mergeDuplicates)
        appSettings.updateDanmakuShowTop(config.showTop)
        appSettings.updateDanmakuShowBottom(config.showBottom)
        appSettings.updateDanmakuShowScrollRl(config.showScrollRl)
    }
}

private data class DanmakuDisplay(
    val enabled: Boolean,
    val areaPercent: Int,
    val opacity: Float,
    val textScale: Float,
    val speed: Float
)

private data class DanmakuBehavior(
    val densityLevel: Int,
    val mergeDuplicates: Boolean,
    val showTop: Boolean,
    val showBottom: Boolean,
    val showScrollRl: Boolean
)
