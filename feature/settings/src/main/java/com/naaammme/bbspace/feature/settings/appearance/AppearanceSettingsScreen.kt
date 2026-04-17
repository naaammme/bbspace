package com.naaammme.bbspace.feature.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.feature.settings.SettingsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.theme.AnimationSpeed
import com.naaammme.bbspace.core.designsystem.theme.CornerStyle
import com.naaammme.bbspace.core.designsystem.theme.PresetColors
import com.naaammme.bbspace.core.designsystem.theme.ThemeMode
import com.naaammme.bbspace.core.designsystem.theme.TransitionStyle
import com.naaammme.bbspace.feature.settings.components.SettingSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val config by viewModel.themeConfig.collectAsStateWithLifecycle()

    CollapsingTopBarScaffold(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = { Text("外观设计") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ThemeModeSelector(
                    selected = config.themeMode,
                    onSelect = viewModel::updateThemeMode
                )
            }

            item {
                SettingSwitch(
                    title = "动态取色",
                    subtitle = "Android 12+ 从壁纸提取颜色",
                    checked = config.useDynamicColor,
                    onCheckedChange = viewModel::updateUseDynamicColor
                )
            }

            item {
                ColorPaletteSelector(
                    selected = config.seedColor,
                    onSelect = viewModel::updateSeedColor
                )
            }

            item {
                SettingSwitch(
                    title = "纯色背景",
                    subtitle = "深色用纯黑，浅色用纯白",
                    checked = config.isPureBlack,
                    onCheckedChange = viewModel::updateIsPureBlack
                )
            }

            item {
                FontScaleSelector(
                    scale = config.fontScale,
                    onScaleChange = viewModel::updateFontScale
                )
            }

            item {
                AnimationSpeedSelector(
                    speed = config.animationSpeed,
                    onSelect = viewModel::updateAnimationSpeed
                )
            }

            item {
                CornerStyleSelector(
                    selected = config.cornerStyle,
                    onSelect = viewModel::updateCornerStyle
                )
            }

            item {
                TransitionStyleSelector(
                    selected = config.transitionStyle,
                    onSelect = viewModel::updateTransitionStyle
                )
            }
        }
    }
}

@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("主题模式", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = selected == mode,
                        onClick = { onSelect(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.LIGHT -> "浅色"
                                    ThemeMode.DARK -> "深色"
                                    ThemeMode.SYSTEM -> "跟随系统"
                                },
                                maxLines = 1
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorPaletteSelector(
    selected: Color,
    onSelect: (Color) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("主题色", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PresetColors.forEach { (color, _) ->
                    ColorItem(
                        color = color,
                        selected = color == selected,
                        onClick = { onSelect(color) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorItem(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private val FONT_SCALES = listOf(0.8f, 0.85f, 0.9f, 0.95f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f)

@Composable
private fun FontScaleSelector(
    scale: Float,
    onScaleChange: (Float) -> Unit
) {
    val idx = remember(scale) {
        FONT_SCALES.indexOfFirst { kotlin.math.abs(it - scale) < 0.01f }
            .coerceAtLeast(0)
    }
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("字体大小", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${(FONT_SCALES[idx] * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = idx.toFloat(),
                onValueChange = { onScaleChange(FONT_SCALES[it.toInt()]) },
                valueRange = 0f..(FONT_SCALES.size - 1).toFloat(),
                steps = FONT_SCALES.size - 2
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("小", style = MaterialTheme.typography.bodySmall)
                Text("标准", style = MaterialTheme.typography.bodySmall)
                Text("大", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TransitionStyleSelector(
    selected: TransitionStyle,
    onSelect: (TransitionStyle) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("过渡动画", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            TransitionStyle.entries.forEach { style ->
                FilterChip(
                    modifier = Modifier.fillMaxWidth(),
                    selected = selected == style,
                    onClick = { onSelect(style) },
                    label = {
                        Text(
                            when (style) {
                                TransitionStyle.SHARED_AXIS_X -> "水平滑动"
                                TransitionStyle.SHARED_AXIS_Y -> "垂直滑动"
                                TransitionStyle.SHARED_AXIS_Z -> "缩放"
                                TransitionStyle.FADE_THROUGH -> "淡入淡出"
                                TransitionStyle.SLIDE -> "滑动"
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AnimationSpeedSelector(
    speed: AnimationSpeed,
    onSelect: (AnimationSpeed) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("动画速度", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimationSpeed.entries.forEach { s ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = speed == s,
                        onClick = { onSelect(s) },
                        label = {
                            Text(
                                when (s) {
                                    AnimationSpeed.OFF -> "关闭"
                                    AnimationSpeed.FAST -> "快速"
                                    AnimationSpeed.NORMAL -> "标准"
                                    AnimationSpeed.SLOW -> "慢速"
                                },
                                maxLines = 1
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CornerStyleSelector(
    selected: CornerStyle,
    onSelect: (CornerStyle) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("圆角风格", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CornerStyle.entries.forEach { s ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = selected == s,
                        onClick = { onSelect(s) },
                        label = {
                            Text(
                                when (s) {
                                    CornerStyle.SQUARE -> "直角"
                                    CornerStyle.STANDARD -> "标准"
                                    CornerStyle.ROUNDED -> "圆润"
                                    CornerStyle.CIRCULAR -> "圆形"
                                },
                                maxLines = 1
                            )
                        }
                    )
                }
            }
        }
    }
}
