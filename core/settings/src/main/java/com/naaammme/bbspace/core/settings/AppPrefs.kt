package com.naaammme.bbspace.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.model.AnimationSpeed
import com.naaammme.bbspace.core.model.CornerStyle
import com.naaammme.bbspace.core.model.DEFAULT_PULL_REFRESH_DISTANCE_DP
import com.naaammme.bbspace.core.model.DEFAULT_ROUND_SCREEN_SAFE_PADDING_SCALE
import com.naaammme.bbspace.core.model.DEFAULT_SEED_COLOR_ARGB
import com.naaammme.bbspace.core.model.DanmakuConfig
import com.naaammme.bbspace.core.model.FrameRateMode
import com.naaammme.bbspace.core.model.NavTransitionConfig
import com.naaammme.bbspace.core.model.PaletteStyle
import com.naaammme.bbspace.core.model.PlayerBufferProfile
import com.naaammme.bbspace.core.model.ThemeMode
import com.naaammme.bbspace.core.model.ThemeVisualConfig
import com.naaammme.bbspace.core.model.TransitionStyle
import com.naaammme.bbspace.core.model.VideoCdnMode
import com.naaammme.bbspace.core.model.VideoCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.enums.enumEntries
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val INTEREST_DONE_KEY = "interest_done"
private val Context.appSettingsDataStore by preferencesDataStore("app_settings")

fun <T> Flow<T>.stateInWhileSubscribed(
    scope: CoroutineScope,
    initialValue: T
): StateFlow<T> = stateIn(
    scope = scope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = initialValue
)

class Setting<T> internal constructor(
    val key: String,
    val defaultValue: T,
    private val dataStore: DataStore<Preferences>,
    private val prefsState: StateFlow<Preferences?>,
    private val writeScope: CoroutineScope,
    internal val read: (Preferences) -> T,
    internal val write: (MutablePreferences, T) -> Unit
) {
    val current: T
        get() = prefsState.value?.let(read) ?: defaultValue

    val flow: Flow<T> = prefsState.filterNotNull().map(read).distinctUntilChanged()

    fun stateIn(scope: CoroutineScope): StateFlow<T> =
        flow.stateInWhileSubscribed(scope, current)

    suspend fun get(): T = read(dataStore.data.first())

    suspend fun set(value: T) {
        withContext(NonCancellable) {
            dataStore.edit { write(it, value) }
        }
    }

    fun commit(value: T) {
        writeScope.launch {
            dataStore.edit { write(it, value) }
        }
    }

    suspend fun update(transform: (T) -> T) {
        withContext(NonCancellable) {
            dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
        }
    }
}

