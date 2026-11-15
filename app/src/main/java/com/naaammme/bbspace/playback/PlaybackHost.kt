package com.naaammme.bbspace.playback

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition.Companion.None as EnterNone
import androidx.compose.animation.ExitTransition.Companion.None as ExitNone
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import com.naaammme.bbspace.core.model.VideoDownloadRequest
import com.naaammme.bbspace.feature.live.LiveScreen
import com.naaammme.bbspace.feature.live.LiveViewModel
import com.naaammme.bbspace.feature.video.VideoScreen
import com.naaammme.bbspace.feature.video.VideoViewModel

@OptIn(UnstableApi::class)
@Composable
fun PlaybackHost(
    mode: PlaybackHostMode,
    playbackHostViewModel: PlaybackHostViewModel,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit,
    onDismissExpanded: () -> Unit,
    onGoHome: () -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onOpenDownloadCache: () -> Unit,
    onStartDownload: (VideoDownloadRequest) -> Unit,
    videoViewModel: VideoViewModel,
    liveViewModel: LiveViewModel,
    modifier: Modifier = Modifier
) {
    val procOwner = remember { ProcessLifecycleOwner.get() }
    val player by playbackHostViewModel.player.collectAsStateWithLifecycle()
    val target by playbackHostViewModel.currentTarget.collectAsStateWithLifecycle()
    val sessionState by playbackHostViewModel.sessionState.collectAsStateWithLifecycle()
    val backgroundPlaybackEnabled by playbackHostViewModel.backgroundPlaybackEnabled.collectAsStateWithLifecycle()
    val miniPlayerAvailable by playbackHostViewModel.miniPlayerAvailable.collectAsStateWithLifecycle()
    DisposableEffect(procOwner, target, backgroundPlaybackEnabled) {
        if (target == null || backgroundPlaybackEnabled) {
            return@DisposableEffect onDispose { }
        }
        val lifecycle = procOwner.lifecycle
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> playbackHostViewModel.onEnterBackground()
                Lifecycle.Event.ON_START -> playbackHostViewModel.onReturnForeground()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
        }
    }
    var prevMode by remember { mutableStateOf(mode) }
    LaunchedEffect(mode) { prevMode = mode }
    var prevInPip by remember { mutableStateOf(PipPlayback.isInPip) }
    LaunchedEffect(PipPlayback.isInPip) { prevInPip = PipPlayback.isInPip }
    val skipEnter = (
        mode == PlaybackHostMode.Expanded && prevMode == PlaybackHostMode.Mini
        ) || (prevInPip && !PipPlayback.isInPip)
    val enterSpec = if (skipEnter) EnterNone else slideInHorizontally(
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
    ) { fullWidth -> fullWidth }
    // 正常分支被画中画临时替换时，用 SaveableStateHolder 保留视频页/直播页的可保存状态
    val expandedStateHolder = rememberSaveableStateHolder()

    Box(modifier = Modifier.fillMaxSize()) {
        if (PipPlayback.isInPip) {
            // 原生画中画模式：整个窗口只渲染播放器画面。
            // Android 12+ 点击由系统菜单接管（不处理应用内控件），
            // 12 以下点击呼出应用内控件
            val pipTarget = target
            if (pipTarget != null) {
                InAppMiniPlayer(
                    player = player,
                    target = pipTarget,
                    sessionState = sessionState,
                    showControlsOnTap = Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
                    onExpand = onExpand,
                    onTogglePlay = onTogglePlay,
                    onClose = onClose
                )
            } else {
                // 画中画内已无播放目标：把任务退到后台，退出画中画窗口
                val act = LocalActivity.current
                LaunchedEffect(pipTarget) {
                    act?.moveTaskToBack(true)
                }
            }
        } else {
            expandedStateHolder.SaveableStateProvider("playback_expanded") {
                AnimatedVisibility(
                    visible = target is StreamPlaybackTarget.Video && mode == PlaybackHostMode.Expanded,
                    enter = enterSpec,
                    exit = ExitNone
                ) {
                    VideoScreen(
                        onBack = onDismissExpanded,
                        onGoHome = onGoHome,
                        onOpenSpace = onOpenSpace,
                        onOpenDownloadCache = onOpenDownloadCache,
                        onStartDownload = onStartDownload,
                        viewModel = videoViewModel,
                        hostExpanded = true
                    )
                }

                AnimatedVisibility(
                    visible = target is StreamPlaybackTarget.Live && mode == PlaybackHostMode.Expanded,
                    enter = enterSpec,
                    exit = ExitNone
                ) {
                    LiveScreen(
                        onBack = onDismissExpanded,
                        viewModel = liveViewModel,
                        hostExpanded = true
                    )
                }
            }

            val curTarget = target
            if (mode == PlaybackHostMode.Mini &&
                miniPlayerAvailable &&
                curTarget != null
            ) {
                // 小窗比例跟随视频实际画面（未渲染首帧时由宿主回退 16:9）
                val videoAspect = if (
                    sessionState.videoWidth > 0 &&
                    sessionState.videoHeight > 0
                ) {
                    sessionState.videoWidth.toFloat() / sessionState.videoHeight
                } else {
                    0f
                }
                DraggableMiniPlayerHost(
                    videoAspectRatio = videoAspect,
                    modifier = modifier
                ) {
                    InAppMiniPlayer(
                        player = player,
                        target = curTarget,
                        sessionState = sessionState,
                        onExpand = onExpand,
                        onTogglePlay = onTogglePlay,
                        onClose = onClose
                    )
                }
            }
        }
    }
}
