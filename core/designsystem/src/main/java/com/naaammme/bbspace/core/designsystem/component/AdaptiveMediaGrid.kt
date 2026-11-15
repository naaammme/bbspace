package com.naaammme.bbspace.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun rememberAdaptiveGridColumnCount(
    compact: Int = 2,
    medium: Int = 3,
    expanded: Int = 4
): Int {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return when {
        windowSizeClass.isWidthAtLeastBreakpoint(840) -> expanded
        windowSizeClass.isWidthAtLeastBreakpoint(600) -> medium
        else -> compact
    }
}

@Composable
fun <T> AdaptiveMediaGrid(
    items: List<T>,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState? = null,
    errorMessage: String? = null,
    loadMoreEnabled: Boolean = true,
    columns: Int = rememberAdaptiveGridColumnCount(),
    loadingPlaceholderCount: Int = 10,
    horizontalSpacing: Dp = 6.dp,
    verticalSpacing: Dp = 6.dp,
    contentPadding: PaddingValues = PaddingValues(
        start = 6.dp,
        top = 0.dp,
        end = 6.dp,
        bottom = 4.dp
    ),
    key: (index: Int, item: T) -> Any = { index, _ -> index },
    contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    loadingContent: @Composable () -> Unit,
    headerContent: (@Composable () -> Unit)? = null,
    emptyContent: (@Composable () -> Unit)? = null,
    errorContent: @Composable (String) -> Unit = { msg ->
        DefaultGridError(msg)
    },
    loadMoreContent: @Composable () -> Unit = {
        DefaultGridLoadMore()
    },
    itemContent: @Composable (item: T) -> Unit
) {
    val listState = state ?: rememberLazyListState()
    val currentItems by rememberUpdatedState(items)
    val shouldLoadMore by remember(listState, loadMoreEnabled) {
        derivedStateOf {
            loadMoreEnabled && !listState.canScrollForward && currentItems.isNotEmpty()
        }
    }

    LaunchedEffect(shouldLoadMore, loadMoreEnabled, isRefreshing, isLoadingMore) {
        if (loadMoreEnabled && shouldLoadMore && !isRefreshing && !isLoadingMore) {
            onLoadMore()
        }
    }

    BiliPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        // 按行分组的网格：同一横排的卡片高度对齐到该排最高的一个，
        // 只有标题/投稿者区域被拉伸，保证整个页面横平竖直
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            when {
                items.isEmpty() && isRefreshing -> {
                    items(
                        count = (loadingPlaceholderCount + columns - 1) / columns,
                        key = { rowIndex -> "loading_row_$rowIndex" },
                        contentType = { "loading_row" }
                    ) {
                        MediaGridRow(
                            cellCount = columns,
                            horizontalSpacing = horizontalSpacing
                        ) {
                            loadingContent()
                        }
                    }
                }

                items.isEmpty() -> {
                    when {
                        emptyContent != null -> {
                            item(
                                key = "empty",
                                contentType = "empty"
                            ) {
                                emptyContent()
                            }
                        }

                        !errorMessage.isNullOrBlank() -> {
                            val err = errorMessage.orEmpty()
                            item(
                                key = "error",
                                contentType = "error"
                            ) {
                                errorContent(err)
                            }
                        }
                    }
                }

                else -> {
                    if (!errorMessage.isNullOrBlank()) {
                        val err = errorMessage.orEmpty()
                        item(
                            key = "error",
                            contentType = "error"
                        ) {
                            errorContent(err)
                        }
                    }

                    if (headerContent != null) {
                        item(
                            key = "header",
                            contentType = "header"
                        ) {
                            headerContent()
                        }
                    }

                    val rowCount = (items.size + columns - 1) / columns
                    items(
                        count = rowCount,
                        key = { rowIndex ->
                            val first = rowIndex * columns
                            val last = minOf(first + columns, items.size)
                            (first until last).joinToString("|") { key(it, items[it]).toString() }
                        },
                        contentType = { rowIndex ->
                            val first = rowIndex * columns
                            val last = minOf(first + columns, items.size)
                            (first until last).map { contentType(it, items[it]) }
                        }
                    ) { rowIndex ->
                        val first = rowIndex * columns
                        val last = minOf(first + columns, items.size)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
                        ) {
                            for (index in first until last) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    itemContent(items[index])
                                }
                            }
                            // 末行不满时用空位补齐，保持各列宽度一致
                            repeat(columns - (last - first)) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    if (isLoadingMore) {
                        item(
                            key = "loading_more",
                            contentType = "loading_more"
                        ) {
                            loadMoreContent()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaGridRow(
    cellCount: Int,
    horizontalSpacing: Dp,
    cellContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
    ) {
        repeat(cellCount) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                cellContent()
            }
        }
    }
}

@Composable
private fun DefaultGridError(message: String) {
    Text(
        text = "加载失败: $message",
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun DefaultGridLoadMore() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}
