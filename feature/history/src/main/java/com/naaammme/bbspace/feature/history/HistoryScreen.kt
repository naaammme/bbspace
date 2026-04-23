package com.naaammme.bbspace.feature.history

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.naaammme.bbspace.core.common.media.thumbnailUrl
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.core.designsystem.component.FilledTabRow
import com.naaammme.bbspace.core.designsystem.component.VideoListCardSkeleton
import com.naaammme.bbspace.core.model.HistoryItem
import com.naaammme.bbspace.core.model.HistoryTab
import com.naaammme.bbspace.core.model.HistoryTarget
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.VideoRoute
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenVideo: (VideoRoute) -> Unit,
    onOpenLive: (LiveRoute) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val uiState by rememberUpdatedState(state)

    LaunchedEffect(listState) {
        snapshotFlow {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }
            .distinctUntilChanged()
            .filter { (total, last) ->
                uiState.canLoadMore &&
                        !uiState.isLoadingMore &&
                        uiState.errorMessage == null &&
                        total > 0 &&
                        last >= total - LOAD_MORE_TRIGGER_OFFSET
            }
            .collect {
                viewModel.loadMore()
            }
    }

    LaunchedEffect(state.tab) {
        val needScrollTop = listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 0
        if (needScrollTop && listState.layoutInfo.totalItemsCount > 0) {
            listState.scrollToItem(0)
        }
    }

    CollapsingTopBarScaffold(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = { Text("历史记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
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
                tabs = HistoryTab.entries.map { it.title },
                selectedIndex = state.tab.ordinal,
                onSelect = { index -> viewModel.selectTab(HistoryTab.entries[index]) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f)
            ) {
                when {
                    state.isLoading && state.items.isEmpty() -> {
                        HistoryLoading(modifier = Modifier.fillMaxSize())
                    }

                    state.errorMessage != null && state.items.isEmpty() -> {
                        HistoryError(
                            message = state.errorMessage.orEmpty(),
                            onRetry = viewModel::refresh,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    state.items.isEmpty() -> {
                        HistoryEmpty(
                            text = "暂无${state.tab.title}历史",
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
                                contentType = { it.type }
                            ) { item ->
                                HistoryItemCard(
                                    item = item,
                                    onClick = {
                                        when (val target = item.target) {
                                            is HistoryTarget.Video -> onOpenVideo(target.route)
                                            is HistoryTarget.Live -> onOpenLive(target.route)
                                            null -> Unit
                                        }
                                    }
                                )
                            }

                            if (state.isLoadingMore) {
                                items(
                                    count = LOAD_MORE_SKELETON_COUNT,
                                    key = { index -> "history_loading_$index" },
                                    contentType = { "loading" }
                                ) {
                                    VideoListCardSkeleton()
                                }
                            }

                            if (state.errorMessage != null && state.items.isNotEmpty()) {
                                item(
                                    key = "history_error",
                                    contentType = "error"
                                ) {
                                    HistoryError(
                                        message = state.errorMessage.orEmpty(),
                                        onRetry = if (state.errorOnLoadMore) {
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

@Composable
private fun HistoryLoading(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            count = INIT_SKELETON_COUNT,
            key = { index -> "history_skeleton_$index" },
            contentType = { "skeleton" }
        ) {
            VideoListCardSkeleton()
        }
    }
}

@Composable
private fun HistoryEmpty(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HistoryError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message.ifBlank { "加载历史记录失败" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryItemCard(
    item: HistoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content: @Composable () -> Unit = {
        HistoryItemContent(item = item)
    }

    if (item.isOpenable) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun HistoryItemContent(
    item: HistoryItem,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HistoryCover(
            item = item,
            modifier = Modifier.weight(0.38f)
        )

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

            Text(
                text = buildInfoLine(item),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = buildMetaLine(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            progressText(item)?.let { progress ->
                Text(
                    text = progress,
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

@Composable
private fun HistoryCover(
    item: HistoryItem,
    modifier: Modifier = Modifier
) {
    val cover = item.cover
    val context = LocalContext.current
    val imageRequest = remember(cover, context) {
        cover?.let {
            ImageRequest.Builder(context)
                .data(thumbnailUrl(it))
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(16f / 10f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = item.typeLabel,
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = item.typeLabel,
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

private fun buildInfoLine(item: HistoryItem): String {
    return listOfNotNull(
        item.ownerName,
        item.badge,
        item.subtitle
    ).joinToString(" · ").ifBlank { item.typeLabel }
}

private fun buildMetaLine(item: HistoryItem): String {
    return listOfNotNull(
        item.deviceLabel,
        DateFormat.format("MM-dd HH:mm", item.viewedAtSec * 1000).toString()
    ).joinToString(" · ")
}

private fun progressText(item: HistoryItem): String? {
    val progress = item.progressSec ?: return null
    if (progress < 0L) return "已看完"
    val duration = item.durationSec
    if (duration == null || duration <= 0L) return null
    return if (progress >= duration) {
        "已看完"
    } else {
        "进度 ${formatDuration(progress)} / ${formatDuration(duration)}"
    }
}

private fun formatDuration(sec: Long): String {
    val total = sec.coerceAtLeast(0L)
    val hour = total / 3600
    val minute = (total % 3600) / 60
    val second = total % 60
    return if (hour > 0L) {
        "%d:%02d:%02d".format(hour, minute, second)
    } else {
        "%02d:%02d".format(minute, second)
    }
}

private const val INIT_SKELETON_COUNT = 8
private const val LOAD_MORE_SKELETON_COUNT = 2
private const val LOAD_MORE_TRIGGER_OFFSET = 3
