package com.naaammme.bbspace.core.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.naaammme.bbspace.core.designsystem.theme.AnimationSpeed
import com.naaammme.bbspace.core.designsystem.theme.CornerStyle
import com.naaammme.bbspace.core.designsystem.theme.FrameRateMode
import com.naaammme.bbspace.core.designsystem.theme.ThemeConfig
import com.naaammme.bbspace.core.designsystem.theme.ThemeMode
import com.naaammme.bbspace.core.designsystem.theme.TransitionStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettingsStore by preferencesDataStore("app_settings")

@Singleton
class AppSettings @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val seedColorKey = intPreferencesKey("seed_color")
    private val useDynamicColorKey = booleanPreferencesKey("use_dynamic_color")
    private val fontScaleKey = floatPreferencesKey("font_scale")
    private val animationSpeedKey = stringPreferencesKey("animation_speed")
    private val transitionStyleKey = stringPreferencesKey("transition_style")
    private val isPureBlackKey = booleanPreferencesKey("is_pure_black")
    private val frameRateModeKey = stringPreferencesKey("frame_rate_mode")
    private val cornerStyleKey = stringPreferencesKey("corner_style")
    private val hdFeedKey = booleanPreferencesKey("hd_feed")
    private val personalizedRcmdKey = booleanPreferencesKey("personalized_rcmd")

    val themeConfig: Flow<ThemeConfig> = context.appSettingsStore.data.map { prefs ->
        ThemeConfig(
            themeMode = prefs[themeModeKey]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM,
            seedColor = Color(prefs[seedColorKey] ?: 0xFFFB7299.toInt()),
            useDynamicColor = prefs[useDynamicColorKey] ?: true,
            fontScale = prefs[fontScaleKey] ?: 1.0f,
            animationSpeed = prefs[animationSpeedKey]?.let { AnimationSpeed.valueOf(it) } ?: AnimationSpeed.NORMAL,
            transitionStyle = prefs[transitionStyleKey]?.let { TransitionStyle.valueOf(it) } ?: TransitionStyle.SHARED_AXIS_X,
            isPureBlack = prefs[isPureBlackKey] ?: false,
            preferredFrameRate = prefs[frameRateModeKey]?.let { FrameRateMode.valueOf(it) } ?: FrameRateMode.AUTO,
            cornerStyle = prefs[cornerStyleKey]?.let { CornerStyle.valueOf(it) } ?: CornerStyle.STANDARD
        )
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.appSettingsStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun updateSeedColor(color: Color) {
        context.appSettingsStore.edit { it[seedColorKey] = color.toArgb() }
    }

    suspend fun updateUseDynamicColor(use: Boolean) {
        context.appSettingsStore.edit { it[useDynamicColorKey] = use }
    }

    suspend fun updateFontScale(scale: Float) {
        context.appSettingsStore.edit { it[fontScaleKey] = scale }
    }

    suspend fun updateAnimationSpeed(speed: AnimationSpeed) {
        context.appSettingsStore.edit { it[animationSpeedKey] = speed.name }
    }

    suspend fun updateTransitionStyle(style: TransitionStyle) {
        context.appSettingsStore.edit { it[transitionStyleKey] = style.name }
    }

    suspend fun updateIsPureBlack(isPure: Boolean) {
        context.appSettingsStore.edit { it[isPureBlackKey] = isPure }
    }

    suspend fun updateFrameRateMode(mode: FrameRateMode) {
        context.appSettingsStore.edit { it[frameRateModeKey] = mode.name }
    }

    suspend fun updateCornerStyle(style: CornerStyle) {
        context.appSettingsStore.edit { it[cornerStyleKey] = style.name }
    }

    val hdFeed: Flow<Boolean> = context.appSettingsStore.data.map { it[hdFeedKey] ?: false }

    val personalizedRcmd: Flow<Boolean> = context.appSettingsStore.data.map { it[personalizedRcmdKey] ?: true }

    private val interestDoneKey = booleanPreferencesKey("interest_done")
    val interestDone: Flow<Boolean> = context.appSettingsStore.data.map { it[interestDoneKey] ?: false }

    suspend fun updateHdFeed(enabled: Boolean) {
        context.appSettingsStore.edit { it[hdFeedKey] = enabled }
    }

    suspend fun updatePersonalizedRcmd(enabled: Boolean) {
        context.appSettingsStore.edit { it[personalizedRcmdKey] = enabled }
    }

    suspend fun markInterestDone() {
        context.appSettingsStore.edit { it[interestDoneKey] = true }
    }

    private val blockGaiaKey = booleanPreferencesKey("block_gaia")
    val blockGaia: Flow<Boolean> = context.appSettingsStore.data.map { it[blockGaiaKey] ?: false }

    suspend fun updateBlockGaia(enabled: Boolean) {
        context.appSettingsStore.edit { it[blockGaiaKey] = enabled }
    }

    private val enableHdrAnd8kKey = booleanPreferencesKey("enable_hdr_8k")
    private val defaultVideoQualityKey = intPreferencesKey("default_video_quality")
    private val defaultAudioQualityKey = intPreferencesKey("default_audio_quality")
    private val forceHostKey = intPreferencesKey("force_host")
    private val needTrialKey = booleanPreferencesKey("need_trial")
    private val preferredCodecKey = intPreferencesKey("preferred_codec_qn")
    private val playerMinBufMsKey = intPreferencesKey("player_min_buf_ms")
    private val playerMaxBufMsKey = intPreferencesKey("player_max_buf_ms")
    private val playerPlayBufMsKey = intPreferencesKey("player_play_buf_ms")
    private val playerRebufMsKey = intPreferencesKey("player_rebuf_ms")
    private val playerBackBufMsKey = intPreferencesKey("player_back_buf_ms")
    private val preferSoftDecKey = booleanPreferencesKey("prefer_soft_dec")
    private val decFallbackKey = booleanPreferencesKey("dec_fallback")
    private val bgPlayKey = booleanPreferencesKey("bg_play")

    val enableHdrAnd8k: Flow<Boolean> = context.appSettingsStore.data.map { it[enableHdrAnd8kKey] ?: false }
    val defaultVideoQuality: Flow<Int> = context.appSettingsStore.data.map { it[defaultVideoQualityKey] ?: 80 }
    val defaultAudioQuality: Flow<Int> = context.appSettingsStore.data.map { it[defaultAudioQualityKey] ?: 0 }
    val forceHost: Flow<Int> = context.appSettingsStore.data.map { it[forceHostKey] ?: 0 }
    val needTrial: Flow<Boolean> = context.appSettingsStore.data.map { it[needTrialKey] ?: false }
    val preferredCodec: Flow<Int> = context.appSettingsStore.data.map { it[preferredCodecKey] ?: 2 }
    val playerMinBufferMs: Flow<Int> = context.appSettingsStore.data.map { it[playerMinBufMsKey] ?: 2_000 }
    val playerMaxBufferMs: Flow<Int> = context.appSettingsStore.data.map { it[playerMaxBufMsKey] ?: 15_000 }
    val playerPlaybackBufferMs: Flow<Int> = context.appSettingsStore.data.map { it[playerPlayBufMsKey] ?: 250 }
    val playerRebufferMs: Flow<Int> = context.appSettingsStore.data.map { it[playerRebufMsKey] ?: 500 }
    val playerBackBufferMs: Flow<Int> = context.appSettingsStore.data.map { it[playerBackBufMsKey] ?: 5_000 }
    val preferSoftwareDecode: Flow<Boolean> = context.appSettingsStore.data.map { it[preferSoftDecKey] ?: false }
    val decoderFallback: Flow<Boolean> = context.appSettingsStore.data.map { it[decFallbackKey] ?: true }
    val backgroundPlayback: Flow<Boolean> = context.appSettingsStore.data.map { it[bgPlayKey] ?: false }

    suspend fun updateEnableHdrAnd8k(enabled: Boolean) {
        context.appSettingsStore.edit { it[enableHdrAnd8kKey] = enabled }
    }

    suspend fun updateDefaultVideoQuality(quality: Int) {
        context.appSettingsStore.edit { it[defaultVideoQualityKey] = quality }
    }

    suspend fun updateDefaultAudioQuality(quality: Int) {
        context.appSettingsStore.edit { it[defaultAudioQualityKey] = quality }
    }

    suspend fun updateForceHost(value: Int) {
        context.appSettingsStore.edit { it[forceHostKey] = value }
    }

    suspend fun updateNeedTrial(enabled: Boolean) {
        context.appSettingsStore.edit { it[needTrialKey] = enabled }
    }

    suspend fun updatePreferredCodec(codec: Int) {
        context.appSettingsStore.edit { it[preferredCodecKey] = codec }
    }

    suspend fun updatePlayerMinBufferMs(value: Int) {
        context.appSettingsStore.edit { it[playerMinBufMsKey] = value }
    }

    suspend fun updatePlayerMaxBufferMs(value: Int) {
        context.appSettingsStore.edit { it[playerMaxBufMsKey] = value }
    }

    suspend fun updatePlayerPlaybackBufferMs(value: Int) {
        context.appSettingsStore.edit { it[playerPlayBufMsKey] = value }
    }

    suspend fun updatePlayerRebufferMs(value: Int) {
        context.appSettingsStore.edit { it[playerRebufMsKey] = value }
    }

    suspend fun updatePlayerBackBufferMs(value: Int) {
        context.appSettingsStore.edit { it[playerBackBufMsKey] = value }
    }

    suspend fun updatePreferSoftwareDecode(enabled: Boolean) {
        context.appSettingsStore.edit { it[preferSoftDecKey] = enabled }
    }

    suspend fun updateDecoderFallback(enabled: Boolean) {
        context.appSettingsStore.edit { it[decFallbackKey] = enabled }
    }

    suspend fun updateBackgroundPlayback(enabled: Boolean) {
        context.appSettingsStore.edit { it[bgPlayKey] = enabled }
    }
}
