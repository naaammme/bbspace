package com.naaammme.bbspace.feature.download

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.naaammme.bbspace.core.model.DownloadPlaybackStatus
import com.naaammme.bbspace.feature.download.model.DownloadPlayerViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Suppress("UnsafeOptInUsageError")
@UnstableApi
@Composable
fun DownloadPlayerScreen(
    onBack: () -> Unit,
    viewModel: DownloadPlayerViewModel = hiltViewModel()
) {
    val procOwner = remember { ProcessLifecycleOwner.get() }
    val ctx = LocalContext.current
    val act = remember(ctx) { ctx.findActivity() }
    var isFull by rememberSaveable { mutableStateOf(false) }

    val closePage = {
        viewModel.close()
        onBack()
    }
    val handleBack = {
        if (isFull) {
            isFull = false
        } else {
            closePage()
        }
    }

    BackHandler(onBack = handleBack)

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.close()
        }
    }

    DisposableEffect(act, isFull) {
        val activity = act
        if (activity == null) {
            onDispose { }
        } else {
            val win = activity.window
            val ctrl = WindowInsetsControllerCompat(win, win.decorView)
            if (isFull) {
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

    DisposableEffect(procOwner, viewModel) {
        val lifecycle = procOwner.lifecycle
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !viewModel.settingsState.value.playback.backgroundPlayback) {
                if (viewModel.state.value.isPlaying) {
                    viewModel.pause()
                }
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val playerGap = 12.dp
        val playerHeight = maxWidth * (9f / 16f)
        val playerSpaceHeight = statusTop + playerHeight

        if (!isFull) {
            DownloadPlayerMetaList(
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding(),
                topContentPadding = playerSpaceHeight + playerGap
            )
        }

        Box(
            modifier = if (isFull) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(playerSpaceHeight)
                    .background(Color.Black)
            }
        ) {
            DownloadPlayerPane(
                modifier = if (isFull) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(playerHeight)
                },
                viewModel = viewModel,
                isFull = isFull,
                onToggleFull = { isFull = !isFull },
                onBackClick = handleBack
            )
        }
    }
}

@Composable
private fun DownloadPlayerMetaList(
    viewModel: DownloadPlayerViewModel,
    modifier: Modifier = Modifier,
    topContentPadding: Dp
) {
    val metaFlow = remember(viewModel) {
        viewModel.state.map { playback ->
            DownloadPlayerMetaUiState(
                title = playback.title,
                subtitle = playback.subtitle,
                status = playback.playbackStatus,
                isPreparing = playback.isPreparing,
                error = playback.error
            )
        }
        .distinctUntilChanged()
    }
    val meta by metaFlow.collectAsStateWithLifecycle(initialValue = DownloadPlayerMetaUiState())

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            top = topContentPadding,
            end = 16.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item("meta") {
            DownloadPlayerMetaCard(meta = meta)
        }
    }
}

@Composable
private fun DownloadPlayerMetaCard(
    meta: DownloadPlayerMetaUiState
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = meta.title.ifBlank { "离线缓存" },
                style = MaterialTheme.typography.titleMedium
            )
            meta.subtitle?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = downloadStatusText(meta.status, meta.isPreparing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (meta.isPreparing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            meta.error?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

internal fun downloadStatusText(
    status: DownloadPlaybackStatus,
    isPreparing: Boolean
): String {
    if (isPreparing) return "准备播放"
    return when (status) {
        DownloadPlaybackStatus.IDLE -> "待播放"
        DownloadPlaybackStatus.BUFFERING -> "缓冲中"
        DownloadPlaybackStatus.READY -> "可播放"
        DownloadPlaybackStatus.ENDED -> "播放结束"
    }
}

private data class DownloadPlayerMetaUiState(
    val title: String = "",
    val subtitle: String? = null,
    val status: DownloadPlaybackStatus = DownloadPlaybackStatus.IDLE,
    val isPreparing: Boolean = false,
    val error: String? = null
)

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
