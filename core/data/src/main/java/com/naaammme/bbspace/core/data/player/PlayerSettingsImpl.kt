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
import kotlinx.coroutines.flow.stateIn

@Singleton
class PlayerSettingsImpl @Inject constructor(
    private val store: PlayerSettingsStore
) : PlayerSettings {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val state: StateFlow<PlayerSettingsState> = store.state.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = PlayerSettingsState()
    )

    override suspend fun updateBuffer(settings: PlayerBufferSettings) {
        store.updatePlayerBufferProfile(settings.profile)
    }

    override suspend fun updatePlayback(settings: PlayerPlaybackPrefs) {
        store.updateBackgroundPlayback(settings.backgroundPlayback)
        store.updateInAppMiniPlayer(settings.inAppMiniPlayer)
        store.updateReportPlayback(settings.reportPlayback)
        store.updatePreferSoftwareDecode(settings.preferSoftwareDecode)
        store.updateDecoderFallback(settings.decoderFallback)
        store.updateAutoRotateFullscreen(settings.autoRotateFullscreen)
        store.updateGestureSpeed(settings.gestureSpeed)
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
