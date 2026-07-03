package com.naaammme.bbspace.feature.video

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.media3.common.util.UnstableApi
import com.naaammme.bbspace.core.model.PlaybackSource
import com.naaammme.bbspace.core.model.PlaybackStream
import com.naaammme.bbspace.core.model.PlayerSettingsState
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.core.model.VideoDownloadKind
import com.naaammme.bbspace.core.model.VideoDownloadOption
import com.naaammme.bbspace.core.model.VideoDownloadOptions
import com.naaammme.bbspace.core.model.VideoDownloadRequest
import com.naaammme.bbspace.feature.video.detail.VideoDetailPage
import com.naaammme.bbspace.feature.video.player.VideoPlayerPane
import java.util.Locale

internal val speedOps = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)

@Suppress("UnsafeOptInUsageError")
@UnstableApi
@Composable
fun VideoScreen(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onOpenDownloadCache: () -> Unit,
    onStartDownload: (VideoDownloadRequest) -> Unit,
    viewModel: VideoViewModel,
    hostExpanded: Boolean = true
) {
    val videoState by viewModel.videoState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle(initialValue = PlayerSettingsState())
    val act = LocalActivity.current
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isExpandedWidth = windowSizeClass.isWidthAtLeastBreakpoint(840)
    val themeUsesDarkSystemBarIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f
    var isFull by rememberSaveable { mutableStateOf(false) }
    var downloadSheetOn by rememberSaveable { mutableStateOf(false) }
    val fullOn = hostExpanded && isFull
    val isPortraitVideo = remember(videoState.currentStream) {
        val stream = videoState.currentStream ?: return@remember false
        (stream.width ?: 0) in 1..<(stream.height ?: 0)
    }
    val openTarget = remember(viewModel) {
        { target: VideoTarget -> viewModel.openTarget(target) }
    }
    val switchPage = remember(viewModel) {
        { cid: Long -> viewModel.switchPage(cid) }
    }
    val switchEpisode = remember(viewModel) {
        { target: VideoTarget -> viewModel.switchEpisode(target) }
    }
    val downloadClick = remember { { downloadSheetOn = true } }
    val handleBack = {
        if (fullOn) {
            isFull = false
        } else if (!viewModel.popPage()) {
            onBack()
        } else {
            Unit
        }
    }

    if (hostExpanded) {
        BackHandler {
            handleBack()
        }
    }

    LaunchedEffect(hostExpanded) {
        if (!hostExpanded) {
            downloadSheetOn = false
        }
    }

    if (act != null) {
        val win = act.window
        val ctrl = remember(win) { WindowInsetsControllerCompat(win, win.decorView) }
        val restoreLightSystemBars by rememberUpdatedState(themeUsesDarkSystemBarIcons)

        LaunchedEffect(ctrl, fullOn, isExpandedWidth, themeUsesDarkSystemBarIcons) {
            ctrl.isAppearanceLightStatusBars =
                !fullOn && isExpandedWidth && themeUsesDarkSystemBarIcons
            ctrl.isAppearanceLightNavigationBars = !fullOn && themeUsesDarkSystemBarIcons

            if (fullOn) {
                ctrl.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                ctrl.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        DisposableEffect(ctrl) {
            onDispose {
                ctrl.isAppearanceLightStatusBars = restoreLightSystemBars
                ctrl.isAppearanceLightNavigationBars = restoreLightSystemBars
                ctrl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(act, fullOn, settingsState.playback.autoRotateFullscreen, isPortraitVideo) {
        act?.requestedOrientation = if (
            fullOn &&
            settingsState.playback.autoRotateFullscreen &&
            !isPortraitVideo
        ) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val playerTopPad = 16.dp
        val playerGap = 16.dp
        val compactVideoH = maxWidth * (9f / 16f)
        val compactPlayerSpaceH = statusTop + compactVideoH
        val expandedContentW = (maxWidth - (playerTopPad * 2) - playerGap).coerceAtLeast(0.dp)
        val expandedPlayerW = expandedContentW * 0.54f
        val expandedPlayerH = (maxHeight - statusTop - (playerTopPad * 2)).coerceAtLeast(0.dp)

        when {
            !hostExpanded || fullOn -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    VideoPlayerPane(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = viewModel,
                        videoTitle = videoState.detail?.title,
                        isFull = fullOn,
                        onToggleFull = { isFull = !isFull },
                        onBackClick = handleBack,
                        onGoHome = onGoHome
                    )
                }
            }

            isExpandedWidth -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .padding(playerTopPad),
                    horizontalArrangement = Arrangement.spacedBy(playerGap),
                    verticalAlignment = androidx.compose.ui.Alignment.Top
                ) {
                    VideoPlayerPane(
                        modifier = Modifier
                            .width(expandedPlayerW)
                            .height(expandedPlayerH)
                            .clip(MaterialTheme.shapes.extraLarge),
                        viewModel = viewModel,
                        videoTitle = videoState.detail?.title,
                        isFull = false,
                        onToggleFull = { isFull = true },
                        onBackClick = handleBack,
                        onGoHome = onGoHome
                    )
                    VideoDetailPage(
                        modifier = Modifier.weight(1f),
                        detail = videoState.detail,
                        ids = videoState.ids,
                        detailLoading = videoState.detailLoading,
                        detailError = videoState.detailError,
                        commentSubject = viewModel.commentSubject,
                        contentHorizontalPad = 0.dp,
                        onOpenVideo = openTarget,
                        onOpenSpace = onOpenSpace,
                        onDownloadClick = downloadClick,
                        onOpenEpisode = switchEpisode,
                        onSwitchPage = switchPage
                    )
                }
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(compactPlayerSpaceH)
                            .background(Color.Black)
                    ) {
                        VideoPlayerPane(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .height(compactVideoH),
                            viewModel = viewModel,
                            videoTitle = videoState.detail?.title,
                            isFull = false,
                            onToggleFull = { isFull = true },
                            onBackClick = handleBack,
                            onGoHome = onGoHome
                        )
                    }
                    VideoDetailPage(
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.background),
                        detail = videoState.detail,
                        ids = videoState.ids,
                        detailLoading = videoState.detailLoading,
                        detailError = videoState.detailError,
                        commentSubject = viewModel.commentSubject,
                        contentHorizontalPad = 16.dp,
                        onOpenVideo = openTarget,
                        onOpenSpace = onOpenSpace,
                        onDownloadClick = downloadClick,
                        onOpenEpisode = switchEpisode,
                        onSwitchPage = switchPage
                    )
                }
            }
        }
    }

    if (hostExpanded && downloadSheetOn) {
        val sheetVideoQuality = videoState.currentStream?.quality ?: 80
        val sheetAudioQuality = videoState.currentAudio?.id ?: 0
        DownloadTaskSheet(
            currentVideoQuality = sheetVideoQuality,
            currentAudioQuality = sheetAudioQuality,
            canStartDownload = viewModel.currentDownloadRequest(
                kind = VideoDownloadKind.VIDEO,
                videoQuality = sheetVideoQuality,
                audioQuality = sheetAudioQuality
            ) != null,
            onDismiss = { downloadSheetOn = false },
            onOpenCache = {
                downloadSheetOn = false
                onOpenDownloadCache()
            },
            onStart = { kind, videoQuality, audioQuality ->
                val request = viewModel.currentDownloadRequest(
                    kind = kind,
                    videoQuality = videoQuality,
                    audioQuality = audioQuality
                ) ?: error("下载参数无效")
                onStartDownload(request)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DownloadTaskSheet(
    currentVideoQuality: Int,
    currentAudioQuality: Int,
    canStartDownload: Boolean,
    onDismiss: () -> Unit,
    onOpenCache: () -> Unit,
    onStart: (VideoDownloadKind, Int, Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var kind by rememberSaveable { mutableStateOf(VideoDownloadKind.VIDEO) }
    var videoQuality by rememberSaveable {
        mutableIntStateOf(currentVideoQuality.takeIf { it > 0 } ?: 80)
    }
    var audioQuality by rememberSaveable {
        mutableIntStateOf(currentAudioQuality.takeIf { it > 0 } ?: 0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("下载", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = kind == VideoDownloadKind.VIDEO,
                    onClick = { kind = VideoDownloadKind.VIDEO },
                    label = { Text("下载视频") }
                )
                FilterChip(
                    selected = kind == VideoDownloadKind.AUDIO,
                    onClick = { kind = VideoDownloadKind.AUDIO },
                    label = { Text("下载音频") }
                )
            }
            if (kind == VideoDownloadKind.VIDEO) {
                DownloadOptionGroup(
                    title = "画质",
                    options = VideoDownloadOptions.videoQualities,
                    selected = videoQuality,
                    onSelect = { videoQuality = it }
                )
            }
            DownloadOptionGroup(
                title = "音质",
                options = VideoDownloadOptions.audioQualities,
                selected = audioQuality,
                onSelect = { audioQuality = it }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenCache,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("查看缓存")
                }
                Button(
                    onClick = { onStart(kind, videoQuality, audioQuality) },
                    enabled = canStartDownload,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (kind == VideoDownloadKind.VIDEO) "下载视频" else "下载音频")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadOptionGroup(
    title: String,
    options: List<VideoDownloadOption>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                    label = { Text(option.label) }
                )
            }
        }
    }
}

internal fun formatDuration(ms: Long): String {
    val sec = ms / 1000
    val min = sec / 60
    val hour = min / 60
    return if (hour > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hour, min % 60, sec % 60)
    } else {
        String.format(Locale.ROOT, "%d:%02d", min, sec % 60)
    }
}

internal fun formatPlaybackTime(
    posMs: Long,
    durMs: Long
): String {
    return "${formatDuration(posMs)} / ${formatDuration(durMs)}"
}

internal fun formatSpeed(speed: Float): String {
    val num = if (speed % 1f == 0f) {
        String.format(Locale.ROOT, "%.1f", speed)
    } else {
        String.format(Locale.ROOT, "%.2f", speed).trimEnd('0').trimEnd('.')
    }
    return "${num}x"
}

internal fun getCodecName(codecId: Int): String {
    return when (codecId) {
        7 -> "AVC/H.264"
        12 -> "HEVC/H.265"
        13 -> "AV1"
        else -> "未知 $codecId"
    }
}

internal fun getAudioName(
    audioId: Int,
    short: Boolean = false
): String {
    return when (audioId) {
        30216 -> "64K"
        30232 -> "132K"
        30280 -> "192K"
        30250 -> if (short) "杜比" else "杜比全景声"
        30251 -> if (short) "无损" else "Hi-Res 无损"
        else -> if (short) "音频" else "音频 $audioId"
    }
}

internal fun getQualityName(
    source: PlaybackSource?,
    stream: PlaybackStream?
): String {
    source ?: return "画质"
    stream ?: return "画质"
    val option = source.qualityOptions.firstOrNull { it.quality == stream.quality }
    val label = option?.displayDescription?.takeIf(String::isNotBlank)
        ?: option?.description?.takeIf(String::isNotBlank)
        ?: stream.description.takeIf(String::isNotBlank)
        ?: "画质"
    return label.substringBefore(' ').ifBlank { label }
}


