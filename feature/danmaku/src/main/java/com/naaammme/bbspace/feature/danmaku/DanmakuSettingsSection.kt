package com.naaammme.bbspace.feature.danmaku

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.model.DanmakuConfig
import java.util.Locale

@Composable
fun DanmakuSettingsSection(
    config: DanmakuConfig,
    onConfigChange: (DanmakuConfig) -> Unit
) {
    SectionTitle("弹幕设置")

    ChoiceCard(
        title = "显示区域",
        subtitle = "控制滚动弹幕可使用的纵向区域",
        currentValue = config.areaPercent,
        options = listOf(25, 50, 75, 100),
        label = ::formatDanmakuArea,
        onSelect = { onConfigChange(config.copy(areaPercent = it)) }
    )

    SliderCard(
        title = "不透明度",
        subtitle = "调低后更不挡画面",
        value = config.opacity,
        valueRange = 0.1f..1f,
        steps = 8,
        valueLabel = ::formatPercent,
        onValueChangeCommitted = { onConfigChange(config.copy(opacity = it)) }
    )

    SliderCard(
        title = "字体大小",
        subtitle = "对原始弹幕字号做整体缩放",
        value = config.textScale,
        valueRange = 0.5f..2f,
        steps = 14,
        valueLabel = ::formatMultiple,
        onValueChangeCommitted = { onConfigChange(config.copy(textScale = it)) }
    )

    SliderCard(
        title = "弹幕速度",
        subtitle = "数值越大，弹幕滚动越快",
        value = config.speed,
        valueRange = 0.5f..2f,
        steps = 14,
        valueLabel = ::formatMultiple,
        onValueChangeCommitted = { onConfigChange(config.copy(speed = it)) }
    )

    ChoiceCard(
        title = "弹幕密度",
        subtitle = "控制同屏弹幕数量和防重叠策略",
        currentValue = config.densityLevel,
        options = listOf(0, 1, 2),
        label = ::formatDanmakuDensity,
        onSelect = { onConfigChange(config.copy(densityLevel = it)) }
    )

    SwitchCard(
        title = "重复弹幕合并",
        subtitle = "合并短时间内重复出现的相同内容",
        checked = config.mergeDuplicates,
        onCheckedChange = { onConfigChange(config.copy(mergeDuplicates = it)) }
    )

    SwitchCard(
        title = "滚动弹幕",
        subtitle = "控制右向左滚动弹幕显示",
        checked = config.showScrollRl,
        onCheckedChange = { onConfigChange(config.copy(showScrollRl = it)) }
    )

    SwitchCard(
        title = "顶部弹幕",
        subtitle = "固定显示在顶部",
        checked = config.showTop,
        onCheckedChange = { onConfigChange(config.copy(showTop = it)) }
    )

    SwitchCard(
        title = "底部弹幕",
        subtitle = "固定显示在底部",
        checked = config.showBottom,
        onCheckedChange = { onConfigChange(config.copy(showBottom = it)) }
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Padding.Horizontal, vertical = Padding.Vertical),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Padding.Inner)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SliderCard(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: (Float) -> String,
    onValueChangeCommitted: (Float) -> Unit
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Padding.Horizontal, vertical = Padding.Vertical),
            verticalArrangement = Arrangement.spacedBy(Padding.InnerLarge)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Padding.Inner)
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = valueLabel(sliderValue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onValueChangeCommitted(sliderValue) },
                valueRange = valueRange,
                steps = steps
            )
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String,
    currentValue: Int,
    options: List<Int>,
    label: (Int) -> String,
    onSelect: (Int) -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Padding.Horizontal, vertical = Padding.Vertical),
            verticalArrangement = Arrangement.spacedBy(Padding.InnerLarge)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Padding.InnerLarge)
            ) {
                options.forEach { option ->
                    FilterChip(
                        selected = option == currentValue,
                        onClick = { onSelect(option) },
                        label = { Text(label(option)) }
                    )
                }
            }
        }
    }
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

private fun formatPercent(value: Float): String {
    return "${(value * 100).toInt()}%"
}

private fun formatMultiple(value: Float): String {
    val normalized = if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
    }
    return "${normalized}x"
}

private object Padding {
    val Horizontal = 16.dp
    val Vertical = 16.dp
    val Inner = 4.dp
    val InnerLarge = 8.dp
}
