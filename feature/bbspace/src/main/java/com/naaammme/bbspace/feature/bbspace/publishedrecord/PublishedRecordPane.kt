package com.naaammme.bbspace.feature.bbspace.publishedrecord

import android.text.format.DateFormat
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.SearchCapsuleField
import com.naaammme.bbspace.core.model.PUBLISHED_RECORD_KIND_COMMENT
import com.naaammme.bbspace.core.model.PUBLISHED_RECORD_KIND_LIVE_DANMAKU
import com.naaammme.bbspace.core.model.PUBLISHED_RECORD_KIND_VIDEO_DANMAKU
import com.naaammme.bbspace.core.model.PublishedRecord
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch

@Composable
fun PublishedRecordPane(
    modifier: Modifier = Modifier,
    onOpenCommentDetail: (PublishedRecord) -> Unit = {},
    onOpenTarget: (PublishedRecord) -> Unit = {},
    vm: PublishedRecordViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var pendingDelete by remember { mutableStateOf<PublishedRecord?>(null) }
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - LOAD_MORE_TRIGGER_OFFSET
        }
    }

    LaunchedEffect(vm) {
        vm.loadIfNeeded()
    }

    LaunchedEffect(shouldLoadMore, state.isLoading, state.isLoadingMore, state.hasMore) {
        if (shouldLoadMore && !state.isLoading && !state.isLoadingMore && state.hasMore && state.items.isNotEmpty()) {
            vm.loadMore()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PublishedRecordFilterBar(
            keyword = state.keywordInput,
            sortDesc = state.sortDesc,
            onKeywordChange = vm::updateKeywordInput,
            onSearch = vm::search,
            onToggleSort = vm::toggleSort
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            when {
                state.error != null -> {
                    item {
                        PublishedRecordStateCard(
                            text = state.error.orEmpty(),
                            isError = true
                        )
                    }
                }

                state.items.isEmpty() && !state.isLoading -> {
                    item {
                        EmptyPublishedRecord(hasQuery = state.hasQuery)
                    }
                }

                else -> {
                    items(
                        items = state.items,
                        key = { it.key }
                    ) { item ->
                        PublishedRecordCard(
                            item = item,
                            onOpenTarget = onOpenTarget,
                            onClick = item.takeIf { it.kind == PUBLISHED_RECORD_KIND_COMMENT }
                                ?.let { { onOpenCommentDetail(it) } },
                            onDelete = { pendingDelete = item }
                        )
                    }
                }
            }

            if (!state.hasMore && state.items.isNotEmpty() && !state.isLoadingMore) {
                item {
                    PublishedRecordStateCard(text = "已经到底了")
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除记录") },
            text = { Text("只会删除本地记录,不会影响服务器上的内容") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            vm.delete(item.key)
                        }
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
}

@Composable
private fun PublishedRecordFilterBar(
    keyword: String,
    sortDesc: Boolean,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onToggleSort: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchCapsuleField(
            value = keyword,
            onValueChange = onKeywordChange,
            placeholder = "搜索发布内容",
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    onSearch()
                }
            )
        )
        TextButton(
            onClick = {
                focusManager.clearFocus()
                onSearch()
            }
        ) {
            Text("搜索")
        }
        TextButton(onClick = onToggleSort) {
            Text(if (sortDesc) "最新" else "最早")
        }
    }
}

@Composable
private fun EmptyPublishedRecord(hasQuery: Boolean) {
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
                text = if (hasQuery) "没有匹配的记录" else "还没有本地发布记录",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PublishedRecordCard(
    item: PublishedRecord,
    onOpenTarget: (PublishedRecord) -> Unit,
    onClick: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    val cardContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.content.ifBlank { "(空内容)" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.senderName} · ${formatCommentTime(item.ctime)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = buildRecordMeta(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.imageListJson != null || item.rootId != 0L || item.parentId != 0L) {
                Text(
                    text = if (item.rootId == 0L && item.parentId == 0L) {
                        "包含图片"
                    } else {
                        "回复评论"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onOpenTarget(item) }) {
                    Text("打开")
                }
                TextButton(onClick = onDelete) {
                    Text("删除")
                }
            }
        }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            cardContent()
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            cardContent()
        }
    }
}

@Composable
private fun PublishedRecordStateCard(
    text: String,
    isError: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatCommentTime(ctime: Long): String {
    if (ctime <= 0L) return "--"
    return DateFormat.format("yyyy-MM-dd HH:mm:ss", ctime * 1000L).toString()
}

private fun buildRecordMeta(item: PublishedRecord): String {
    return when (item.kind) {
        PUBLISHED_RECORD_KIND_COMMENT -> "评论 · oid ${item.targetId}"
        PUBLISHED_RECORD_KIND_VIDEO_DANMAKU -> "视频弹幕 · oid ${item.targetId}"
        PUBLISHED_RECORD_KIND_LIVE_DANMAKU -> "直播弹幕 · room ${item.targetId}"
        else -> "记录 · target ${item.targetId}"
    }
}

private const val LOAD_MORE_TRIGGER_OFFSET = 3
