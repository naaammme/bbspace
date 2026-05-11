package com.naaammme.bbspace.core.data.player

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.naaammme.bbspace.core.data.appSettingsDataStore
import com.naaammme.bbspace.core.model.DanmakuConfig
import com.naaammme.bbspace.core.model.PlayerBufferSettings
import com.naaammme.bbspace.core.model.PlayerBufferProfile
import com.naaammme.bbspace.core.model.PlayerPlaybackPrefs
import com.naaammme.bbspace.core.model.PlayerSettingsState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PlayerSettingsStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val playerBufferProfileKey = intPreferencesKey("player_buffer_profile")
    private val playerCdnIdxKey = intPreferencesKey("player_cdn_idx")
    private val preferSoftDecKey = booleanPreferencesKey("prefer_soft_dec")
    private val decFallbackKey = booleanPreferencesKey("dec_fallback")
    private val bgPlayKey = booleanPreferencesKey("bg_play")
    private val inAppMiniPlayerKey = booleanPreferencesKey("in_app_mini_player")
    private val autoRotateFullscreenKey = booleanPreferencesKey("auto_rotate_fullscreen")
    private val gestureSpeedKey = floatPreferencesKey("gesture_speed")
    private val reportPlaybackKey = booleanPreferencesKey("report_playback")
    private val danmakuEnabledKey = booleanPreferencesKey("danmaku_enabled")
    private val danmakuAreaPercentKey = intPreferencesKey("danmaku_area_percent")
    private val danmakuOpacityKey = floatPreferencesKey("danmaku_opacity")
    private val danmakuTextScaleKey = floatPreferencesKey("danmaku_text_scale")
    private val danmakuSpeedKey = floatPreferencesKey("danmaku_speed")
    private val danmakuDensityKey = intPreferencesKey("danmaku_density")
    private val danmakuMergeDuplicatesKey = booleanPreferencesKey("danmaku_merge_duplicates")
    private val danmakuShowTopKey = booleanPreferencesKey("danmaku_show_top")
    private val danmakuShowBottomKey = booleanPreferencesKey("danmaku_show_bottom")
    private val danmakuShowScrollRlKey = booleanPreferencesKey("danmaku_show_scroll_rl")

    val state: Flow<PlayerSettingsState> = context.appSettingsDataStore.data.map { prefs ->
        PlayerSettingsState(
            buffer = PlayerBufferSettings(
                profile = prefs[playerBufferProfileKey].toPlayerBufferProfile()
            ),
            playback = PlayerPlaybackPrefs(
                backgroundPlayback = prefs[bgPlayKey] ?: false,
                inAppMiniPlayer = prefs[inAppMiniPlayerKey] ?: true,
                reportPlayback = prefs[reportPlaybackKey] ?: true,
                preferSoftwareDecode = prefs[preferSoftDecKey] ?: false,
                decoderFallback = prefs[decFallbackKey] ?: true,
                autoRotateFullscreen = prefs[autoRotateFullscreenKey] ?: true,
                gestureSpeed = (prefs[gestureSpeedKey] ?: 2f).coerceIn(0.25f, 3f)
            ),
            danmaku = DanmakuConfig(
                enabled = prefs[danmakuEnabledKey] ?: true,
                areaPercent = prefs[danmakuAreaPercentKey] ?: 100,
                opacity = prefs[danmakuOpacityKey] ?: 1f,
                textScale = prefs[danmakuTextScaleKey] ?: 1f,
                speed = prefs[danmakuSpeedKey] ?: 1f,
                densityLevel = prefs[danmakuDensityKey] ?: 1,
                mergeDuplicates = prefs[danmakuMergeDuplicatesKey] ?: false,
                showTop = prefs[danmakuShowTopKey] ?: true,
                showBottom = prefs[danmakuShowBottomKey] ?: true,
                showScrollRl = prefs[danmakuShowScrollRlKey] ?: true
            )
        )
    }

    val playerBufferProfile: Flow<PlayerBufferProfile> =
        context.appSettingsDataStore.data.map { it[playerBufferProfileKey].toPlayerBufferProfile() }
    val playerCdnIndex: Flow<Int> = context.appSettingsDataStore.data.map { it[playerCdnIdxKey] ?: 0 }
    val preferSoftwareDecode: Flow<Boolean> = context.appSettingsDataStore.data.map { it[preferSoftDecKey] ?: false }
    val decoderFallback: Flow<Boolean> = context.appSettingsDataStore.data.map { it[decFallbackKey] ?: true }
    val backgroundPlayback: Flow<Boolean> = context.appSettingsDataStore.data.map { it[bgPlayKey] ?: false }
    val inAppMiniPlayer: Flow<Boolean> = context.appSettingsDataStore.data.map { it[inAppMiniPlayerKey] ?: true }
    val autoRotateFullscreen: Flow<Boolean> = context.appSettingsDataStore.data.map { it[autoRotateFullscreenKey] ?: true }
    val gestureSpeed: Flow<Float> =
        context.appSettingsDataStore.data.map { (it[gestureSpeedKey] ?: 2f).coerceIn(0.25f, 3f) }
    val reportPlayback: Flow<Boolean> = context.appSettingsDataStore.data.map { it[reportPlaybackKey] ?: true }
    val danmakuEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { it[danmakuEnabledKey] ?: true }
    val danmakuAreaPercent: Flow<Int> = context.appSettingsDataStore.data.map { it[danmakuAreaPercentKey] ?: 100 }
    val danmakuOpacity: Flow<Float> = context.appSettingsDataStore.data.map { it[danmakuOpacityKey] ?: 1f }
    val danmakuTextScale: Flow<Float> = context.appSettingsDataStore.data.map { it[danmakuTextScaleKey] ?: 1f }
    val danmakuSpeed: Flow<Float> = context.appSettingsDataStore.data.map { it[danmakuSpeedKey] ?: 1f }
    val danmakuDensity: Flow<Int> = context.appSettingsDataStore.data.map { it[danmakuDensityKey] ?: 1 }
    val danmakuMergeDuplicates: Flow<Boolean> =
        context.appSettingsDataStore.data.map { it[danmakuMergeDuplicatesKey] ?: false }
    val danmakuShowTop: Flow<Boolean> = context.appSettingsDataStore.data.map { it[danmakuShowTopKey] ?: true }
    val danmakuShowBottom: Flow<Boolean> =
        context.appSettingsDataStore.data.map { it[danmakuShowBottomKey] ?: true }
    val danmakuShowScrollRl: Flow<Boolean> =
        context.appSettingsDataStore.data.map { it[danmakuShowScrollRlKey] ?: true }

    suspend fun updatePlayerBufferProfile(profile: PlayerBufferProfile) {
        context.appSettingsDataStore.edit { it[playerBufferProfileKey] = profile.ordinal }
    }

    suspend fun updatePlayerCdnIndex(value: Int) {
        context.appSettingsDataStore.edit { it[playerCdnIdxKey] = value }
    }

    suspend fun updatePreferSoftwareDecode(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[preferSoftDecKey] = enabled }
    }

    suspend fun updateDecoderFallback(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[decFallbackKey] = enabled }
    }

    suspend fun updateBackgroundPlayback(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[bgPlayKey] = enabled }
    }

    suspend fun updateInAppMiniPlayer(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[inAppMiniPlayerKey] = enabled }
    }

    suspend fun updateAutoRotateFullscreen(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[autoRotateFullscreenKey] = enabled }
    }

    suspend fun updateGestureSpeed(value: Float) {
        context.appSettingsDataStore.edit { it[gestureSpeedKey] = value.coerceIn(0.25f, 3f) }
    }

    suspend fun updateReportPlayback(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[reportPlaybackKey] = enabled }
    }

    suspend fun updateDanmakuEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[danmakuEnabledKey] = enabled }
    }

    suspend fun updateDanmakuAreaPercent(percent: Int) {
        context.appSettingsDataStore.edit { it[danmakuAreaPercentKey] = percent.coerceIn(25, 100) }
    }

    suspend fun updateDanmakuOpacity(value: Float) {
        context.appSettingsDataStore.edit { it[danmakuOpacityKey] = value.coerceIn(0.1f, 1f) }
    }

    suspend fun updateDanmakuTextScale(value: Float) {
        context.appSettingsDataStore.edit { it[danmakuTextScaleKey] = value.coerceIn(0.5f, 2f) }
    }

    suspend fun updateDanmakuSpeed(value: Float) {
        context.appSettingsDataStore.edit { it[danmakuSpeedKey] = value.coerceIn(0.5f, 2f) }
    }

    suspend fun updateDanmakuDensity(level: Int) {
        context.appSettingsDataStore.edit { it[danmakuDensityKey] = level.coerceIn(0, 2) }
    }

    suspend fun updateDanmakuMergeDuplicates(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[danmakuMergeDuplicatesKey] = enabled }
    }

    suspend fun updateDanmakuShowTop(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[danmakuShowTopKey] = enabled }
    }

    suspend fun updateDanmakuShowBottom(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[danmakuShowBottomKey] = enabled }
    }

    suspend fun updateDanmakuShowScrollRl(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[danmakuShowScrollRlKey] = enabled }
    }

    private fun Int?.toPlayerBufferProfile(): PlayerBufferProfile {
        return PlayerBufferProfile.entries.getOrElse(this ?: PlayerBufferProfile.FastStart.ordinal) {
            PlayerBufferProfile.FastStart
        }
    }
}
