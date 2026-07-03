package com.naaammme.bbspace.feature.comment.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.model.CommentSort
import com.naaammme.bbspace.feature.comment.component.CommentReplyAction
import com.naaammme.bbspace.feature.comment.component.StateCard
import com.naaammme.bbspace.feature.comment.component.ThreadReplyCard
import com.naaammme.bbspace.feature.comment.component.formatCount

@Composable
internal fun CommentThreadPane(
    state: CommentThreadState,
    listState: LazyListState,
    currentMid: Long,
    busyReplyIds: Set<Long>,
    onReplyAction: (CommentReplyAction) -> Unit,
    onDismiss: () -> Unit,
    onToggleSort: () -> Unit,
    onLoadMore: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        CommentThreadContent(
            state = state,
            listState = listState,
            currentMid = currentMid,
            busyReplyIds = busyReplyIds,
            onReplyAction = onReplyAction,
            onDismiss = onDismiss,
            onToggleSort = onToggleSort,
            onLoadMore = onLoadMore,
            bottomPadding = bottomPadding,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun CommentThreadContent(
    state: CommentThreadState,
    listState: LazyListState,
    currentMid: Long,
    busyReplyIds: Set<Long>,
    onReplyAction: (CommentReplyAction) -> Unit,
    onDismiss: () -> Unit,
    onToggleSort: () -> Unit,
    onLoadMore: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val stateHolder by rememberUpdatedState(state)
    val shouldLoadMore by remember {
        derivedStateOf {
            val cur = stateHolder
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            cur.hasMore &&
                !cur.loading &&
                !cur.loadingMore &&
                cur.loadMoreError.isNullOrBlank() &&
                cur.items.isNotEmpty() &&
                total > 0 &&
                last >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }
    val canHighlight = state.highlightRpid > 0L && !state.loading && state.error.isNullOrBlank()
    LaunchedEffect(canHighlight) {
        if (!canHighlight) return@LaunchedEffect
        val targetIndex = if (state.root.rpid == state.highlightRpid) {
            0
        } else {
            val i = state.items.indexOfFirst { it.rpid == state.highlightRpid }
            if (i >= 0) 2 + i else -1
        }
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
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
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 16.dp + bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(
                key = "reply_root",
                contentType = "root"
            ) {
                ThreadReplyCard(
                    reply = state.root,
                    currentMid = currentMid,
                    busyReplyIds = busyReplyIds,
                    onAction = onReplyAction
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
                !state.error.isNullOrBlank() && state.items.isEmpty() -> {
                    item(
                        key = "reply_error",
                        contentType = "state"
                    ) {
                        StateCard(text = state.error.orEmpty())
                    }
                }

                state.items.isEmpty() && !state.loading -> {
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
                            currentMid = currentMid,
                            busyReplyIds = busyReplyIds,
                            onAction = onReplyAction
                        )
                    }
                }
            }

            if (!state.loadMoreError.isNullOrBlank()) {
                item(
                    key = "reply_load_more_error",
                    contentType = "footer"
                ) {
                    StateCard(text = state.loadMoreError.orEmpty())
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
