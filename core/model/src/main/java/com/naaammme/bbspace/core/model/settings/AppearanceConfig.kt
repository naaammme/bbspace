package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

const val DEFAULT_SEED_COLOR_ARGB: Int = 0xFFFB7299.toInt()
const val DEFAULT_PULL_REFRESH_DISTANCE_DP: Float = 80f
const val DEFAULT_ROUND_SCREEN_SAFE_PADDING_SCALE: Float = 1.0f

@Immutable
data class ThemeVisualConfig(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val seedColorArgb: Int = DEFAULT_SEED_COLOR_ARGB,
    val useDynamicColor: Boolean = true,
    val paletteStyle: PaletteStyle = PaletteStyle.TONAL_SPOT,
    val swapBaseColors: Boolean = false,
    val fontScale: Float = 1.0f,
    val uiScale: Float = 1.0f,
    val isPureBlack: Boolean = false,
    val cornerStyle: CornerStyle = CornerStyle.STANDARD
)

@Immutable
data class NavTransitionConfig(
    val transitionStyle: TransitionStyle = TransitionStyle.SHARED_AXIS_Z,
    val animationSpeed: AnimationSpeed = AnimationSpeed.NORMAL
)

enum class CornerStyle {
    SQUARE,
    STANDARD,
    ROUNDED,
    CIRCULAR
}

enum class TransitionStyle {
    NONE,
    SHARED_AXIS_X,
    SHARED_AXIS_Y,
    SHARED_AXIS_Z,
    FADE_THROUGH,
    SLIDE
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

enum class PaletteStyle {
    TONAL_SPOT,
    EXPRESSIVE,
    NEUTRAL,
    VIBRANT,
    MONOCHROME
}

enum class AnimationSpeed(val multiplier: Float) {
    OFF(0f),
    FAST(0.5f),
    NORMAL(1f),
    SLOW(1.5f)
}

enum class FrameRateMode(val value: Float) {
    AUTO(0f),
    RATE_60(60f),
    RATE_90(90f),
    RATE_120(120f),
    RATE_144(144f)
}
