package com.naaammme.bbspace.feature.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.designsystem.component.SettingsPageScaffold
import com.naaammme.bbspace.core.designsystem.component.bindSetting
import com.naaammme.bbspace.core.designsystem.theme.PresetColors
import com.naaammme.bbspace.core.model.AnimationSpeed
import com.naaammme.bbspace.core.model.CornerStyle
import com.naaammme.bbspace.core.model.PaletteStyle
import com.naaammme.bbspace.core.model.ThemeMode
import com.naaammme.bbspace.core.model.TransitionStyle
import com.naaammme.bbspace.core.settings.AppearancePrefs
import com.naaammme.bbspace.core.settings.Setting
import com.naaammme.bbspace.feature.settings.SettingChoiceChips
import com.naaammme.bbspace.feature.settings.SettingDiscreteSlider
import com.naaammme.bbspace.feature.settings.SettingListCard
import com.naaammme.bbspace.feature.settings.SettingSwitch
import kotlin.math.roundToInt

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    prefs: AppearancePrefs
) {
    SettingsPageScaffold(title = "外观设计", onBack = onBack) {
        item(key = "theme_mode") {
            SettingChoiceChips(
                title = "主题模式",
                subtitle = "浅色 深色 或跟随系统",
                setting = prefs.themeMode,
                options = ThemeMode.entries,
                label = { mode ->
                    when (mode) {
                        ThemeMode.LIGHT -> "浅色"
                        ThemeMode.DARK -> "深色"
                        ThemeMode.SYSTEM -> "跟随系统"
                    }
                }
            )
        }
        item(key = "dynamic_color") {
            SettingSwitch(
                title = "动态取色",
                subtitle = "Android 12+ 使用系统动态配色",
                setting = prefs.useDynamicColor
            )
        }
        item(key = "palette_style") {
            SettingListCard(
                title = "调色盘风格",
                setting = prefs.paletteStyle,
                options = PaletteStyle.entries,
                label = { style ->
                    when (style) {
                        PaletteStyle.TONAL_SPOT -> "特调"
                        PaletteStyle.EXPRESSIVE -> "表现力"
                        PaletteStyle.NEUTRAL -> "中性"
                        PaletteStyle.VIBRANT -> "鲜艳"
                        PaletteStyle.MONOCHROME -> "黑白"
                    }
                }
            )
        }
        item(key = "swap_base_colors") {
            SettingSwitch(
                title = "反转颜色",
                subtitle = "交换页面背景和低饱和容器色，强调色保持不变",
                setting = prefs.swapBaseColors
            )
        }
        item(key = "seed_color") {
            SeedColorPaletteCard(setting = prefs.seedColorArgb)
        }
        item(key = "pure_black") {
            SettingSwitch(
                title = "纯色背景",
                subtitle = "深色用纯黑，浅色用纯白，会参与背景层级反转",
                setting = prefs.isPureBlack
            )
        }
        item(key = "font_scale") {
            SettingDiscreteSlider(
                title = "字体大小",
                subtitle = "调整应用字体大小",
                setting = prefs.fontScale,
                values = FONT_SCALES,
                valueLabel = { "${(it * 100).roundToInt()}%" },
                minLabel = "小",
                midLabel = "标准",
                maxLabel = "大"
            )
        }
        item(key = "ui_scale") {
            SettingDiscreteSlider(
                title = "界面缩放",
                subtitle = "调整界面元素的整体大小",
                setting = prefs.uiScale,
                values = UI_SCALES,
                valueLabel = { "${(it * 100).roundToInt()}%" }
            )
        }
        item(key = "round_screen_safe_padding") {
            SettingDiscreteSlider(
                title = "圆屏安全边距",
                subtitle = "仅圆形屏幕生效，0% 为关闭，100% 为默认",
                setting = prefs.roundScreenSafePaddingScale,
                values = ROUND_SCREEN_SAFE_PADDING_SCALES,
                valueLabel = { "${(it * 100).roundToInt()}%" }
            )
        }
        item(key = "pull_refresh_distance") {
            SettingDiscreteSlider(
                title = "下拉刷新行程",
                subtitle = "数值越大，下拉越长才会触发刷新",
                setting = prefs.pullRefreshDistanceDp,
                values = PULL_REFRESH_DISTANCES,
                valueLabel = { "${it.roundToInt()}dp" },
                minLabel = "短",
                midLabel = "标准",
                maxLabel = "长"
            )
        }
        item(key = "fix_bottom_bar") {
            SettingSwitch(
                title = "固定底栏",
                subtitle = "主页底栏不再随滚动自动收起",
                setting = prefs.fixBottomBar
            )
        }
        item(key = "animation_speed") {
            SettingChoiceChips(
                title = "动画速度",
                subtitle = "调整界面动画速度",
                setting = prefs.animationSpeed,
                options = AnimationSpeed.entries,
                label = { speed ->
                    when (speed) {
                        AnimationSpeed.OFF -> "关闭"
                        AnimationSpeed.FAST -> "快速"
                        AnimationSpeed.NORMAL -> "标准"
                        AnimationSpeed.SLOW -> "慢速"
                    }
                }
            )
        }
        item(key = "corner_style") {
            SettingChoiceChips(
                title = "圆角风格",
                subtitle = "调整界面圆角风格",
                setting = prefs.cornerStyle,
                options = CornerStyle.entries,
                label = { style ->
                    when (style) {
                        CornerStyle.SQUARE -> "直角"
                        CornerStyle.STANDARD -> "标准"
                        CornerStyle.ROUNDED -> "圆润"
                        CornerStyle.CIRCULAR -> "圆形"
                    }
                }
            )
        }
        item(key = "transition_style") {
            SettingListCard(
                title = "过渡动画",
                subtitle = "调整页面切换动画样式",
                setting = prefs.transitionStyle,
                options = TransitionStyle.entries,
                label = { style ->
                    when (style) {
                        TransitionStyle.NONE -> "无动画"
                        TransitionStyle.SHARED_AXIS_X -> "水平滑动"
                        TransitionStyle.SHARED_AXIS_Y -> "垂直滑动"
                        TransitionStyle.SHARED_AXIS_Z -> "缩放"
                        TransitionStyle.FADE_THROUGH -> "淡入淡出"
                        TransitionStyle.SLIDE -> "滑动"
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeedColorPaletteCard(
    setting: Setting<Int>
) {
    val (selectedArgb, setColor) = setting.flow.bindSetting(setting.current, setting::commit)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("主题色", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PresetColors.forEach { (color, _) ->
                    val argb = color.toArgb()
                    val selected = argb == selectedArgb
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (selected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                setColor(argb)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private val FONT_SCALES = listOf(0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f)
private val UI_SCALES = listOf(0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f)
private val ROUND_SCREEN_SAFE_PADDING_SCALES = listOf(
    0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f,
    0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f
)
private val PULL_REFRESH_DISTANCES = listOf(
    16f, 24f, 32f, 40f, 48f, 56f, 64f, 72f, 80f,
    88f, 96f, 104f, 112f, 120f, 128f, 136f, 144f, 152f, 160f
)
