package com.naaammme.bbspace.feature.video.player

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.DanmakuSettingsSection
import com.naaammme.bbspace.core.model.PlaybackError
import com.naaammme.bbspace.core.model.PlayerBufferProfile
import com.naaammme.bbspace.core.model.PlayerSettingsState
import com.naaammme.bbspace.core.model.VideoPlaybackState
import com.naaammme.bbspace.core.model.buildPlaybackCdns
import com.naaammme.bbspace.feature.video.VideoViewModel
import com.naaammme.bbspace.feature.video.formatDuration
import com.naaammme.bbspace.feature.video.formatSpeed
import com.naaammme.bbspace.feature.video.getAudioName
import com.naaammme.bbspace.feature.video.getCodecName
import com.naaammme.bbspace.feature.video.getQualityName
import com.naaammme.bbspace.feature.video.speedOps
import kotlin.math.roundToInt

private enum class PlaybackSheetSection(
    val title: String
) {
    Info("视频信息"),
    Playback("播放设置"),
    Danmaku("弹幕设置")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoPlaybackSheet(
    state: VideoPlaybackState,
    viewModel: VideoViewModel,
    limitUnderPlayer: Boolean,
    onDismiss: () -> Unit
) {
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle(initialValue = PlayerSettingsState())
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var section by rememberSaveable { mutableStateOf(PlaybackSheetSection.Info) }
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val shouldLimitHeight = limitUnderPlayer && windowInfo.containerSize.height > windowInfo.containerSize.width
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val maxContentHeight = remember(shouldLimitHeight, windowInfo.containerSize, statusBarHeight, density) {
        if (shouldLimitHeight) {
            val screenHeight = with(density) { windowInfo.containerSize.height.toDp() }
            val screenWidth = with(density) { windowInfo.containerSize.width.toDp() }
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
        VideoPlaybackPanelContent(
            state = state,
            settingsState = settingsState,
            viewModel = viewModel,
            section = section,
            onSectionChange = { section = it },
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (shouldLimitHeight && maxContentHeight != Dp.Unspecified) {
                        Modifier.heightIn(max = maxContentHeight)
                    } else {
                        Modifier
                    }
                )
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
internal fun VideoPlaybackSidebar(
    state: VideoPlaybackState,
    viewModel: VideoViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle(initialValue = PlayerSettingsState())
    var section by rememberSaveable { mutableStateOf(PlaybackSheetSection.Playback) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val panelWidth = (maxWidth * 0.42f).coerceIn(240.dp, 360.dp)
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = onDismiss)
            )
            Card(
                modifier = Modifier
                    .width(panelWidth)
                    .fillMaxHeight()
            ) {
                VideoPlaybackPanelContent(
                    state = state,
                    settingsState = settingsState,
                    viewModel = viewModel,
                    section = section,
                    onSectionChange = { section = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoPlaybackPanelContent(
    state: VideoPlaybackState,
    settingsState: PlayerSettingsState,
    viewModel: VideoViewModel,
    section: PlaybackSheetSection,
    onSectionChange: (PlaybackSheetSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlaybackSheetSection.entries.forEach { item ->
                FilterChip(
                    selected = item == section,
                    onClick = { onSectionChange(item) },
                    label = { Text(item.title) }
                )
            }
        }

        when (section) {
            PlaybackSheetSection.Info -> PlayerInfoSection(state)
            PlaybackSheetSection.Playback -> PlaybackSettingsSection(
                state = state,
                settingsState = settingsState,
                viewModel = viewModel
            )

            PlaybackSheetSection.Danmaku -> DanmakuSettingsSection(
                config = settingsState.danmaku,
                onConfigChange = viewModel::updateDanmaku
            )
        }
    }
}

@Composable
private fun PlaybackSettingsSection(
    state: VideoPlaybackState,
    settingsState: PlayerSettingsState,
    viewModel: VideoViewModel
) {
    val videoResizeModeState = LocalVideoResizeModeState.current
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
        title = "缓冲策略",
        subtitle = "影响缓冲时长，时长短起播更快但弱网和倍速可能卡顿，时长长则相反",
        currentValue = settingsState.buffer.profile.ordinal,
        options = PlayerBufferProfile.entries.indices.toList(),
        label = { bufferProfileText(PlayerBufferProfile.entries[it]) },
        onSelect = { viewModel.updateBufferProfile(PlayerBufferProfile.entries[it]) }
    )

    SheetChoiceCard(
        title = "长按倍速",
        subtitle = "长按屏幕时临时切到这个倍速，松手恢复",
        currentValue = settingsState.playback.gestureSpeed,
        options = speedOps,
        onSelect = viewModel::updateGestureSpeed
    )

    SheetChoiceCard(
        title = "全屏视频大小",
        subtitle = "调整画面尺寸",
        currentValue = videoResizeModeState.value.ordinal,
        options = PlayerVideoResizeMode.entries.indices.toList(),
        label = { videoResizeModeText(PlayerVideoResizeMode.entries[it]) },
        onSelect = { videoResizeModeState.value = PlayerVideoResizeMode.entries[it] }
    )

    SheetSwitchCard(
        title = "后台播放",
        subtitle = "退出页面或切到后台后继续播放，并显示系统通知",
        checked = settingsState.playback.backgroundPlayback,
        onCheckedChange = viewModel::updateBackgroundPlayback
    )

    SheetSwitchCard(
        title = "应用内小窗",
        subtitle = "允许把视频和直播缩成应用内小窗继续播放",
        checked = settingsState.playback.inAppMiniPlayer,
        onCheckedChange = viewModel::updateInAppMiniPlayer
    )

    SheetSwitchCard(
        title = "播放行为上报",
        subtitle = "向服务端上报播放心跳和历史，关闭影响个性化推荐和历史记录",
        checked = settingsState.playback.reportPlayback,
        onCheckedChange = viewModel::updateReportPlayback
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

    SheetSwitchCard(
        title = "全屏自动横屏",
        subtitle = "点击全屏按钮时自动强制横屏",
        checked = settingsState.playback.autoRotateFullscreen,
        onCheckedChange = viewModel::updateAutoRotateFullscreen
    )
}

@Composable
private fun PlayerInfoSection(state: VideoPlaybackState) {
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
        title = "解码器",
        rows = listOf(
            "视频解码器" to (state.videoDecoderName ?: "未知"),
            "音频解码器" to (state.audioDecoderName ?: "未知")
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
private fun SheetChoiceCard(
    title: String,
    subtitle: String,
    currentValue: Float,
    options: List<Float>,
    onSelect: (Float) -> Unit
) {
    val idx = options.indexOfFirst { it == currentValue }.coerceAtLeast(0)
    var dragIdx by remember { mutableStateOf<Int?>(null) }
    val displayIdx = dragIdx ?: idx
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
            Text(
                text = formatSpeed(options[displayIdx]),
                style = MaterialTheme.typography.titleMedium
            )

            Slider(
                value = displayIdx.toFloat(),
                onValueChange = { raw ->
                    dragIdx = raw.roundToInt().coerceIn(0, options.lastIndex)
                },
                onValueChangeFinished = {
                    val finalIdx = dragIdx ?: return@Slider
                    onSelect(options[finalIdx])
                    dragIdx = null
                },
                valueRange = 0f..options.lastIndex.toFloat(),
                steps = (options.size - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatSpeed(options.first()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatSpeed(options.last()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

private fun bufferProfileText(value: PlayerBufferProfile): String {
    return when (value) {
        PlayerBufferProfile.FastStart -> "快速起播"
        PlayerBufferProfile.Balanced -> "均衡模式"
        PlayerBufferProfile.Stable -> "稳定优先"
    }
}

private fun videoResizeModeText(value: PlayerVideoResizeMode): String {
    return when (value) {
        PlayerVideoResizeMode.Fit -> "适应"
        PlayerVideoResizeMode.Zoom -> "裁剪铺满"
        PlayerVideoResizeMode.Fill -> "拉伸铺满"
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
