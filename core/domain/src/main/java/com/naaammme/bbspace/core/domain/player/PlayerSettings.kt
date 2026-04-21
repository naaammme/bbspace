package com.naaammme.bbspace.core.domain.player

import com.naaammme.bbspace.core.model.DanmakuConfig
import com.naaammme.bbspace.core.model.PlayerBufferSettings
import com.naaammme.bbspace.core.model.PlayerPlaybackPrefs
import com.naaammme.bbspace.core.model.PlayerSettingsState
import kotlinx.coroutines.flow.StateFlow

interface PlayerSettings {
    val state: StateFlow<PlayerSettingsState>

    suspend fun updateBuffer(settings: PlayerBufferSettings)
    suspend fun updatePlayback(settings: PlayerPlaybackPrefs)
    suspend fun updateDanmaku(config: DanmakuConfig)
}
