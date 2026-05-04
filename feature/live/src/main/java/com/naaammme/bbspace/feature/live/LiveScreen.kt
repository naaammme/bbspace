package com.naaammme.bbspace.feature.live

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.naaammme.bbspace.core.model.LivePlaybackViewState
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.feature.live.player.LivePlayerPane

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    onBack: () -> Unit,
    viewModel: LiveViewModel = hiltViewModel(),
    hostExpanded: Boolean = true
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val ctx = LocalContext.current
    val act = remember(ctx) { ctx.findActivity() }
    var isFull by rememberSaveable { mutableStateOf(false) }
    val fullOn = hostExpanded && isFull

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
        val activity = act
        if (activity == null) {
            onDispose { }
        } else {
            val win = activity.window
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (fullOn) {
            LivePlayerPane(
                route = state.route,
                player = player,
                playbackState = state.playbackState,
                isFull = true,
                onToggleFull = toggleFull,
                onTogglePlay = viewModel::togglePlayPause,
                onRetry = viewModel::retry,
                onSwitchQuality = viewModel::switchQuality,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = state.route?.title ?: "直播"
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

                LivePlayerPane(
                    route = state.route,
                    player = player,
                    playbackState = state.playbackState,
                    isFull = false,
                    onToggleFull = toggleFull,
                    onTogglePlay = viewModel::togglePlayPause,
                    onRetry = viewModel::retry,
                    onSwitchQuality = viewModel::switchQuality,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )

                LiveDetailSection(
                    route = state.route,
                    playbackState = state.playbackState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LiveDetailSection(
    route: LiveRoute?,
    playbackState: LivePlaybackViewState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = route?.title ?: "直播间 ${route?.roomId ?: 0L}",
            style = MaterialTheme.typography.titleMedium
        )

        route?.ownerName?.let { ownerName ->
            Text(
                text = ownerName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tags = listOfNotNull(
                route?.roomId?.takeIf { it > 0L }?.let { "房间 $it" },
                route?.onlineText?.takeIf(String::isNotBlank)
            )
            if (tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tags.forEach { text ->
                        MetaTag(text)
                    }
                }
            }

            playbackState.error?.let { error ->
                Text(
                    text = error.toUiMessage(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            playbackState.playerError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
@Composable
private fun MetaTag(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
