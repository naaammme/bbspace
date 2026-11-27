package com.naaammme.bbspace.core.designsystem.component

import androidx.compose.runtime.Composable
import com.naaammme.bbspace.core.model.DanmakuConfig
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DanmakuSettingsSection(
    config: DanmakuConfig,
    onUpdate: ((DanmakuConfig) -> DanmakuConfig) -> Unit
) {
    SettingSectionHeader("弹幕设置")

    SettingChoiceCard(
        title = "显示区域",
        subtitle = "控制滚动弹幕可使用的纵向区域",
        currentValue = config.areaPercent,
        options = listOf(25, 50, 75, 100),
        label = ::formatDanmakuArea,
        onSelect = { area -> onUpdate { it.copy(areaPercent = area) } }
    )

    SettingSliderCard(
        title = "不透明度",
        subtitle = "调低后更不挡画面",
        value = config.opacity,
        valueRange = 0.1f..1f,
        steps = 8,
        valueLabel = ::formatPercent,
        onValueChangeCommitted = { opacity -> onUpdate { it.copy(opacity = opacity) } }
    )

    SettingSliderCard(
        title = "字体大小",
        subtitle = "对原始弹幕字号做整体缩放",
        value = config.textScale,
        valueRange = 0.5f..2f,
        steps = 14,
        valueLabel = ::formatMultiple,
        onValueChangeCommitted = { scale -> onUpdate { it.copy(textScale = scale) } }
    )

    SettingSliderCard(
        title = "弹幕速度",
        subtitle = "数值越大，弹幕滚动越快",
        value = config.speed,
        valueRange = 0.5f..2f,
        steps = 14,
        valueLabel = ::formatMultiple,
        onValueChangeCommitted = { speed -> onUpdate { it.copy(speed = speed) } }
    )

    SettingChoiceCard(
        title = "弹幕密度",
        subtitle = "控制同屏弹幕数量和防重叠策略",
        currentValue = config.densityLevel,
        options = listOf(0, 1, 2),
        label = ::formatDanmakuDensity,
        onSelect = { level -> onUpdate { it.copy(densityLevel = level) } }
    )

    SettingSliderCard(
        title = "智能云屏蔽",
        subtitle = "过滤低于当前权重等级的弹幕",
        value = config.weightFilterLevel.toFloat(),
        valueRange = 0f..10f,
        steps = 9,
        valueLabel = ::formatDanmakuWeightLevel,
        onValueChangeCommitted = { level ->
            onUpdate { it.copy(weightFilterLevel = level.roundToInt()) }
        }
    )

    SettingSwitchCard(
        title = "重复弹幕合并",
        subtitle = "合并短时间内重复出现的相同内容",
        checked = config.mergeDuplicates,
        onCheckedChange = { merge -> onUpdate { it.copy(mergeDuplicates = merge) } }
    )

    SettingSwitchCard(
        title = "滚动弹幕",
        subtitle = "控制右向左滚动弹幕显示",
        checked = config.showScrollRl,
        onCheckedChange = { show -> onUpdate { it.copy(showScrollRl = show) } }
    )

    SettingSwitchCard(
        title = "顶部弹幕",
        subtitle = "固定显示在顶部",
        checked = config.showTop,
        onCheckedChange = { show -> onUpdate { it.copy(showTop = show) } }
    )

    SettingSwitchCard(
        title = "底部弹幕",
        subtitle = "固定显示在底部",
        checked = config.showBottom,
        onCheckedChange = { show -> onUpdate { it.copy(showBottom = show) } }
    )
}

private fun formatDanmakuArea(value: Int): String {
    return when (value) {
        25 -> "1/4 屏"
        50 -> "半屏"
        75 -> "3/4 屏"
        else -> "满屏"
    }
}

private fun formatDanmakuDensity(value: Int): String {
    return when (value) {
        0 -> "稀疏"
        2 -> "密集"
        else -> "标准"
    }
}

private fun formatDanmakuWeightLevel(value: Float): String {
    return "${value.roundToInt().coerceIn(0, 10)}级"
}

private fun formatPercent(value: Float): String {
    return "${(value * 100).roundToInt()}%"
}

private fun formatMultiple(value: Float): String {
    val normalized = if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
    }
    return "${normalized}x"
}