@Singleton
class SettingStore @Inject constructor(
    @param:ApplicationContext context: Context
) {
    // 直接使用 DataStore 的缓存和数据流
    private val dataStore = context.appSettingsDataStore
    private val readScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeErrorHandler = CoroutineExceptionHandler { _, throwable ->
        Logger.e("SettingStore", throwable) { "写入设置失败" }
    }
    private val writeScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(1) + writeErrorHandler
    )
    private val prefsState: StateFlow<Preferences?> = dataStore.data.stateIn(
        scope = readScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    internal fun boolean(key: String, default: Boolean): Setting<Boolean> =
        createSetting(booleanPreferencesKey(key), default)

    internal fun int(key: String, default: Int, range: IntRange? = null): Setting<Int> =
        createSetting(intPreferencesKey(key), default) { value ->
            range?.let { value.coerceIn(it) } ?: value
        }

    internal fun float(
        key: String,
        default: Float,
        range: ClosedFloatingPointRange<Float>
    ): Setting<Float> =
        createSetting(floatPreferencesKey(key), default) { value ->
            value.takeUnless(Float::isNaN)?.coerceIn(range) ?: default
        }

    internal inline fun <reified E : Enum<E>> enum(key: String, default: E): Setting<E> =
        enum(key, default, enumEntries<E>())

    internal fun <E : Enum<E>> enum(key: String, default: E, entries: List<E>): Setting<E> {
        val prefKey = stringPreferencesKey(key)
        return Setting(
            key = key,
            defaultValue = default,
            dataStore = dataStore,
            prefsState = prefsState,
            writeScope = writeScope,
            read = { prefs ->
                val name = prefs[prefKey]
                entries.firstOrNull { it.name == name } ?: default
            },
            write = { prefs, value -> prefs[prefKey] = value.name }
        )
    }

    internal fun <R> observe(transform: (Reader) -> R): Flow<R> =
        prefsState.filterNotNull().map { transform(Reader(it)) }.distinctUntilChanged()

    internal fun <R> current(transform: (Reader) -> R, default: R): R =
        prefsState.value?.let { transform(Reader(it)) } ?: default

    internal suspend fun <R> read(transform: (Reader) -> R): R =
        transform(Reader(dataStore.data.first()))

    internal suspend fun editBatch(block: (Editor) -> Unit) {
        withContext(NonCancellable) {
            dataStore.edit { block(Editor(it)) }
        }
    }

    suspend fun resetAllSettings() {
        withContext(NonCancellable) {
            dataStore.edit { prefs ->
                prefs.asMap().keys.filter { it.name != INTEREST_DONE_KEY }.forEach { prefs.remove(it) }
            }
        }
    }

    private fun <T> createSetting(
        key: Preferences.Key<T>,
        default: T,
        normalize: (T) -> T = { it }
    ): Setting<T> = Setting(
        key = key.name,
        defaultValue = normalize(default),
        dataStore = dataStore,
        prefsState = prefsState,
        writeScope = writeScope,
        read = { prefs -> normalize(prefs[key] ?: default) },
        write = { prefs, value -> prefs[key] = normalize(value) }
    )

    internal class Reader(private val prefs: Preferences) {
        operator fun <T> get(setting: Setting<T>): T = setting.read(prefs)
    }

    internal class Editor(private val prefs: MutablePreferences) {
        val reader = Reader(prefs)

        operator fun <T> set(setting: Setting<T>, value: T) {
            setting.write(prefs, value)
        }
    }
}

@Singleton
class AppearancePrefs @Inject constructor(
    private val store: SettingStore
) {
    val themeMode = store.enum("theme_mode", ThemeMode.SYSTEM)
    val seedColorArgb = store.int("seed_color", DEFAULT_SEED_COLOR_ARGB)
    val useDynamicColor = store.boolean("use_dynamic_color", true)
    val paletteStyle = store.enum("palette_style", PaletteStyle.TONAL_SPOT)
    val swapBaseColors = store.boolean("swap_base_colors", false)
    val fontScale = store.float("font_scale", 1.0f, range = 0.6f..1.4f)
    val uiScale = store.float("ui_scale", 1.0f, range = 0.5f..1.1f)
    val roundScreenSafePaddingScale = store.float(
        "round_screen_safe_padding_scale",
        DEFAULT_ROUND_SCREEN_SAFE_PADDING_SCALE,
        range = 0f..1.5f
    )
    val pullRefreshDistanceDp = store.float(
        "pull_refresh_distance",
        DEFAULT_PULL_REFRESH_DISTANCE_DP,
        range = 16f..160f
    )
    val animationSpeed = store.enum("animation_speed", AnimationSpeed.NORMAL)
    val transitionStyle = store.enum("transition_style", TransitionStyle.SHARED_AXIS_Z)
    val isPureBlack = store.boolean("is_pure_black", false)
    val preferredFrameRate = store.enum("frame_rate_mode", FrameRateMode.AUTO)
    val cornerStyle = store.enum("corner_style", CornerStyle.STANDARD)
    val fixBottomBar = store.boolean("fix_bottom_bar", true)

    val themeVisualConfig: Flow<ThemeVisualConfig> = store.observe(::readThemeVisual)

    val navTransitionConfig: Flow<NavTransitionConfig> = store.observe(::readNavTransition)

    fun currentThemeVisual(): ThemeVisualConfig =
        store.current(::readThemeVisual, ThemeVisualConfig())

    fun currentNavTransition(): NavTransitionConfig =
        store.current(::readNavTransition, NavTransitionConfig())

    private fun readThemeVisual(reader: SettingStore.Reader): ThemeVisualConfig {
        return ThemeVisualConfig(
            themeMode = reader[themeMode],
            seedColorArgb = reader[seedColorArgb],
            useDynamicColor = reader[useDynamicColor],
            paletteStyle = reader[paletteStyle],
            swapBaseColors = reader[swapBaseColors],
            fontScale = reader[fontScale],
            uiScale = reader[uiScale],
            isPureBlack = reader[isPureBlack],
            cornerStyle = reader[cornerStyle]
        )
    }

    private fun readNavTransition(reader: SettingStore.Reader): NavTransitionConfig {
        return NavTransitionConfig(
            transitionStyle = reader[transitionStyle],
            animationSpeed = reader[animationSpeed]
        )
    }
}

