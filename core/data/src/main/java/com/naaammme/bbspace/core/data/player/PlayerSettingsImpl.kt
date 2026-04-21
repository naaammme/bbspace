package com.naaammme.bbspace.core.data.player

import com.naaammme.bbspace.core.domain.player.PlayerSettings
import com.naaammme.bbspace.core.model.DanmakuConfig
import com.naaammme.bbspace.core.model.PlayerBufferSettings
import com.naaammme.bbspace.core.model.PlayerPlaybackPrefs
import com.naaammme.bbspace.core.model.PlayerSettingsState
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
class PlayerSettingsImpl @Inject constructor(
    private val store: PlayerSettingsStore
) : PlayerSettings {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val state: StateFlow<PlayerSettingsState> = combine(
        combine(
            store.playerMinBufferMs,
            store.playerMaxBufferMs,
            store.playerPlaybackBufferMs,
            store.playerRebufferMs,
            store.playerBackBufferMs
        ) { minBufferMs, maxBufferMs, playbackBufferMs, rebufferMs, backBufferMs ->
            PlayerBufferSettings(
                minBufferMs = minBufferMs,
                maxBufferMs = maxBufferMs,
                playbackBufferMs = playbackBufferMs,
                rebufferMs = rebufferMs,
                backBufferMs = backBufferMs
            )
        },
        combine(
            store.backgroundPlayback,
            store.reportPlayback,
            store.preferSoftwareDecode,
            store.decoderFallback
        ) { backgroundPlayback, reportPlayback, preferSoftwareDecode, decoderFallback ->
            PlayerPlaybackPrefs(
                backgroundPlayback = backgroundPlayback,
                reportPlayback = reportPlayback,
                preferSoftwareDecode = preferSoftwareDecode,
                decoderFallback = decoderFallback
            )
        },
        combine(
            combine(
                store.danmakuEnabled,
                store.danmakuAreaPercent,
                store.danmakuOpacity,
                store.danmakuTextScale,
                store.danmakuSpeed
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
                store.danmakuDensity,
                store.danmakuMergeDuplicates,
                store.danmakuShowTop,
                store.danmakuShowBottom,
                store.danmakuShowScrollRl
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
            DanmakuConfig(
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
        PlayerSettingsState(
            buffer = buffer,
            playback = playback,
            danmaku = danmaku
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = PlayerSettingsState()
    )

    override suspend fun updateBuffer(settings: PlayerBufferSettings) {
        store.updatePlayerMinBufferMs(settings.minBufferMs)
        store.updatePlayerMaxBufferMs(settings.maxBufferMs)
        store.updatePlayerPlaybackBufferMs(settings.playbackBufferMs)
        store.updatePlayerRebufferMs(settings.rebufferMs)
        store.updatePlayerBackBufferMs(settings.backBufferMs)
    }

    override suspend fun updatePlayback(settings: PlayerPlaybackPrefs) {
        store.updateBackgroundPlayback(settings.backgroundPlayback)
        store.updateReportPlayback(settings.reportPlayback)
        store.updatePreferSoftwareDecode(settings.preferSoftwareDecode)
        store.updateDecoderFallback(settings.decoderFallback)
    }

    override suspend fun updateDanmaku(config: DanmakuConfig) {
        store.updateDanmakuEnabled(config.enabled)
        store.updateDanmakuAreaPercent(config.areaPercent)
        store.updateDanmakuOpacity(config.opacity)
        store.updateDanmakuTextScale(config.textScale)
        store.updateDanmakuSpeed(config.speed)
        store.updateDanmakuDensity(config.densityLevel)
        store.updateDanmakuMergeDuplicates(config.mergeDuplicates)
        store.updateDanmakuShowTop(config.showTop)
        store.updateDanmakuShowBottom(config.showBottom)
        store.updateDanmakuShowScrollRl(config.showScrollRl)
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
