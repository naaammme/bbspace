package com.naaammme.bbspace.core.domain.player

import com.naaammme.bbspace.core.model.DanmakuConfig
import com.naaammme.bbspace.core.model.PlayerBufferProfile
import com.naaammme.bbspace.core.model.PlayerSettingsState
import com.naaammme.bbspace.core.model.VideoCdnMode
import kotlinx.coroutines.flow.Flow

interface PlayerSettings {
    // 这里只暴露持久偏好的原始流，缓存和会话态交给上层自己处理
    val state: Flow<PlayerSettingsState>

    suspend fun setBufferProfile(profile: PlayerBufferProfile)
    suspend fun setBackgroundPlayback(enabled: Boolean)
    suspend fun setInAppMiniPlayer(enabled: Boolean)
    suspend fun setReportPlayback(enabled: Boolean)
    suspend fun setPreferSoftwareDecode(enabled: Boolean)
    suspend fun setDecoderFallback(enabled: Boolean)
    suspend fun setAutoRotateFullscreen(enabled: Boolean)
    suspend fun setGestureSpeed(speed: Float)
    suspend fun setVideoCdnMode(mode: VideoCdnMode)
    suspend fun setDanmaku(config: DanmakuConfig)
}
