package com.naaammme.bbspace.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class ThemeConfig(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val seedColor: Color = Color(0xFFFB7299),
    val useDynamicColor: Boolean = false,
    val fontScale: Float = 1.0f,
    val animationSpeed: AnimationSpeed = AnimationSpeed.NORMAL,
    val transitionStyle: TransitionStyle = TransitionStyle.SHARED_AXIS_X,
    val isPureBlack: Boolean = false,
    val preferredFrameRate: FrameRateMode = FrameRateMode.AUTO,
    val cornerStyle: CornerStyle = CornerStyle.STANDARD
)

enum class CornerStyle {
    SQUARE,
    STANDARD,
    ROUNDED,
    CIRCULAR
}

enum class TransitionStyle {
    SHARED_AXIS_X,
    SHARED_AXIS_Y,
    SHARED_AXIS_Z,
    FADE_THROUGH,
    SLIDE
}

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
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

val PresetColors = listOf(
    Color(0xFFFB7299) to "哔哩粉",
    Color(0xFF00A1D6) to "哔哩蓝",
    Color(0xFFF44336) to "红色",
    Color(0xFFFF9800) to "橙色",
    Color(0xFFFFC107) to "琥珀",
    Color(0xFFFFEB3B) to "黄色",
    Color(0xFFCDDC39) to "酸橙",
    Color(0xFF8BC34A) to "浅绿",
    Color(0xFF4CAF50) to "绿色",
    Color(0xFF009688) to "青色",
    Color(0xFF00BCD4) to "蓝绿",
    Color(0xFF03A9F4) to "浅蓝",
    Color(0xFF2196F3) to "蓝色",
    Color(0xFF3F51B5) to "靛蓝",
    Color(0xFF9C27B0) to "紫色",
    Color(0xFF673AB7) to "深紫",
    Color(0xFF607D8B) to "蓝灰",
    Color(0xFF795548) to "棕色",
    Color(0xFF9E9E9E) to "灰色"
)
