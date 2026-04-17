package com.naaammme.bbspace.feature.bbspace

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.core.model.VideoHistory
import com.naaammme.bbspace.feature.bbspace.model.BbSpaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BbSpaceScreen(
    onBack: () -> Unit,
    vm: BbSpaceViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(BbSpacePage.Home) }
    val title = if (page == BbSpacePage.Home) "bb空间" else "播放历史"
    val handleBack = {
        if (page == BbSpacePage.Home) {
            onBack()
        } else {
            page = BbSpacePage.Home
        }
    }

    BackHandler(enabled = page != BbSpacePage.Home) {
        page = BbSpacePage.Home
    }

    CollapsingTopBarScaffold(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (page) {
                BbSpacePage.Home -> {
                    HistoryEntryCard(
                        count = state.videos.size,
                        onClick = { page = BbSpacePage.PlaybackHistory }
                    )
                }
                BbSpacePage.PlaybackHistory -> {
                    BbSpaceHistoryContent(vm = vm, state = state)
                }
            }
        }
    }
}

@Composable
private fun BbSpaceHistoryContent(
    vm: BbSpaceViewModel,
    state: com.naaammme.bbspace.feature.bbspace.model.BbSpaceState
) {
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<VideoHistory?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        item {
            HistoryManageCard(
                count = state.videos.size,
                onExport = {
                    if (state.videos.isEmpty()) {
                        Toast.makeText(context, "暂无播放历史", Toast.LENGTH_SHORT).show()
                    } else {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "bbspace_video_history",
                                vm.exportVideos(state.videos)
                            )
                        )
                        Toast.makeText(context, "已复制到剪切板", Toast.LENGTH_SHORT).show()
                    }
                },
                onClear = {
                    if (state.videos.isEmpty()) {
                        Toast.makeText(context, "暂无可删除记录", Toast.LENGTH_SHORT).show()
                    } else {
                        showClearDialog = true
                    }
                }
            )
        }

        if (state.videos.isEmpty()) {
            item {
                EmptyHistory()
            }
        } else {
            items(
                items = state.videos,
                key = { it.id }
            ) { item ->
                HistoryCard(
                    item = item,
                    onDelete = { pendingDelete = item }
                )
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除记录") },
            text = { Text("删除 ${item.title.ifBlank { item.bvid ?: "这条播放记录" }}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        vm.deleteVideo(item)
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空播放历史") },
            text = { Text("这会删除所有本地播放历史记录") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        vm.clearVideos()
                    }
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private enum class BbSpacePage {
    Home,
    PlaybackHistory
}

@Composable
private fun HistoryEntryCard(
    count: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "播放历史",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "当前有 $count 条本地记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "进入",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryManageCard(
    count: Int,
    onExport: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "播放历史",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "当前有 $count 条本地记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导出")
                }
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清空")
                }
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "还没有本地播放历史",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryCard(
    item: VideoHistory,
    onDelete: () -> Unit
) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val title = remember(item.title, item.bvid, item.aid) {
        item.title.ifBlank { item.bvid?.takeIf(String::isNotBlank) ?: "视频 ${item.aid}" }
    }
    val sub = remember(item.part, item.partTitle, item.ownerName) {
        buildList {
            item.part?.let { add("P$it") }
            item.partTitle?.takeIf(String::isNotBlank)?.let(::add)
            item.ownerName?.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString(" · ")
    }
    val progress = remember(item.progressMs, item.durationMs, item.finished) {
        buildString {
            append(formatMs(item.progressMs))
            if (item.durationMs > 0L) {
                append(" / ")
                append(formatMs(item.durationMs))
            }
            if (item.finished) {
                append(" · 已看完")
            }
        }
    }
    val updated = remember(item.updatedAt) {
        if (item.updatedAt <= 0L) {
            "--"
        } else {
            DateFormat.format("yyyy-MM-dd HH:mm", item.updatedAt).toString()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Cover(item = item)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (expanded && sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (expanded) {
                    Text(
                        text = "UID ${item.uid}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "进度 $progress",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "最后更新 $updated",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("删除")
                    }
                }
            }

            Text(
                text = if (expanded) "收起" else "展开",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Cover(item: VideoHistory) {
    if (!item.cover.isNullOrBlank()) {
        AsyncImage(
            model = item.cover,
            contentDescription = item.title,
            modifier = Modifier
                .size(width = 96.dp, height = 60.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 60.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (item.ownerName.isNullOrBlank()) Icons.Default.DateRange else Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatMs(value: Long): String {
    val totalSec = (value.coerceAtLeast(0L) / 1000L).toInt()
    val hour = totalSec / 3600
    val minute = (totalSec % 3600) / 60
    val second = totalSec % 60
    return if (hour > 0) {
        "%d:%02d:%02d".format(hour, minute, second)
    } else {
        "%02d:%02d".format(minute, second)
    }
}
