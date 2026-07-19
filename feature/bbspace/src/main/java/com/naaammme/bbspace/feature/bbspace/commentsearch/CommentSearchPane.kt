package com.naaammme.bbspace.feature.bbspace.commentsearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.model.PublishedRecord
import com.naaammme.bbspace.core.model.PUBLISHED_RECORD_KIND_COMMENT
import com.naaammme.bbspace.core.designsystem.component.StateMessageCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommentSearchPane(
    modifier: Modifier = Modifier,
    onOpenCommentDetail: (PublishedRecord) -> Unit = {},
    vm: CommentSearchViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - LOAD_MORE_TRIGGER_OFFSET
        }
    }

    LaunchedEffect(shouldLoadMore, state.isLoading, state.isLoadingMore) {
        if (shouldLoadMore && !state.isLoadingMore && !state.isEnd && !state.isLoading && !state.queryPending && state.items.isNotEmpty()) {
            vm.loadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CommentSearchHeaderCard(
                uidInput = state.uidInput,
                keywordInput = state.keywordInput,
                mode = state.mode,
                isLoading = state.isLoading,
                isLoadingMore = state.isLoadingMore,
                onUidChanged = vm::updateUidInput,
                onKeywordChanged = vm::updateKeywordInput,
                onModeSelected = vm::selectMode,
                onQueryClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    vm.query()
                }
            )
        }

        state.allCount?.let { allCount ->
            item {
                CommentSearchBaseCard(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "共 $allCount 条，已加载 ${state.items.size} 条",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val tipText = when {
                        state.queryPending -> "筛选条件已修改，点击查询更新结果"
                        !state.isEnd -> "上滑继续加载"
                        state.isEnd -> "已到末页"
                        else -> "暂无更多"
                    }
                    Text(
                        text = tipText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.allCount != null && state.items.isEmpty() && state.error == null && !state.isLoading) {
            item {
                StateMessageCard(text = "没有匹配结果")
            }
        }

        if (state.items.isNotEmpty()) {
            itemsIndexed(
                items = state.items,
                key = { _, item -> item.id }
            ) { _, item ->
                val record = item.record
                CommentSearchCard(
                    item = item,
                    onClick = if (record != null) {
                        { onOpenCommentDetail(record) }
                    } else null
                )
            }
        }

        if (state.isLoadingMore) {
            item {
                StateMessageCard(text = "加载更多中")
            }
        }

        state.appendError?.let { message ->
            item {
                StateMessageCard(
                    text = message,
                    isError = true,
                    actionText = "重试加载更多",
                    onAction = vm::loadMore
                )
            }
        }

        state.error?.let { message ->
            item {
                StateMessageCard(text = message, isError = true)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentSearchHeaderCard(
    uidInput: String,
    keywordInput: String,
    mode: CommentSearchMode,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    onUidChanged: (String) -> Unit,
    onKeywordChanged: (String) -> Unit,
    onModeSelected: (CommentSearchMode) -> Unit,
    onQueryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CommentSearchBaseCard(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "查评论",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = uidInput,
            onValueChange = onUidChanged,
            enabled = !isLoading && !isLoadingMore,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            label = { Text("UID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = keywordInput,
            onValueChange = onKeywordChanged,
            enabled = !isLoading && !isLoadingMore,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            label = { Text("关键词") },
            singleLine = true
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CommentSearchMode.entries.forEach { md ->
                FilterChip(
                    selected = mode == md,
                    onClick = { onModeSelected(md) },
                    enabled = !isLoading && !isLoadingMore,
                    label = { Text(md.title) }
                )
            }
        }
        Button(
            onClick = onQueryClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && !isLoadingMore
        ) {
            Text(if (isLoading) "查询中" else "查询")
        }
    }
}

@Composable
private fun CommentSearchBaseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = verticalArrangement,
            content = content
        )
    }
}

@Composable
private fun CommentSearchCard(
    item: CommentSearchItem,
    onClick: (() -> Unit)? = null
) {
    CommentSearchBaseCard(onClick = onClick) {
        item.title?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = item.message.ifBlank { "(空评论)" },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
        if (item.metaLine.isNotBlank()) {
            Text(
                text = item.metaLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (item.timeText.isNotBlank()) {
            Text(
                text = item.timeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val LOAD_MORE_TRIGGER_OFFSET = 3
