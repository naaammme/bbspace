package com.naaammme.bbspace.feature.comment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.designsystem.component.PreviewImage
import com.naaammme.bbspace.core.model.CommentSort
import com.naaammme.bbspace.core.model.CommentUser

@Composable
internal fun CommentThreadPane(
    state: CommentThreadState,
    isLoading: (Long) -> Boolean,
    onSaveImage: (PreviewImage) -> Unit,
    onDismiss: () -> Unit,
    onToggleSort: () -> Unit,
    onTranslate: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenUser: (CommentUser) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        CommentThreadContent(
            state = state,
            isLoading = isLoading,
            onSaveImage = onSaveImage,
            onDismiss = onDismiss,
            onToggleSort = onToggleSort,
            onTranslate = onTranslate,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
            onOpenUser = onOpenUser,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun CommentThreadContent(
    state: CommentThreadState,
    isLoading: (Long) -> Boolean,
    onSaveImage: (PreviewImage) -> Unit,
    onDismiss: () -> Unit,
    onToggleSort: () -> Unit,
    onTranslate: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenUser: (CommentUser) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(state.hasMore, state.loading, state.loadingMore, state.loadMoreError, state.items) {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            state.hasMore &&
                !state.loading &&
                !state.loadingMore &&
                state.loadMoreError.isNullOrBlank() &&
                state.items.isNotEmpty() &&
                total > 0 &&
                last >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
        HorizontalDivider()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(
                key = "reply_root",
                contentType = "root"
            ) {
                ThreadReplyCard(
                    reply = state.root,
                    isLoading = isLoading,
                    onTranslate = onTranslate,
                    onSaveImage = onSaveImage,
                    onOpenUser = onOpenUser
                )
            }
            item(
                key = "reply_info",
                contentType = "info"
            ) {
                ThreadInfoBar(
                    count = state.count,
                    sort = state.sort,
                    canSwitchSort = state.canSwitchSort,
                    onToggleSort = onToggleSort
                )
            }

            when {
                state.loading && state.items.isEmpty() -> {
                    item(
                        key = "reply_loading",
                        contentType = "state"
                    ) {
                        StateCard("加载回复中...")
                    }
                }

                !state.error.isNullOrBlank() && state.items.isEmpty() -> {
                    item(
                        key = "reply_error",
                        contentType = "state"
                    ) {
                        RetryCard(
                            text = state.error.orEmpty(),
                            button = "重试",
                            onRetry = onRetry
                        )
                    }
                }

                state.items.isEmpty() -> {
                    item(
                        key = "reply_empty",
                        contentType = "state"
                    ) {
                        StateCard("还没有回复")
                    }
                }

                else -> {
                    items(
                        items = state.items,
                        key = { it.rpid },
                        contentType = { "reply" }
                    ) { reply ->
                        ThreadReplyCard(
                            reply = reply,
                            isLoading = isLoading,
                            onTranslate = onTranslate,
                            onSaveImage = onSaveImage,
                            onOpenUser = onOpenUser
                        )
                    }
                }
            }

            if (state.loadingMore) {
                item(
                    key = "reply_loading_more",
                    contentType = "footer"
                ) {
                    StateCard("加载更多回复中...")
                }
            } else if (!state.loadMoreError.isNullOrBlank()) {
                item(
                    key = "reply_load_more_error",
                    contentType = "footer"
                ) {
                    RetryCard(
                        text = state.loadMoreError.orEmpty(),
                        button = "重试加载更多",
                        onRetry = onLoadMore
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadInfoBar(
    count: Long,
    sort: CommentSort,
    canSwitchSort: Boolean,
    onToggleSort: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (count > 0L) {
                "相关回复共${count.formatCount()}条"
            } else {
                "相关回复"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (canSwitchSort) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
                onClick = onToggleSort
            ) {
                Text(
                    text = "按${threadSortText(sort)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } else {
            Text(
                text = threadSortText(sort),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun threadSortText(sort: CommentSort): String {
    return when (sort) {
        CommentSort.HOT -> "热门"
        CommentSort.TIME -> "时间"
    }
}
