package com.naaammme.bbspace.feature.im.feed

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.AvatarImage
import com.naaammme.bbspace.core.designsystem.component.BiliPullToRefreshBox
import com.naaammme.bbspace.core.designsystem.component.CoverImage
import com.naaammme.bbspace.core.designsystem.component.StateMessageCard
import com.naaammme.bbspace.core.model.PUBLISHED_RECORD_KIND_COMMENT
import com.naaammme.bbspace.core.model.PublishedRecord
import com.naaammme.bbspace.core.model.im.MsgFeedItem
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun MsgFeedPane(
    modifier: Modifier = Modifier,
    onOpenCommentDetail: (PublishedRecord) -> Unit,
    vm: MsgFeedViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.initLoad()
    }

    BiliPullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { vm.refresh() },
        modifier = modifier
    ) {
        if (state.items.isEmpty()) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                !state.errorMessage.isNullOrBlank() -> {
                    StateMessageCard(
                        text = state.errorMessage.orEmpty(),
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        isError = true,
                        actionText = "重试",
                        onAction = { vm.refresh() }
                    )
                }
                else -> {
                    StateMessageCard(
                        text = "暂无通知",
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    )
                }
            }
            return@BiliPullToRefreshBox
        }

        val listState = rememberLazyListState()
        val latestState by rememberUpdatedState(state)
        LaunchedEffect(listState) {
            snapshotFlow {
                val cur = latestState
                val total = listState.layoutInfo.totalItemsCount
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                cur.canLoadMore &&
                    !cur.isRefreshing &&
                    !cur.isLoadingMore &&
                    cur.loadMoreError.isNullOrBlank() &&
                    total > 0 &&
                    last >= total - 4
            }
                .distinctUntilChanged()
                .collect { shouldLoadMore ->
                    if (shouldLoadMore) vm.loadMore()
                }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.items, key = { it.msgId }) { item ->
                    MsgFeedCard(
                        item = item,
                        onOpenCommentDetail = onOpenCommentDetail
                    )
                }

                if (state.isLoadingMore) {
                    item(key = "loading_more") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (!state.loadMoreError.isNullOrBlank()) {
                    item(key = "load_more_error") {
                        StateMessageCard(
                            text = state.loadMoreError.orEmpty(),
                            modifier = Modifier.padding(vertical = 8.dp),
                            isError = true,
                            actionText = "重试",
                            onAction = { vm.loadMore() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MsgFeedCard(
    item: MsgFeedItem,
    onOpenCommentDetail: (PublishedRecord) -> Unit
) {
    val timeText = remember(item.msgTime) {
        item.msgTime
            .takeIf { it > 0L }
            ?.let { DateFormat.format("MM-dd HH:mm", it * 1000L).toString() }
            .orEmpty()
    }
    val headerTitle = remember(item.users) {
        item.users.take(2).joinToString("、") { it.name }
    }

    val cardContent: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.padding(16.dp)) {
            val user = item.users.firstOrNull()
            Row(verticalAlignment = Alignment.Top) {
                if (user != null) {
                    Box(modifier = Modifier.size(40.dp)) {
                        AvatarImage(
                            url = user.avatar,
                            contentDescription = user.name,
                            modifier = Modifier.fillMaxSize(),
                            fallbackText = user.name.take(1)
                        )
                        if (item.isUnread) {
                            Badge(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(12.dp),
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.actionText.isNotBlank()) {
                            Text(
                                text = item.actionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (item.isUnread) {
                            Badge(
                                modifier = Modifier.size(12.dp),
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = item.actionText.ifBlank { "未知用户回复了你" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (timeText.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (item.sourceContent.isNotBlank() || !item.coverImage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (item.sourceContent.isNotBlank()) {
                            Text(
                                text = item.sourceContent,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    if (!item.coverImage.isNullOrBlank()) {
                        CoverImage(
                            url = item.coverImage,
                            contentDescription = null,
                            modifier = Modifier.size(width = 96.dp, height = 54.dp)
                        )
                    }
                }
            }
            if (item.rootReplyContent.isNotBlank() || item.targetReplyContent.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = item.targetReplyContent.takeIf { it.isNotBlank() } ?: item.rootReplyContent,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
    if (item.canOpenDetail) {
        Card(
            onClick = { onOpenCommentDetail(item.toPublishedRecord()) },
            modifier = Modifier.fillMaxWidth(),
            content = cardContent
        )
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            content = cardContent
        )
    }
}

private fun MsgFeedItem.toPublishedRecord(): PublishedRecord {
    val user = users.firstOrNull()
    return PublishedRecord(
        key = "feed_${msgId}",
        kind = PUBLISHED_RECORD_KIND_COMMENT,
        itemId = sourceId.takeIf { it > 0L } ?: rootId,
        targetId = subjectId,
        targetType = businessId,
        senderMid = user?.mid ?: 0L,
        senderName = user?.name.orEmpty(),
        senderAvatar = user?.avatar.orEmpty(),
        content = sourceContent,
        ctime = msgTime,
        rootId = rootId,
        parentId = 0L
    )
}
