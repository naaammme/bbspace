package com.naaammme.bbspace.feature.live

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowWidthSizeClass
import com.naaammme.bbspace.core.model.LiveRoomPanelState
import com.naaammme.bbspace.feature.live.component.LivePlaybackBody
import com.naaammme.bbspace.feature.live.player.LivePlayerPane

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    onBack: () -> Unit,
    viewModel: LiveViewModel = hiltViewModel(),
    hostExpanded: Boolean = true
) {
    val route by viewModel.route.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val roomPanel by viewModel.roomPanel.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val ctx = LocalContext.current
    val act = remember(ctx) { ctx.findActivity() }
    val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    var isFull by rememberSaveable { mutableStateOf(false) }
    val fullOn = hostExpanded && isFull
    val isExpanded = hostExpanded && widthClass == WindowWidthSizeClass.EXPANDED && !fullOn

    val toggleFull = { isFull = !isFull }
    val handleBack = {
        if (fullOn) {
            isFull = false
        } else {
            onBack()
        }
    }

    if (hostExpanded) {
        BackHandler(onBack = handleBack)
    }

    LaunchedEffect(hostExpanded) {
        if (!hostExpanded) {
            isFull = false
        }
    }

    DisposableEffect(owner, viewModel) {
        val lifecycle = owner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.ensureStarted()
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.ensureStarted()
        }
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(act, fullOn) {
        if (act == null) {
            onDispose { }
        } else {
            val win = act.window
            val ctrl = WindowInsetsControllerCompat(win, win.decorView)
            if (fullOn) {
                ctrl.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                ctrl.show(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                ctrl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(act, fullOn, settingsState.playback.autoRotateFullscreen) {
        val activity = act ?: return@DisposableEffect onDispose { }
        activity.requestedOrientation = if (fullOn && settingsState.playback.autoRotateFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val playerGap = 16.dp
        val playerTopPad = 16.dp
        val compactVideoH = maxWidth * (9f / 16f)
        val expandedContentW = (maxWidth - (playerTopPad * 2) - playerGap).coerceAtLeast(0.dp)
        val expandedPlayerW = expandedContentW * 0.54f
        val expandedPlayerH = (maxHeight - (playerTopPad * 2)).coerceAtLeast(0.dp)

        if (fullOn) {
            LivePlayerPane(
                route = route,
                player = player,
                playbackState = playbackState,
                roomSessionState = viewModel.roomSession,
                isFull = true,
                onToggleFull = toggleFull,
                onTogglePlay = viewModel::togglePlayPause,
                onToggleDanmaku = viewModel::setDanmakuEnabled,
                onRetry = viewModel::retry,
                onSwitchQuality = viewModel::switchQuality,
                settingsState = settingsState,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        TopBarPanel(
                            popularCount = roomPanel.popularCount,
                            panel = roomPanel.panel
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = handleBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                )

                LivePlaybackBody(
                    route = route,
                    playbackState = playbackState,
                    roomSessionState = viewModel.roomSession,
                    player = player,
                    isExpanded = isExpanded,
                    playerSpaceWidth = expandedPlayerW,
                    playerSpaceHeight = if (isExpanded) expandedPlayerH else compactVideoH,
                    onToggleFull = toggleFull,
                    onTogglePlay = viewModel::togglePlayPause,
                    onToggleDanmaku = viewModel::setDanmakuEnabled,
                    onRetry = viewModel::retry,
                    onSwitchQuality = viewModel::switchQuality,
                    settingsState = settingsState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TopBarPanel(
    popularCount: Long,
    panel: LiveRoomPanelState
) {
    val parts = listOfNotNull(
        popularCount.takeIf { it > 0L }?.let { "人气 $it" },
        panel.watchedText?.takeIf(String::isNotBlank),
        panel.onlineRankText?.takeIf(String::isNotBlank),
        panel.rankChangedText?.takeIf(String::isNotBlank)
    )
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