@Singleton
class PlaybackPrefs @Inject constructor(
    private val store: SettingStore
) {
    val enableHdrAnd8k = store.boolean("enable_hdr_8k", false)
    val defaultVideoQuality = store.int("default_video_quality", 64)
    val defaultAudioQuality = store.int("default_audio_quality", 0)
    val forceHttps = store.boolean("force_https", false)
    val needTrial = store.boolean("need_trial", false)
    val preferredCodec = store.enum("preferred_codec", VideoCodec.HEVC)
    val enableWebPlayback = store.boolean("enable_web_playback", false)
    val bufferProfile = store.enum("buffer_profile", PlayerBufferProfile.FastStart)
    val backgroundPlayback = store.boolean("bg_play", true)
    val inAppMiniPlayer = store.boolean("in_app_mini_player", true)
    val reportPlayback = store.boolean("report_playback", true)
    val preferSoftwareDecode = store.boolean("prefer_soft_dec", false)
    val decoderFallback = store.boolean("dec_fallback", true)
    val autoRotateFullscreen = store.boolean("auto_rotate_fullscreen", false)
    val gestureSpeed = store.float("gesture_speed", 2f, range = 0.25f..3f)
    val videoCdnMode = store.enum("video_cdn_mode", VideoCdnMode.Backup2)

    suspend fun readEngineSettings(): EngineSettings = store.read {
        EngineSettings(it[bufferProfile], it[preferSoftwareDecode], it[decoderFallback])
    }

    suspend fun readRequestSettings(): RequestSettings = store.read {
        RequestSettings(
            enableHdrAnd8k = it[enableHdrAnd8k],
            needTrial = it[needTrial],
            preferredCodec = it[preferredCodec],
            forceHttps = it[forceHttps]
        )
    }

    suspend fun readOpenVideoSettings(): OpenVideoSettings = store.read {
        OpenVideoSettings(
            defaultVideoQuality = it[defaultVideoQuality],
            defaultAudioQuality = it[defaultAudioQuality],
            videoCdnMode = it[videoCdnMode]
        )
    }

    data class EngineSettings(
        val bufferProfile: PlayerBufferProfile,
        val preferSoftwareDecode: Boolean,
        val decoderFallback: Boolean
    )

    data class RequestSettings(
        val enableHdrAnd8k: Boolean,
        val needTrial: Boolean,
        val preferredCodec: VideoCodec,
        val forceHttps: Boolean
    )

    data class OpenVideoSettings(
        val defaultVideoQuality: Int,
        val defaultAudioQuality: Int,
        val videoCdnMode: VideoCdnMode
    )
}

