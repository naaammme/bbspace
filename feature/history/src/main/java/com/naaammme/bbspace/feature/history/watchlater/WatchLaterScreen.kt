package com.naaammme.bbspace.feature.history.watchlater

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.BiliPullToRefreshBox
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.core.designsystem.component.CoverImage
import com.naaammme.bbspace.core.designsystem.component.FilledTabRow
import com.naaammme.bbspace.core.designsystem.component.StateMessageCard
import com.naaammme.bbspace.core.designsystem.component.VideoListCardSkeleton
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.core.model.WatchLaterItem
import com.naaammme.bbspace.core.model.WatchLaterTab
import com.naaammme.bbspace.feature.history.component.HistoryListLoading
import com.naaammme.bbspace.feature.history.component.LOAD_MORE_SKELETON_COUNT
import com.naaammme.bbspace.feature.history.component.LoadMoreTrigger
import com.naaammme.bbspace.feature.history.component.formatVideoDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchLaterScreen(
    onBack: () -> Unit,
    onOpenVideo: (VideoTarget) -> Unit,
    viewModel: WatchLaterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LoadMoreTrigger(
        listState = listState,
        canLoadMore = { state.canLoadMore },
        isLoadingMore = { state.isLoadingMore },
        hasError = { state.errorMessage != null },
        onLoadMore = viewModel::loadMore
    )

    LaunchedEffect(state.tab, state.asc) {
        val needScrollTop = listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 0
        if (needScrollTop && listState.layoutInfo.totalItemsCount > 0) {
            listState.scrollToItem(0)
        }
    }

    CollapsingTopBarScaffold(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = {
                    Text(
                        text = if (!state.countText.isNullOrBlank()) {
                            "稍后再看 ${state.countText}"
                        } else {
                            "稍后再看"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::toggleSort) {
                        Text(if (state.asc) "最早添加" else "最新添加")
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
        ) {
            FilledTabRow(
                tabs = WatchLaterTab.entries.map { it.title },
                selectedIndex = state.tab.ordinal,
                onSelect = { index -> viewModel.selectTab(WatchLaterTab.entries[index]) },
                modifier = Modifier
            )

            BiliPullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f)
            ) {
                when {
                    state.isLoading && state.items.isEmpty() -> {
                        HistoryListLoading(
                            skeletonPrefix = "watch_later_skeleton",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    state.errorMessage != null && state.items.isEmpty() -> {
                        StateMessageCard(
                            text = state.errorMessage.orEmpty().ifBlank { "加载稍后再看失败" },
                            modifier = Modifier.fillMaxSize(),
                            isError = true,
                            actionText = "重试",
                            onAction = viewModel::refresh
                        )
                    }

                    state.items.isEmpty() -> {
                        StateMessageCard(
                            text = "暂无${state.tab.title}内容",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = state.items,
                                key = { it.key },
                                contentType = { it.cardType }
                            ) { item ->
                                WatchLaterItemCard(
                                    item = item,
                                    onClick = {
                                        item.target?.let(onOpenVideo)
                                    }
                                )
                            }

                            if (state.isLoadingMore) {
                                items(
                                    count = LOAD_MORE_SKELETON_COUNT,
                                    key = { index -> "watch_later_loading_$index" },
                                    contentType = { "loading" }
                                ) {
                                    VideoListCardSkeleton()
                                }
                            }

                            if (state.errorMessage != null && state.items.isNotEmpty()) {
                                item(
                                    key = "watch_later_error",
                                    contentType = "error"
                                ) {
                                    StateMessageCard(
                                        text = state.errorMessage.orEmpty().ifBlank { "加载稍后再看失败" },
                                        isError = true,
                                        actionText = "重试",
                                        onAction = if (state.errorOnLoadMore) {
                                            viewModel::loadMore
                                        } else {
                                            viewModel::refresh
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchLaterItemCard(
    item: WatchLaterItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardModifier = modifier.fillMaxWidth()
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)

    if (item.isOpenable) {
        Card(onClick = onClick, modifier = cardModifier, colors = colors, elevation = elevation) {
            WatchLaterItemContent(item = item)
        }
    } else {
        Card(modifier = cardModifier, colors = colors, elevation = elevation) {
            WatchLaterItemContent(item = item)
        }
    }
}

@Composable
private fun WatchLaterItemContent(
    item: WatchLaterItem,
    modifier: Modifier = Modifier
) {
    val infoLine = remember(item) { buildInfoLine(item) }
    val metaLine = remember(item) { buildMetaLine(item) }
    val progress = remember(item) { progressText(item) }
    val duration = remember(item) { durationText(item.durationSec) }

    if (!item.isOpenable) {
        Text(
            text = item.title,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CoverImage(
            url = item.cover,
            contentDescription = "",
            modifier = Modifier
                .weight(0.38f)
                .aspectRatio(16f / 10f)
        ) {
            duration?.let { text ->
                Text(
                    text = text,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.56f), MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (infoLine.isNotBlank()) {
                Text(
                    text = infoLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = metaLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            progress?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.shapes.extraSmall
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun buildInfoLine(item: WatchLaterItem): String {
    return listOfNotNull(
        item.ownerName,
        item.badge,
        item.intro?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
}

private fun buildMetaLine(item: WatchLaterItem): String {
    return listOfNotNull(
        item.viewText?.takeIf { it.isNotBlank() }?.let { "播放 $it" },
        item.danmakuText?.takeIf { it.isNotBlank() }?.let { "弹幕 $it" },
        item.addedAtSec.takeIf { it > 0L }?.let {
            DateFormat.format("MM-dd HH:mm", it * 1000).toString()
        }
    ).joinToString(" · ")
}

private fun progressText(item: WatchLaterItem): String? {
    val progress = item.progressSec ?: return null
    val duration = item.durationSec ?: return if (progress > 0L) "已观看 $progress 秒" else null
    return if (progress >= duration) {
        "已看完"
    } else if (progress <= 0L) {
        "未开始"
    } else {
        "进度 ${formatVideoDuration(progress)} / ${formatVideoDuration(duration)}"
    }
}

private fun durationText(sec: Long?): String? {
    val value = sec ?: return null
    if (value <= 0L) return null
    return formatVideoDuration(value)
}
