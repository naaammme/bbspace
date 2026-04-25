package com.naaammme.bbspace.feature.download

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.model.VideoDownloadKind
import com.naaammme.bbspace.core.model.VideoDownloadOption
import com.naaammme.bbspace.core.model.VideoDownloadOptions
import com.naaammme.bbspace.core.model.VideoDownloadProgress
import com.naaammme.bbspace.feature.download.model.DownloadUiState
import com.naaammme.bbspace.feature.download.model.DownloadViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    onBack: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("视频下载") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        DownloadContent(
            state = state,
            onInputChange = viewModel::updateInput,
            onSelectKind = viewModel::selectKind,
            onSelectQuality = viewModel::selectQuality,
            onSelectAudio = viewModel::selectAudio,
            onStartInputTask = viewModel::startInputTask,
            onStartDownload = viewModel::startDownload,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        )
    }
}

@Composable
private fun DownloadContent(
    state: DownloadUiState,
    onInputChange: (String) -> Unit,
    onSelectKind: (VideoDownloadKind) -> Unit,
    onSelectQuality: (Int) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onStartInputTask: () -> Unit,
    onStartDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canDownload = state.hasTask && !state.loading && !state.downloading

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InputCard(
            input = state.input,
            enabled = !state.loading && !state.downloading,
            onInputChange = onInputChange,
            onStart = onStartInputTask
        )
        KindCard(state.kind, onSelectKind)
        if (state.kind == VideoDownloadKind.VIDEO) {
            QualityCard(
                title = "视频画质偏好",
                options = VideoDownloadOptions.videoQualities,
                selected = state.videoQuality,
                onSelect = onSelectQuality
            )
        }
        QualityCard(
            title = "音频质量偏好",
            options = VideoDownloadOptions.audioQualities,
            selected = state.audioQuality,
            onSelect = onSelectAudio
        )

        if (state.loading) {
            StateCard("正在加载下载信息")
        }
        state.error?.takeIf(String::isNotBlank)?.let {
            StateCard(it, isError = true)
        }
        ProgressCard(state.progress)
        if (state.hasTask) {
            Button(
                onClick = onStartDownload,
                enabled = canDownload,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.downloading) "下载中" else "开始下载")
            }
        }
    }
}

@Composable
private fun InputCard(
    input: String,
    enabled: Boolean,
    onInputChange: (String) -> Unit,
    onStart: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("下载目标", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                enabled = enabled,
                singleLine = true,
                label = { Text("链接、av号或BV号") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onStart,
                enabled = enabled && input.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("解析并下载")
            }
        }
    }
}

@Composable
private fun KindCard(
    selected: VideoDownloadKind,
    onSelect: (VideoDownloadKind) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("下载内容", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selected == VideoDownloadKind.VIDEO,
                    onClick = { onSelect(VideoDownloadKind.VIDEO) },
                    label = { Text("下载视频") }
                )
                FilterChip(
                    selected = selected == VideoDownloadKind.AUDIO,
                    onClick = { onSelect(VideoDownloadKind.AUDIO) },
                    label = { Text("下载音频") }
                )
            }
        }
    }
}

@Composable
private fun QualityCard(
    title: String,
    options: List<VideoDownloadOption>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEach { option ->
                    FilterChip(
                        selected = option.value == selected,
                        onClick = { onSelect(option.value) },
                        label = {
                            Text(
                                text = option.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(progress: VideoDownloadProgress?) {
    progress ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (progress) {
                VideoDownloadProgress.Preparing -> {
                    Text("准备下载", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is VideoDownloadProgress.Downloading -> {
                    Text(
                        text = "正在下载${progress.label}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    val fraction = if (progress.totalBytes > 0L) {
                        progress.doneBytes.toFloat() / progress.totalBytes.toFloat()
                    } else {
                        0f
                    }
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${formatBytes(progress.doneBytes)} / ${formatBytes(progress.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                VideoDownloadProgress.Muxing -> {
                    Text("正在合并媒体", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is VideoDownloadProgress.Done -> {
                    Text("下载完成", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = progress.uri,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StateCard(
    text: String,
    isError: Boolean = false
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(16.dp)
        )
    }
}

private fun formatBytes(value: Long): String {
    if (value <= 0L) return "未知"
    val mb = value / 1024f / 1024f
    return String.format(Locale.ROOT, "%.1f MB", mb)
}
