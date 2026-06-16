package com.naaammme.bbspace.core.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.naaammme.bbspace.core.designsystem.theme.AnimationSpeed
import com.naaammme.bbspace.core.designsystem.theme.CornerStyle
import com.naaammme.bbspace.core.designsystem.theme.DEFAULT_PULL_REFRESH_DISTANCE_DP
import com.naaammme.bbspace.core.designsystem.theme.FrameRateMode
import com.naaammme.bbspace.core.designsystem.theme.MAX_PULL_REFRESH_DISTANCE_DP
import com.naaammme.bbspace.core.designsystem.theme.MIN_PULL_REFRESH_DISTANCE_DP
import com.naaammme.bbspace.core.designsystem.theme.ThemeConfig
import com.naaammme.bbspace.core.designsystem.theme.ThemeMode
import com.naaammme.bbspace.core.designsystem.theme.TransitionStyle
import com.naaammme.bbspace.core.domain.player.PlayerSettings
import com.naaammme.bbspace.core.model.DanmakuConfig
import com.naaammme.bbspace.core.model.PlayerBufferProfile
import com.naaammme.bbspace.core.model.PlayerBufferSettings
import com.naaammme.bbspace.core.model.PlayerPlaybackPrefs
import com.naaammme.bbspace.core.model.PlayerSettingsState
import com.naaammme.bbspace.core.model.VideoCdnMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettings @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PlayerSettings {
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val seedColorKey = intPreferencesKey("seed_color")
    private val useDynamicColorKey = booleanPreferencesKey("use_dynamic_color")
    private val swapBaseColorsKey = booleanPreferencesKey("swap_base_colors")
    private val fontScaleKey = floatPreferencesKey("font_scale")
    private val pullRefreshDistanceKey = floatPreferencesKey("pull_refresh_distance")
    private val animationSpeedKey = stringPreferencesKey("animation_speed")
    private val transitionStyleKey = stringPreferencesKey("transition_style")
    private val isPureBlackKey = booleanPreferencesKey("is_pure_black")
    private val frameRateModeKey = stringPreferencesKey("frame_rate_mode")
    private val cornerStyleKey = stringPreferencesKey("corner_style")
    private val hdFeedKey = booleanPreferencesKey("hd_feed")
    private val personalizedRcmdKey = booleanPreferencesKey("personalized_rcmd")
    private val lessonsModeKey = booleanPreferencesKey("lessons_mode")
    private val teenagersModeKey = booleanPreferencesKey("teenagers_mode")
    private val teenagersAgeKey = intPreferencesKey("teenagers_age")
    private val autoCheckUpdateKey = booleanPreferencesKey("auto_check_update")

    val themeConfig: Flow<ThemeConfig> = context.appSettingsDataStore.data.map { prefs ->
        ThemeConfig(
            themeMode = prefs[themeModeKey]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM,
            seedColor = Color(prefs[seedColorKey] ?: 0xFFFB7299.toInt()),
            useDynamicColor = prefs[useDynamicColorKey] ?: true,
            swapBaseColors = prefs[swapBaseColorsKey] ?: false,
            fontScale = prefs[fontScaleKey] ?: 1.0f,
            pullRefreshDistanceDp = (prefs[pullRefreshDistanceKey] ?: DEFAULT_PULL_REFRESH_DISTANCE_DP)
                .coerceIn(MIN_PULL_REFRESH_DISTANCE_DP, MAX_PULL_REFRESH_DISTANCE_DP),
            animationSpeed = prefs[animationSpeedKey]?.let { AnimationSpeed.valueOf(it) } ?: AnimationSpeed.NORMAL,
            transitionStyle = prefs[transitionStyleKey]?.let { TransitionStyle.valueOf(it) } ?: TransitionStyle.SHARED_AXIS_Z,
            isPureBlack = prefs[isPureBlackKey] ?: false,
            preferredFrameRate = prefs[frameRateModeKey]?.let { FrameRateMode.valueOf(it) } ?: FrameRateMode.AUTO,
            cornerStyle = prefs[cornerStyleKey]?.let { CornerStyle.valueOf(it) } ?: CornerStyle.STANDARD
        )
    }.distinctUntilChanged()

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.appSettingsDataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun updateSeedColor(color: Color) {
        context.appSettingsDataStore.edit { it[seedColorKey] = color.toArgb() }
    }

    suspend fun updateUseDynamicColor(use: Boolean) {
        context.appSettingsDataStore.edit { it[useDynamicColorKey] = use }
    }

    suspend fun updateSwapBaseColors(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[swapBaseColorsKey] = enabled }
    }

    suspend fun updateFontScale(scale: Float) {
        context.appSettingsDataStore.edit { it[fontScaleKey] = scale }
    }

    suspend fun updatePullRefreshDistance(distanceDp: Float) {
        context.appSettingsDataStore.edit {
            it[pullRefreshDistanceKey] = distanceDp.coerceIn(
                MIN_PULL_REFRESH_DISTANCE_DP,
                MAX_PULL_REFRESH_DISTANCE_DP
            )
        }
    }

    suspend fun updateAnimationSpeed(speed: AnimationSpeed) {
        context.appSettingsDataStore.edit { it[animationSpeedKey] = speed.name }
    }

    suspend fun updateTransitionStyle(style: TransitionStyle) {
        context.appSettingsDataStore.edit { it[transitionStyleKey] = style.name }
    }

    suspend fun updateIsPureBlack(isPure: Boolean) {
        context.appSettingsDataStore.edit { it[isPureBlackKey] = isPure }
    }

    suspend fun updateFrameRateMode(mode: FrameRateMode) {
        context.appSettingsDataStore.edit { it[frameRateModeKey] = mode.name }
    }

    suspend fun updateCornerStyle(style: CornerStyle) {
        context.appSettingsDataStore.edit { it[cornerStyleKey] = style.name }
    }

    val hdFeed: Flow<Boolean> = context.appSettingsDataStore.data.map { it[hdFeedKey] ?: false }

    val personalizedRcmd: Flow<Boolean> = context.appSettingsDataStore.data.map { it[personalizedRcmdKey] ?: true }

    val lessonsMode: Flow<Boolean> = context.appSettingsDataStore.data.map { it[lessonsModeKey] ?: false }

    val teenagersMode: Flow<Boolean> = context.appSettingsDataStore.data.map { it[teenagersModeKey] ?: false }

    val teenagersAge: Flow<Int> = context.appSettingsDataStore.data.map {
        (it[teenagersAgeKey] ?: DEFAULT_TEENAGERS_AGE).coerceIn(MIN_TEENAGERS_AGE, MAX_TEENAGERS_AGE)
    }

    val autoCheckUpdate: Flow<Boolean> = context.appSettingsDataStore.data.map { it[autoCheckUpdateKey] ?: true }

    private val interestDoneKey = booleanPreferencesKey("interest_done")
    val interestDone: Flow<Boolean> = context.appSettingsDataStore.data.map { it[interestDoneKey] ?: false }

    suspend fun updateHdFeed(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[hdFeedKey] = enabled }
    }

    suspend fun updatePersonalizedRcmd(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[personalizedRcmdKey] = enabled }
    }

    suspend fun updateLessonsMode(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[lessonsModeKey] = enabled }
    }

    suspend fun updateTeenagersMode(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[teenagersModeKey] = enabled }
    }

    suspend fun updateTeenagersAge(age: Int) {
        context.appSettingsDataStore.edit { it[teenagersAgeKey] = age.coerceIn(MIN_TEENAGERS_AGE, MAX_TEENAGERS_AGE) }
    }

    suspend fun updateAutoCheckEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[autoCheckUpdateKey] = enabled }
    }

    suspend fun markInterestDone() {
        context.appSettingsDataStore.edit { it[interestDoneKey] = true }
    }

    private val blockGaiaKey = booleanPreferencesKey("block_gaia")
    val blockGaia: Flow<Boolean> = context.appSettingsDataStore.data.map { it[blockGaiaKey] ?: false }

    suspend fun updateBlockGaia(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[blockGaiaKey] = enabled }
    }

    private val enableHdrAnd8kKey = booleanPreferencesKey("enable_hdr_8k")
    private val defaultVideoQualityKey = intPreferencesKey("default_video_quality")
    private val defaultAudioQualityKey = intPreferencesKey("default_audio_quality")
    private val forceHostKey = intPreferencesKey("force_host")
    private val needTrialKey = booleanPreferencesKey("need_trial")
    private val preferredCodecKey = intPreferencesKey("preferred_codec_qn")
    private val enableWebPlaybackKey = booleanPreferencesKey("enable_web_playback")
    private val playerBufferProfileKey = intPreferencesKey("player_buffer_profile")
    private val preferSoftDecKey = booleanPreferencesKey("prefer_soft_dec")
    private val decFallbackKey = booleanPreferencesKey("dec_fallback")
    private val bgPlayKey = booleanPreferencesKey("bg_play")
    private val inAppMiniPlayerKey = booleanPreferencesKey("in_app_mini_player")
    private val autoRotateFullscreenKey = booleanPreferencesKey("auto_rotate_fullscreen")
    private val gestureSpeedKey = floatPreferencesKey("gesture_speed")
    private val videoCdnModeKey = stringPreferencesKey("video_cdn_mode")
    private val reportPlaybackKey = booleanPreferencesKey("report_playback")
    private val danmakuEnabledKey = booleanPreferencesKey("danmaku_enabled")
    private val danmakuAreaPercentKey = intPreferencesKey("danmaku_area_percent")
    private val danmakuOpacityKey = floatPreferencesKey("danmaku_opacity")
    private val danmakuTextScaleKey = floatPreferencesKey("danmaku_text_scale")
    private val danmakuSpeedKey = floatPreferencesKey("danmaku_speed")
    private val danmakuDensityKey = intPreferencesKey("danmaku_density")
    private val danmakuWeightFilterLevelKey = intPreferencesKey("danmaku_weight_filter_level")
    private val danmakuMergeDuplicatesKey = booleanPreferencesKey("danmaku_merge_duplicates")
    private val danmakuShowTopKey = booleanPreferencesKey("danmaku_show_top")
    private val danmakuShowBottomKey = booleanPreferencesKey("danmaku_show_bottom")
    private val danmakuShowScrollRlKey = booleanPreferencesKey("danmaku_show_scroll_rl")

    val enableHdrAnd8k: Flow<Boolean> = context.appSettingsDataStore.data.map { it[enableHdrAnd8kKey] ?: false }
    val defaultVideoQuality: Flow<Int> = context.appSettingsDataStore.data.map { it[defaultVideoQualityKey] ?: 64 }
    val defaultAudioQuality: Flow<Int> = context.appSettingsDataStore.data.map { it[defaultAudioQualityKey] ?: 0 }
    val forceHost: Flow<Int> = context.appSettingsDataStore.data.map { it[forceHostKey] ?: 0 }
    val needTrial: Flow<Boolean> = context.appSettingsDataStore.data.map { it[needTrialKey] ?: false }
    val preferredCodec: Flow<Int> = context.appSettingsDataStore.data.map { it[preferredCodecKey] ?: 2 }
    val enableWebPlayback: Flow<Boolean> = context.appSettingsDataStore.data.map { it[enableWebPlaybackKey] ?: false }
    // 播放偏好和外观 推荐等设置一样都属于持久偏好，不在这里做会话态缓存
    override val state: Flow<PlayerSettingsState> = context.appSettingsDataStore.data.map { prefs ->
        val defDanmaku = DanmakuConfig()
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
                gestureSpeed = (prefs[gestureSpeedKey] ?: 2f).coerceIn(0.25f, 3f),
                videoCdnMode = prefs[videoCdnModeKey]
                    ?.let(VideoCdnMode::valueOf)
                    ?: VideoCdnMode.Backup1
            ),
            danmaku = DanmakuConfig(
                enabled = prefs[danmakuEnabledKey] ?: defDanmaku.enabled,
                areaPercent = prefs[danmakuAreaPercentKey] ?: defDanmaku.areaPercent,
                opacity = prefs[danmakuOpacityKey] ?: defDanmaku.opacity,
                textScale = prefs[danmakuTextScaleKey] ?: defDanmaku.textScale,
                speed = prefs[danmakuSpeedKey] ?: defDanmaku.speed,
                densityLevel = prefs[danmakuDensityKey] ?: defDanmaku.densityLevel,
                weightFilterLevel = (prefs[danmakuWeightFilterLevelKey] ?: defDanmaku.weightFilterLevel).coerceIn(0, 10),
                mergeDuplicates = prefs[danmakuMergeDuplicatesKey] ?: defDanmaku.mergeDuplicates,
                showTop = prefs[danmakuShowTopKey] ?: defDanmaku.showTop,
                showBottom = prefs[danmakuShowBottomKey] ?: defDanmaku.showBottom,
                showScrollRl = prefs[danmakuShowScrollRlKey] ?: defDanmaku.showScrollRl
            )
        )
    }.distinctUntilChanged()

    suspend fun updateEnableHdrAnd8k(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[enableHdrAnd8kKey] = enabled }
    }

    suspend fun updateDefaultVideoQuality(quality: Int) {
        context.appSettingsDataStore.edit { it[defaultVideoQualityKey] = quality }
    }

    suspend fun updateDefaultAudioQuality(quality: Int) {
        context.appSettingsDataStore.edit { it[defaultAudioQualityKey] = quality }
    }

    suspend fun updateForceHost(value: Int) {
        context.appSettingsDataStore.edit { it[forceHostKey] = value }
    }

    suspend fun updateNeedTrial(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[needTrialKey] = enabled }
    }

    suspend fun updatePreferredCodec(codec: Int) {
        context.appSettingsDataStore.edit { it[preferredCodecKey] = codec }
    }

    suspend fun updateEnableWebPlayback(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[enableWebPlaybackKey] = enabled }
    }

    override suspend fun setBufferProfile(profile: PlayerBufferProfile) {
        context.appSettingsDataStore.edit { it[playerBufferProfileKey] = profile.ordinal }
    }

    override suspend fun setBackgroundPlayback(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[bgPlayKey] = enabled }
    }

    override suspend fun setInAppMiniPlayer(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[inAppMiniPlayerKey] = enabled }
    }

    override suspend fun setReportPlayback(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[reportPlaybackKey] = enabled }
    }

    override suspend fun setPreferSoftwareDecode(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[preferSoftDecKey] = enabled }
    }

    override suspend fun setDecoderFallback(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[decFallbackKey] = enabled }
    }

    override suspend fun setAutoRotateFullscreen(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[autoRotateFullscreenKey] = enabled }
    }

    override suspend fun setGestureSpeed(speed: Float) {
        context.appSettingsDataStore.edit { it[gestureSpeedKey] = speed.coerceIn(0.25f, 3f) }
    }

    override suspend fun setVideoCdnMode(mode: VideoCdnMode) {
        context.appSettingsDataStore.edit { it[videoCdnModeKey] = mode.name }
    }

    override suspend fun setDanmaku(config: DanmakuConfig) {
        context.appSettingsDataStore.edit {
            it[danmakuEnabledKey] = config.enabled
            it[danmakuAreaPercentKey] = config.areaPercent.coerceIn(25, 100)
            it[danmakuOpacityKey] = config.opacity.coerceIn(0.1f, 1f)
            it[danmakuTextScaleKey] = config.textScale.coerceIn(0.5f, 2f)
            it[danmakuSpeedKey] = config.speed.coerceIn(0.5f, 2f)
            it[danmakuDensityKey] = config.densityLevel.coerceIn(0, 2)
            it[danmakuWeightFilterLevelKey] = config.weightFilterLevel.coerceIn(0, 10)
            it[danmakuMergeDuplicatesKey] = config.mergeDuplicates
            it[danmakuShowTopKey] = config.showTop
            it[danmakuShowBottomKey] = config.showBottom
            it[danmakuShowScrollRlKey] = config.showScrollRl
        }
    }

    suspend fun resetAllSettings() {
        context.appSettingsDataStore.edit {
            val interestDone = it[interestDoneKey] ?: false
            it.clear()
            if (interestDone) {
                it[interestDoneKey] = true
            }
        }
    }

    private companion object {
        const val DEFAULT_TEENAGERS_AGE = 16
        const val MIN_TEENAGERS_AGE = 1
        const val MAX_TEENAGERS_AGE = 17
    }

    private fun Int?.toPlayerBufferProfile(): PlayerBufferProfile {
        return PlayerBufferProfile.entries.getOrElse(this ?: PlayerBufferProfile.FastStart.ordinal) {
            PlayerBufferProfile.FastStart
        }
    }
}
