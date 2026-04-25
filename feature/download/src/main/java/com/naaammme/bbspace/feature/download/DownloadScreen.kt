package com.naaammme.bbspace.feature.download

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.FilledTabRow
import com.naaammme.bbspace.core.model.VideoDownloadKind
import com.naaammme.bbspace.core.model.VideoDownloadOption
import com.naaammme.bbspace.core.model.VideoDownloadOptions
import com.naaammme.bbspace.core.model.VideoDownloadProgress
import com.naaammme.bbspace.core.model.VideoDownloadTask
import com.naaammme.bbspace.core.model.VideoDownloadTaskStatus
import com.naaammme.bbspace.feature.download.model.DownloadTab
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
            onSelectTab = viewModel::selectTab,
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
    onSelectTab: (DownloadTab) -> Unit,
    onInputChange: (String) -> Unit,
    onSelectKind: (VideoDownloadKind) -> Unit,
    onSelectQuality: (Int) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onStartInputTask: () -> Unit,
    onStartDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        FilledTabRow(
            tabs = DownloadTab.entries.map { it.title },
            selectedIndex = state.tab.ordinal,
            onSelect = { index -> onSelectTab(DownloadTab.entries[index]) },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )

        when (state.tab) {
            DownloadTab.CONFIG -> ConfigTab(
                state = state,
                onInputChange = onInputChange,
                onSelectKind = onSelectKind,
                onSelectQuality = onSelectQuality,
                onSelectAudio = onSelectAudio,
                onStartInputTask = onStartInputTask,
                onStartDownload = onStartDownload,
                modifier = Modifier.fillMaxSize()
            )

            DownloadTab.QUEUE -> QueueTab(
                state = state,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ConfigTab(
    state: DownloadUiState,
    onInputChange: (String) -> Unit,
    onSelectKind: (VideoDownloadKind) -> Unit,
    onSelectQuality: (Int) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onStartInputTask: () -> Unit,
    onStartDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canDownload = state.hasTask && !state.loading

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item("input") {
            InputCard(
                input = state.input,
                enabled = !state.loading,
                onInputChange = onInputChange,
                onStart = onStartInputTask
            )
        }
        state.pendingTitle?.let { title ->
            item("pending") {
                StateCard("已解析目标：$title")
            }
        }
        item("kind") {
            KindCard(state.kind, onSelectKind)
        }
        if (state.kind == VideoDownloadKind.VIDEO) {
            item("video_quality") {
                QualityCard(
                    title = "视频画质偏好",
                    options = VideoDownloadOptions.videoQualities,
                    selected = state.videoQuality,
                    onSelect = onSelectQuality
                )
            }
        }
        item("audio_quality") {
            QualityCard(
                title = "音频质量偏好",
                options = VideoDownloadOptions.audioQualities,
                selected = state.audioQuality,
                onSelect = onSelectAudio
            )
        }
        if (state.loading) {
            item("loading") {
                StateCard("正在解析下载目标")
            }
        }
        state.error?.takeIf(String::isNotBlank)?.let { message ->
            item("error") {
                StateCard(message, isError = true)
            }
        }
        if (state.hasTask) {
            item("start") {
                Button(
                    onClick = onStartDownload,
                    enabled = canDownload,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("加入下载队列")
                }
            }
        }
    }
}

@Composable
private fun QueueTab(
    state: DownloadUiState,
    modifier: Modifier = Modifier
) {
    val tasks = state.tasks.sortedWith(
        compareBy<VideoDownloadTask>({ taskOrder(it) }, { it.id })
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (tasks.isEmpty()) {
            item("queue_empty") {
                StateCard("暂无下载任务")
            }
        } else {
            items(
                items = tasks,
                key = { it.id }
            ) { task ->
                TaskCard(task)
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
                Text("解析目标")
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

@OptIn(ExperimentalLayoutApi::class)
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
private fun TaskCard(task: VideoDownloadTask) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = taskSummary(task),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = taskStatusText(task),
                style = MaterialTheme.typography.bodySmall,
                color = statusColor(task)
            )
            when (val progress = task.progress) {
                is VideoDownloadProgress.Downloading -> {
                    val fraction = if (progress.totalBytes > 0L) {
                        progress.doneBytes.toFloat() / progress.totalBytes.toFloat()
                    } else {
                        0f
                    }
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                VideoDownloadProgress.Preparing,
                VideoDownloadProgress.Muxing -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                else -> Unit
            }
            if (task.status == VideoDownloadTaskStatus.DONE) {
                val uri = (task.progress as? VideoDownloadProgress.Done)?.uri
                if (!uri.isNullOrBlank()) {
                    Text(
                        text = uri,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            task.error?.takeIf(String::isNotBlank)?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
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

private fun taskSummary(task: VideoDownloadTask): String {
    val kind = if (task.request.kind == VideoDownloadKind.VIDEO) "视频" else "音频"
    val video = if (task.request.kind == VideoDownloadKind.VIDEO) {
        VideoDownloadOptions.videoLabel(task.request.videoQuality)
    } else {
        null
    }
    val audio = VideoDownloadOptions.audioLabel(task.request.audioQuality)
    return listOfNotNull(kind, video, audio).joinToString(" · ")
}

private fun taskOrder(task: VideoDownloadTask): Int {
    return when (task.status) {
        VideoDownloadTaskStatus.RUNNING -> 0
        VideoDownloadTaskStatus.WAITING -> 1
        VideoDownloadTaskStatus.FAILED -> 2
        VideoDownloadTaskStatus.DONE -> 3
    }
}

private fun taskStatusText(task: VideoDownloadTask): String {
    return when (task.status) {
        VideoDownloadTaskStatus.WAITING -> "等待下载"
        VideoDownloadTaskStatus.RUNNING -> when (val progress = task.progress) {
            VideoDownloadProgress.Preparing -> "准备下载"
            is VideoDownloadProgress.Downloading -> {
                "正在下载${progress.label} ${formatBytes(progress.doneBytes)} / ${formatBytes(progress.totalBytes)}"
            }
            VideoDownloadProgress.Muxing -> "正在合并媒体"
            is VideoDownloadProgress.Done -> "下载完成"
            null -> "下载中"
        }
        VideoDownloadTaskStatus.DONE -> "下载完成"
        VideoDownloadTaskStatus.FAILED -> "下载失败"
    }
}

@Composable
private fun statusColor(task: VideoDownloadTask) = when (task.status) {
    VideoDownloadTaskStatus.FAILED -> MaterialTheme.colorScheme.error
    VideoDownloadTaskStatus.DONE -> MaterialTheme.colorScheme.primary
    VideoDownloadTaskStatus.RUNNING -> MaterialTheme.colorScheme.onSecondaryContainer
    VideoDownloadTaskStatus.WAITING -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatBytes(value: Long): String {
    if (value <= 0L) return "未知"
    val kb = value / 1024f
    val mb = kb / 1024f
    val gb = mb / 1024f
    return when {
        gb >= 1f -> String.format(Locale.ROOT, "%.1f GB", gb)
        mb >= 1f -> String.format(Locale.ROOT, "%.1f MB", mb)
        kb >= 1f -> String.format(Locale.ROOT, "%.1f KB", kb)
        else -> "$value B"
    }
}
