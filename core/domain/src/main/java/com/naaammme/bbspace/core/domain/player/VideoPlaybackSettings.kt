package com.naaammme.bbspace.core.domain.player

import com.naaammme.bbspace.core.model.VideoBufferSettings
import com.naaammme.bbspace.core.model.VideoDanmakuConfig
import com.naaammme.bbspace.core.model.VideoPlaybackPrefs
import com.naaammme.bbspace.core.model.VideoPlaybackSettingsState
import kotlinx.coroutines.flow.StateFlow

interface VideoPlaybackSettings {
    val state: StateFlow<VideoPlaybackSettingsState>

    suspend fun updateBuffer(settings: VideoBufferSettings)
    suspend fun updatePlayback(settings: VideoPlaybackPrefs)
    suspend fun updateDanmaku(config: VideoDanmakuConfig)
}
