package com.naaammme.bbspace.core.data.player

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.naaammme.bbspace.core.data.appSettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PlayerSettingsStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val playerMinBufMsKey = intPreferencesKey("player_min_buf_ms")
    private val playerMaxBufMsKey = intPreferencesKey("player_max_buf_ms")
    private val playerPlayBufMsKey = intPreferencesKey("player_play_buf_ms")
    private val playerRebufMsKey = intPreferencesKey("player_rebuf_ms")
    private val playerBackBufMsKey = intPreferencesKey("player_back_buf_ms")
    private val playerCdnIdxKey = intPreferencesKey("player_cdn_idx")
    private val preferSoftDecKey = booleanPreferencesKey("prefer_soft_dec")
    private val decFallbackKey = booleanPreferencesKey("dec_fallback")
    private val bgPlayKey = booleanPreferencesKey("bg_play")
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

    val playerMinBufferMs: Flow<Int> = context.appSettingsDataStore.data.map { it[playerMinBufMsKey] ?: 2_000 }
    val playerMaxBufferMs: Flow<Int> = context.appSettingsDataStore.data.map { it[playerMaxBufMsKey] ?: 15_000 }
    val playerPlaybackBufferMs: Flow<Int> = context.appSettingsDataStore.data.map { it[playerPlayBufMsKey] ?: 250 }
    val playerRebufferMs: Flow<Int> = context.appSettingsDataStore.data.map { it[playerRebufMsKey] ?: 500 }
    val playerBackBufferMs: Flow<Int> = context.appSettingsDataStore.data.map { it[playerBackBufMsKey] ?: 5_000 }
    val playerCdnIndex: Flow<Int> = context.appSettingsDataStore.data.map { it[playerCdnIdxKey] ?: 0 }
    val preferSoftwareDecode: Flow<Boolean> = context.appSettingsDataStore.data.map { it[preferSoftDecKey] ?: false }
    val decoderFallback: Flow<Boolean> = context.appSettingsDataStore.data.map { it[decFallbackKey] ?: true }
    val backgroundPlayback: Flow<Boolean> = context.appSettingsDataStore.data.map { it[bgPlayKey] ?: false }
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

    suspend fun updatePlayerMinBufferMs(value: Int) {
        context.appSettingsDataStore.edit { it[playerMinBufMsKey] = value }
    }

    suspend fun updatePlayerMaxBufferMs(value: Int) {
        context.appSettingsDataStore.edit { it[playerMaxBufMsKey] = value }
    }

    suspend fun updatePlayerPlaybackBufferMs(value: Int) {
        context.appSettingsDataStore.edit { it[playerPlayBufMsKey] = value }
    }

    suspend fun updatePlayerRebufferMs(value: Int) {
        context.appSettingsDataStore.edit { it[playerRebufMsKey] = value }
    }

    suspend fun updatePlayerBackBufferMs(value: Int) {
        context.appSettingsDataStore.edit { it[playerBackBufMsKey] = value }
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
}
