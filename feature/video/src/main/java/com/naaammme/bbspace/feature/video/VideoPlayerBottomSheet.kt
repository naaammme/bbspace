package com.naaammme.bbspace.feature.video

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.model.PlaybackError
import com.naaammme.bbspace.core.model.PlaybackState
import com.naaammme.bbspace.core.model.PlaybackViewState
import com.naaammme.bbspace.core.model.VideoPlaybackSettingsState
import com.naaammme.bbspace.core.model.buildPlaybackCdns
import com.naaammme.bbspace.feature.video.model.VideoViewModel
import java.util.Locale

private enum class PlayerSheetSection(
    val title: String
) {
    Info("视频信息"),
    Playback("播放设置"),
    Danmaku("弹幕设置")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoPlayerBottomSheet(
    state: PlaybackViewState,
    viewModel: VideoViewModel,
    limitUnderPlayer: Boolean,
    onDismiss: () -> Unit
) {
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var section by rememberSaveable { mutableStateOf(PlayerSheetSection.Info) }
    val configuration = LocalConfiguration.current
    val shouldLimitHeight = limitUnderPlayer && configuration.screenHeightDp > configuration.screenWidthDp
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val maxContentHeight = remember(
        shouldLimitHeight,
        configuration.screenHeightDp,
        configuration.screenWidthDp,
        statusBarHeight
    ) {
        if (shouldLimitHeight) {
            val screenHeight = configuration.screenHeightDp.dp
            val screenWidth = configuration.screenWidthDp.dp
            val playerHeight = statusBarHeight + (screenWidth * (9f / 16f))
            val sheetTopPadding = 24.dp
            (screenHeight - playerHeight - sheetTopPadding).coerceAtLeast(240.dp)
        } else {
            Dp.Unspecified
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (shouldLimitHeight && maxContentHeight != Dp.Unspecified) {
                        Modifier.heightIn(max = maxContentHeight)
                    } else {
                        Modifier
                    }
                )
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlayerSheetSection.entries.forEach { item ->
                    FilterChip(
                        selected = item == section,
                        onClick = { section = item },
                        label = { Text(item.title) }
                    )
                }
            }

            when (section) {
                PlayerSheetSection.Info -> PlayerInfoSection(state)
                PlayerSheetSection.Playback -> PlaybackSettingsSection(
                    state = state,
                    settingsState = settingsState,
                    viewModel = viewModel
                )

                PlayerSheetSection.Danmaku -> DanmakuSettingsSection(
                    settingsState = settingsState,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun PlaybackSettingsSection(
    state: PlaybackViewState,
    settingsState: VideoPlaybackSettingsState,
    viewModel: VideoViewModel
) {
    SheetSectionTitle("播放设置")

    val cdnOps = buildCdnOps(state.currentStream, state.currentAudio)
    if (cdnOps.size > 1) {
        SheetChoiceCard(
            title = "CDN 选择",
            subtitle = "默认备用1, 点击切换",
            currentValue = state.cdnIndex.coerceIn(0, cdnOps.lastIndex),
            options = cdnOps.indices.toList(),
            label = { cdnOps[it] },
            onSelect = viewModel::switchCdn
        )
    }

    SheetChoiceCard(
        title = "最小缓冲时长",
        subtitle = "持续补缓冲的最低时长",
        currentValue = settingsState.buffer.minBufferMs,
        options = listOf(2_000, 5_000, 10_000, 15_000, 30_000, 60_000),
        label = ::formatBufferMs,
        onSelect = viewModel::updateMinBufferMs
    )

    SheetChoiceCard(
        title = "最大缓冲时长",
        subtitle = "播放器最多预读多久",
        currentValue = settingsState.buffer.maxBufferMs,
        options = listOf(15_000, 30_000, 60_000, 90_000, 120_000),
        label = ::formatBufferMs,
        onSelect = viewModel::updateMaxBufferMs
    )

    SheetChoiceCard(
        title = "起播缓冲时长",
        subtitle = "开始播放前至少缓冲多久",
        currentValue = settingsState.buffer.playbackBufferMs,
        options = listOf(50, 100, 150, 250, 400, 600, 800, 1_000),
        label = ::formatBufferMs,
        onSelect = viewModel::updatePlaybackBufferMs
    )

    SheetChoiceCard(
        title = "重缓冲恢复时长",
        subtitle = "卡顿后恢复播放前至少缓冲多久",
        currentValue = settingsState.buffer.rebufferMs,
        options = listOf(100, 250, 400, 500, 750, 1_000, 1_500),
        label = ::formatBufferMs,
        onSelect = viewModel::updateRebufferMs
    )

    SheetChoiceCard(
        title = "回看缓冲时长",
        subtitle = "保留已播内容用于回退拖动",
        currentValue = settingsState.buffer.backBufferMs,
        options = listOf(0, 5_000, 10_000, 15_000, 30_000),
        label = ::formatBufferMs,
        onSelect = viewModel::updateBackBufferMs
    )

    SheetSwitchCard(
        title = "后台播放",
        subtitle = "当前阶段未接入后台播放宿主，暂不生效",
        checked = settingsState.playback.backgroundPlayback,
        onCheckedChange = viewModel::updateBackgroundPlayback
    )

    SheetSwitchCard(
        title = "软解优先",
        subtitle = "优先使用软件解码",
        checked = settingsState.playback.preferSoftwareDecode,
        onCheckedChange = viewModel::updatePreferSoftwareDecode
    )

    SheetSwitchCard(
        title = "解码失败自动回退",
        subtitle = "允许切换到低优先级解码器",
        checked = settingsState.playback.decoderFallback,
        onCheckedChange = viewModel::updateDecoderFallback
    )
}

@Composable
private fun DanmakuSettingsSection(
    settingsState: VideoPlaybackSettingsState,
    viewModel: VideoViewModel
) {
    SheetSectionTitle("弹幕设置")

    SheetChoiceCard(
        title = "显示区域",
        subtitle = "控制滚动弹幕可使用的纵向区域",
        currentValue = settingsState.danmaku.areaPercent,
        options = listOf(25, 50, 75, 100),
        label = ::formatDanmakuArea,
        onSelect = viewModel::updateDanmakuAreaPercent
    )

    SheetSliderCard(
        title = "不透明度",
        subtitle = "调低后更不挡画面",
        value = settingsState.danmaku.opacity,
        valueRange = 0.1f..1f,
        steps = 8,
        valueLabel = ::formatPercent,
        onValueChangeCommitted = viewModel::updateDanmakuOpacity
    )

    SheetSliderCard(
        title = "字体大小",
        subtitle = "对原始弹幕字号做整体缩放",
        value = settingsState.danmaku.textScale,
        valueRange = 0.5f..2f,
        steps = 14,
        valueLabel = ::formatMultiple,
        onValueChangeCommitted = viewModel::updateDanmakuTextScale
    )

    SheetSliderCard(
        title = "弹幕速度",
        subtitle = "数值越大，弹幕滚动越快",
        value = settingsState.danmaku.speed,
        valueRange = 0.5f..2f,
        steps = 14,
        valueLabel = ::formatMultiple,
        onValueChangeCommitted = viewModel::updateDanmakuSpeed
    )

    SheetChoiceCard(
        title = "弹幕密度",
        subtitle = "控制同屏弹幕数量和防重叠策略",
        currentValue = settingsState.danmaku.densityLevel,
        options = listOf(0, 1, 2),
        label = ::formatDanmakuDensity,
        onSelect = viewModel::updateDanmakuDensity
    )

    SheetSwitchCard(
        title = "重复弹幕合并",
        subtitle = "合并短时间内重复出现的相同内容",
        checked = settingsState.danmaku.mergeDuplicates,
        onCheckedChange = viewModel::updateDanmakuMergeDuplicates
    )

    SheetSwitchCard(
        title = "滚动弹幕",
        subtitle = "控制右向左滚动弹幕显示",
        checked = settingsState.danmaku.showScrollRl,
        onCheckedChange = viewModel::updateDanmakuShowScrollRl
    )

    SheetSwitchCard(
        title = "顶部弹幕",
        subtitle = "固定显示在顶部",
        checked = settingsState.danmaku.showTop,
        onCheckedChange = viewModel::updateDanmakuShowTop
    )

    SheetSwitchCard(
        title = "底部弹幕",
        subtitle = "固定显示在底部",
        checked = settingsState.danmaku.showBottom,
        onCheckedChange = viewModel::updateDanmakuShowBottom
    )
}

@Composable
private fun PlayerInfoSection(state: PlaybackViewState) {
    SheetSectionTitle("视频信息")

    state.error?.let { err ->
        SheetInfoGroup(
            title = "请求错误",
            rows = listOf("错误" to playbackErrorText(err))
        )
    }

    state.playerError?.let { msg ->
        SheetInfoGroup(
            title = "播放器错误",
            rows = listOf("错误" to msg)
        )
    }

    val src = state.playbackSource
    if (src == null) {
        Card {
            Text(
                text = if (state.isPreparing) "正在加载播放信息" else "暂无播放信息",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    val stream = state.currentStream
    val audio = state.currentAudio

    SheetInfoGroup(
        title = "视频",
        rows = buildList {
            add("AV号" to "av${src.videoId.aid}")
            add("CID" to src.videoId.cid.toString())
            add("时长" to formatDuration(src.durationMs))
            stream?.let {
                add(
                    "分辨率" to listOfNotNull(it.width, it.height)
                        .joinToString("x")
                        .ifBlank { "未知" }
                )
                add("画质" to getQualityName(src, it))
                if (it is com.naaammme.bbspace.core.model.PlaybackStream.Dash) {
                    add("帧率" to (it.frameRate ?: "未知"))
                    add("编码" to getCodecName(it.codecId))
                    add("带宽" to "${it.bandwidth / 1000} kbps")
                }
            }
        }
    )

    audio?.let {
        SheetInfoGroup(
            title = "音频",
            rows = listOf(
                "音频ID" to it.id.toString(),
                "音频名称" to getAudioName(it.id),
                "音频带宽" to "${it.bandwidth / 1000} kbps"
            )
        )
    }

    SheetInfoGroup(
        title = "播放状态",
        rows = listOf(
            "状态" to playbackStateText(state),
            "播放位置" to formatDuration(state.positionMs),
            "缓冲位置" to formatDuration(state.bufferedPositionMs),
            "缓冲时长" to formatDuration(state.totalBufferedDurationMs),
            "播放速度" to formatSpeed(state.speed)
        )
    )
}

@Composable
private fun SheetSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SheetSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
private fun SheetSliderCard(
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
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
private fun SheetChoiceCard(
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

@Composable
private fun SheetInfoGroup(
    title: String,
    rows: List<Pair<String, String>>
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.36f)
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(0.64f)
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

private fun formatBufferMs(value: Int): String {
    return when {
        value == 0 -> "关闭"
        value < 1_000 -> "${value}ms"
        value % 1_000 == 0 -> "${value / 1_000}s"
        else -> "${value / 1_000f}s"
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

private fun playbackStateText(state: PlaybackViewState): String {
    return when {
        state.isPlaying -> "播放中"
        state.isPreparing -> "准备中"
        else -> when (state.playbackState) {
            PlaybackState.Buffering -> "缓冲中"
            PlaybackState.Ready -> "已暂停"
            PlaybackState.Ended -> "已结束"
            PlaybackState.Idle -> "未开始"
        }
    }
}

private fun playbackErrorText(err: PlaybackError): String {
    return when (err) {
        is PlaybackError.NoPlayableStream -> err.message
        is PlaybackError.RequestFailed -> err.message
    }
}

private fun buildCdnOps(
    stream: com.naaammme.bbspace.core.model.PlaybackStream?,
    audio: com.naaammme.bbspace.core.model.PlaybackAudio?
): List<String> {
    return buildPlaybackCdns(stream, audio).map { it.label }
}
