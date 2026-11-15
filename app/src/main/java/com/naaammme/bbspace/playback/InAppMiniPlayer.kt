package com.naaammme.bbspace.playback

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.naaammme.bbspace.core.designsystem.component.BiliAsyncImage
import com.naaammme.bbspace.core.designsystem.component.BiliImageVariant
import com.naaammme.bbspace.core.designsystem.icon.AppIcons
import com.naaammme.bbspace.core.model.StreamPlaybackSessionState
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import com.naaammme.bbspace.infra.player.PlayerViewTargetBinder
import kotlinx.coroutines.delay

/** 点击呼出控件后无操作的自动隐藏延时 */
private const val MiniControlsAutoHideMs = 3_000L

/** 小窗尺寸有限，去掉 48dp 最小交互尺寸限制的紧凑图标按钮 */
@Composable
private fun CompactIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        IconButton(
            onClick = onClick,
            modifier = modifier.size(34.dp)
        ) {
            content()
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun InAppMiniPlayer(
    player: Player?,
    target: StreamPlaybackTarget,
    sessionState: StreamPlaybackSessionState,
    showControlsOnTap: Boolean = true,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val playerView = remember(context) {
        PlayerView(context).apply {
            useController = false
            setEnableComposeSurfaceSyncWorkaround(true)
            setKeepContentOnPlayerReset(true)
        }
    }
    // 默认只显示画面；点击小窗呼出控件，无操作数秒后自动隐藏；
    // 双击非按钮区域直接暂停/播放（无论控件是否显示）
    var controlsVisible by remember { mutableStateOf(false) }
    var controlsActivity by remember { mutableIntStateOf(0) }
    LaunchedEffect(controlsVisible, controlsActivity) {
        if (controlsVisible) {
            delay(MiniControlsAutoHideMs)
            controlsVisible = false
        }
    }
    val liveCover = (target as? StreamPlaybackTarget.Live)?.route?.cover

    DisposableEffect(playerView) {
        onDispose {
            PlayerViewTargetBinder.unbind(playerView)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(showControlsOnTap) {
                detectTapGestures(
                    onTap = {
                        if (showControlsOnTap) controlsVisible = !controlsVisible
                    },
                    onDoubleTap = { onTogglePlay() }
                )
            },
        shape = MaterialTheme.shapes.large,
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { playerView },
                update = { view ->
                    PlayerViewTargetBinder.bind(view, player)
                    view.keepScreenOn = sessionState.playWhenReady
                },
                modifier = Modifier.fillMaxSize()
            )
            if (!sessionState.hasRenderedFirstFrame && !liveCover.isNullOrBlank()) {
                BiliAsyncImage(
                    url = liveCover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    variant = BiliImageVariant.Banner
                )
            }
            if (controlsVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    CompactIconButton(
                        onClick = onClose,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color.White
                        )
                    }
                    CompactIconButton(
                        onClick = {
                            onTogglePlay()
                            controlsActivity++
                        },
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Icon(
                            imageVector = if (sessionState.isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (sessionState.isPlaying) "暂停" else "播放",
                            tint = Color.White
                        )
                    }
                    CompactIconButton(
                        onClick = {
                            controlsVisible = false
                            onExpand()
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        Icon(
                            imageVector = AppIcons.Fullscreen,
                            contentDescription = "全屏",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