@Singleton
class DanmakuPrefs @Inject constructor(
    private val store: SettingStore
) {
    val enabled = store.boolean("danmaku_enabled", false)
    val areaPercent = store.int("danmaku_area_percent", 100, range = 25..100)
    val opacity = store.float("danmaku_opacity", 1f, range = 0.1f..1f)
    val textScale = store.float("danmaku_text_scale", 1f, range = 0.5f..2f)
    val speed = store.float("danmaku_speed", 1f, range = 0.5f..2f)
    val densityLevel = store.int("danmaku_density", 1, range = 0..2)
    val weightFilterLevel = store.int("danmaku_weight_filter_level", 2, range = 0..10)
    val mergeDuplicates = store.boolean("danmaku_merge_duplicates", true)
    val showTop = store.boolean("danmaku_show_top", true)
    val showBottom = store.boolean("danmaku_show_bottom", true)
    val showScrollRl = store.boolean("danmaku_show_scroll_rl", true)

    val config: Flow<DanmakuConfig> = store.observe(::readConfig)

    fun currentConfig(): DanmakuConfig = store.current(::readConfig, DanmakuConfig())

    suspend fun update(transform: (DanmakuConfig) -> DanmakuConfig) {
        store.editBatch { editor ->
            val current = readConfig(editor.reader)
            val next = transform(current)
            if (next == current) return@editBatch
            if (next.enabled != current.enabled) editor[enabled] = next.enabled
            if (next.areaPercent != current.areaPercent) editor[areaPercent] = next.areaPercent
            if (next.opacity != current.opacity) editor[opacity] = next.opacity
            if (next.textScale != current.textScale) editor[textScale] = next.textScale
            if (next.speed != current.speed) editor[speed] = next.speed
            if (next.densityLevel != current.densityLevel) editor[densityLevel] = next.densityLevel
            if (next.weightFilterLevel != current.weightFilterLevel) {
                editor[weightFilterLevel] = next.weightFilterLevel
            }
            if (next.mergeDuplicates != current.mergeDuplicates) {
                editor[mergeDuplicates] = next.mergeDuplicates
            }
            if (next.showTop != current.showTop) editor[showTop] = next.showTop
            if (next.showBottom != current.showBottom) editor[showBottom] = next.showBottom
            if (next.showScrollRl != current.showScrollRl) editor[showScrollRl] = next.showScrollRl
        }
    }

    private fun readConfig(reader: SettingStore.Reader): DanmakuConfig {
        return DanmakuConfig(
            enabled = reader[enabled],
            areaPercent = reader[areaPercent],
            opacity = reader[opacity],
            textScale = reader[textScale],
            speed = reader[speed],
            densityLevel = reader[densityLevel],
            weightFilterLevel = reader[weightFilterLevel],
            mergeDuplicates = reader[mergeDuplicates],
            showTop = reader[showTop],
            showBottom = reader[showBottom],
            showScrollRl = reader[showScrollRl]
        )
    }
}

@Singleton
class FeedPrefs @Inject constructor(
    private val store: SettingStore
) {
    val hdFeed = store.boolean("hd_feed", false)
    val personalizedRcmd = store.boolean("personalized_rcmd", true)
    val lessonsMode = store.boolean("lessons_mode", false)
    val teenagersMode = store.boolean("teenagers_mode", false)
    val teenagersAge = store.int("teenagers_age", DEFAULT_TEENAGERS_AGE, range = MIN_TEENAGERS_AGE..MAX_TEENAGERS_AGE)
    val interestDone = store.boolean(INTEREST_DONE_KEY, false)

    suspend fun readRequestSettings(): RequestSettings = store.read {
        RequestSettings(
            hdFeed = it[hdFeed],
            personalizedRcmd = it[personalizedRcmd],
            lessonsMode = it[lessonsMode],
            teenagersMode = it[teenagersMode],
            teenagersAge = it[teenagersAge]
        )
    }

    data class RequestSettings(
        val hdFeed: Boolean,
        val personalizedRcmd: Boolean,
        val lessonsMode: Boolean,
        val teenagersMode: Boolean,
        val teenagersAge: Int
    )

    private companion object {
        const val DEFAULT_TEENAGERS_AGE = 16
        const val MIN_TEENAGERS_AGE = 1
        const val MAX_TEENAGERS_AGE = 17
    }
}

@Singleton
class SystemPrefs @Inject constructor(
    store: SettingStore
) {
    val autoCheckUpdate = store.boolean("auto_check_update", true)
    val useSystemDns = store.boolean("use_system_dns", false)
    val blockGaia = store.boolean("block_gaia", false)
}
