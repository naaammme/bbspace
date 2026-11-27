package com.naaammme.bbspace.feature.video.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.DanmakuSettingsSection
import com.naaammme.bbspace.core.designsystem.component.SettingChoiceCard
import com.naaammme.bbspace.core.designsystem.component.SettingDialogCard
import com.naaammme.bbspace.core.designsystem.component.SettingDiscreteSliderCard
import com.naaammme.bbspace.core.designsystem.component.SettingSectionHeader
import com.naaammme.bbspace.core.designsystem.component.SettingSliderCard
import com.naaammme.bbspace.core.designsystem.component.SettingSwitchCard
import com.naaammme.bbspace.core.designsystem.component.bindSetting
import com.naaammme.bbspace.core.model.PlaybackError
import com.naaammme.bbspace.core.model.PlayerBufferProfile
import com.naaammme.bbspace.core.model.ResolvedVideoIds
import com.naaammme.bbspace.core.model.VideoCdnMode
import com.naaammme.bbspace.core.model.VideoPlaybackState
import com.naaammme.bbspace.core.settings.PlaybackPrefs
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
    videoResizeMode: PlayerVideoResizeMode,
    onVideoResizeModeChange: (PlayerVideoResizeMode) -> Unit,
    limitUnderPlayer: Boolean,
    onDismiss: () -> Unit
) {
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
            ids = state.ids,
            viewModel = viewModel,
            videoResizeMode = videoResizeMode,
            onVideoResizeModeChange = onVideoResizeModeChange,
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
    videoResizeMode: PlayerVideoResizeMode,
    onVideoResizeModeChange: (PlayerVideoResizeMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    ids = state.ids,
                    viewModel = viewModel,
                    videoResizeMode = videoResizeMode,
                    onVideoResizeModeChange = onVideoResizeModeChange,
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
    ids: ResolvedVideoIds,
    viewModel: VideoViewModel,
    videoResizeMode: PlayerVideoResizeMode,
    onVideoResizeModeChange: (PlayerVideoResizeMode) -> Unit,
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
            PlaybackSheetSection.Info -> PlayerInfoSection(state, ids)
            PlaybackSheetSection.Playback -> PlaybackSettingsSection(
                prefs = viewModel.playbackPrefs,
                videoResizeMode = videoResizeMode,
                onVideoResizeModeChange = onVideoResizeModeChange
            )
            PlaybackSheetSection.Danmaku -> {
                val danmakuConfig by viewModel.danmakuConfig.collectAsStateWithLifecycle()
                DanmakuSettingsSection(
                    config = danmakuConfig,
                    onUpdate = viewModel::updateDanmaku
                )
            }
        }
    }
}

@Composable
private fun PlaybackSettingsSection(
    prefs: PlaybackPrefs,
    videoResizeMode: PlayerVideoResizeMode,
    onVideoResizeModeChange: (PlayerVideoResizeMode) -> Unit
) {
    SettingSectionHeader("播放设置")

    val cdnSetting = prefs.videoCdnMode
    val (cdnMode, setCdnMode) = cdnSetting.flow.bindSetting(cdnSetting.current, cdnSetting::commit)
    SettingDialogCard(
        title = "CDN 偏好",
        dialogTitle = "选择 CDN",
        subtitle = "点击切换播放节点",
        currentValue = cdnMode,
        options = VideoCdnMode.entries,
        label = VideoCdnMode::label,
        description = VideoCdnMode::description,
        keyOf = VideoCdnMode::name,
        onSelect = setCdnMode,
        dismissText = "取消"
    )

    val bufferSetting = prefs.bufferProfile
    val (bufferProfile, setBufferProfile) = bufferSetting.flow.bindSetting(bufferSetting.current, bufferSetting::commit)
    SettingChoiceCard(
        title = "缓冲策略",
        subtitle = "影响缓冲时长，时长短起播更快但弱网和倍速可能卡顿，时长长则相反",
        currentValue = bufferProfile,
        options = PlayerBufferProfile.entries,
        label = ::bufferProfileText,
        onSelect = setBufferProfile
    )

    val speedSetting = prefs.gestureSpeed
    val (gestureSpeed, setGestureSpeed) = speedSetting.flow.bindSetting(speedSetting.current, speedSetting::commit)
    SettingDiscreteSliderCard(
        title = "长按倍速",
        subtitle = "长按屏幕时临时切到这个倍速，松手恢复",
        currentValue = gestureSpeed,
        values = speedOps,
        valueLabel = ::formatSpeed,
        onValueSelected = setGestureSpeed,
        minLabel = formatSpeed(speedOps.first()),
        maxLabel = formatSpeed(speedOps.last())
    )

    val doubleTapSeekSetting = prefs.doubleTapSeekSec
    val (doubleTapSeekSec, setDoubleTapSeekSec) = doubleTapSeekSetting.flow.bindSetting(
        doubleTapSeekSetting.current,
        doubleTapSeekSetting::commit
    )
    SettingSliderCard(
        title = "双击快进/快退",
        subtitle = "双击屏幕左右两侧时跳转的秒数",
        value = doubleTapSeekSec.toFloat(),
        valueRange = 1f..20f,
        steps = 18,
        valueLabel = { "${it.roundToInt()}秒" },
        onValueChangeCommitted = { setDoubleTapSeekSec(it.roundToInt()) },
        minLabel = "1秒",
        maxLabel = "20秒"
    )

    SettingChoiceCard(
        title = "全屏视频大小",
        subtitle = "调整画面尺寸",
        currentValue = videoResizeMode,
        options = PlayerVideoResizeMode.entries,
        label = ::videoResizeModeText,
        onSelect = onVideoResizeModeChange
    )

    val switches = remember(prefs) {
        listOf(
            Triple("后台播放", "退出页面或切到后台后继续播放，并显示系统通知", prefs.backgroundPlayback),
            Triple("应用内小窗", "允许把视频和直播缩成应用内小窗继续播放", prefs.inAppMiniPlayer),
            Triple("播放行为上报", "向服务端上报播放心跳和历史，关闭影响个性化推荐和历史记录", prefs.reportPlayback),
            Triple("软解优先", "优先使用软件解码", prefs.preferSoftwareDecode),
            Triple("解码失败自动回退", "允许切换到低优先级解码器", prefs.decoderFallback),
            Triple("全屏自动横屏", "点击全屏按钮时自动强制横屏", prefs.autoRotateFullscreen)
        )
    }
    switches.forEach { (title, subtitle, setting) ->
        val (checked, setChecked) = setting.flow.bindSetting(setting.current, setting::commit)
        SettingSwitchCard(
            title = title,
            subtitle = subtitle,
            checked = checked,
            onCheckedChange = setChecked
        )
    }
}

@Composable
private fun PlayerInfoSection(
    state: VideoPlaybackState,
    ids: ResolvedVideoIds
) {
    SettingSectionHeader("视频信息")

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
            ids.aid.takeIf { it > 0L }?.let { add("AV号" to "av$it") }
            ids.cid.takeIf { it > 0L }?.let { add("CID" to it.toString()) }
            ids.epId.takeIf { it > 0L }?.let { add("EPID" to it.toString()) }
            ids.seasonId.takeIf { it > 0L }?.let { add("SSID" to it.toString()) }
            ids.bvid?.takeIf(String::isNotBlank)?.let { add("BVID" to it) }
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
                    horizontalArrangement = Arrangement.SpaceBetween
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
