package com.naaammme.bbspace.feature.bbspace.playback

import android.content.Context
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.StateMessageCard
import com.naaammme.bbspace.core.model.PlaybackHistory
import com.naaammme.bbspace.feature.bbspace.rememberExportJson

@Composable
fun PlaybackHistoryPane(
    modifier: Modifier = Modifier,
    vm: PlaybackHistoryViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val exportJson = rememberExportJson()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<PlaybackHistory?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        item {
            PlaybackHistoryManageCard(
                count = state.items.size,
                onExport = {
                    if (state.items.isEmpty()) {
                        toast(context, "暂无播放历史")
                    } else {
                        exportJson(
                            "bbspace_playback_history.json",
                            vm.export(state.items)
                        )
                    }
                },
                onClear = {
                    if (state.items.isEmpty()) {
                        toast(context, "暂无可删除记录")
                    } else {
                        showClearDialog = true
                    }
                }
            )
        }

        if (state.items.isEmpty()) {
            item {
                StateMessageCard(
                    text = "还没有本地播放历史",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            items(
                items = state.items,
                key = { it.id }
            ) { item ->
                PlaybackHistoryCard(
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
            text = { Text("删除视频 ${item.aid}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        vm.delete(item)
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
                        vm.clear()
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

@Composable
private fun PlaybackHistoryManageCard(
    count: Int,
    onExport: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
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
private fun PlaybackHistoryCard(
    item: PlaybackHistory,
    onDelete: () -> Unit
) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val title = remember(item.biz, item.aid, item.cid, item.epId) {
        when {
            (item.epId ?: 0L) > 0L -> "${item.biz.uppercase()} ${item.epId}"
            else -> "${item.biz.uppercase()} ${item.aid}:${item.cid}"
        }
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
        onClick = { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                if (expanded) {
                    Text(
                        text = "UID ${item.uid}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "AID ${item.aid} CID ${item.cid}",
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

private fun toast(
    context: Context,
    text: String
) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
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
