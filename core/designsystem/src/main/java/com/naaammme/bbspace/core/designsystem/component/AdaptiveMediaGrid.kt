package com.naaammme.bbspace.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass

@Composable
fun rememberAdaptiveGridColumnCount(
    compact: Int = 2,
    medium: Int = 3,
    expanded: Int = 4
): Int {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return when (windowSizeClass.windowWidthSizeClass) {
        WindowWidthSizeClass.COMPACT -> compact
        WindowWidthSizeClass.MEDIUM -> medium
        WindowWidthSizeClass.EXPANDED -> expanded
        else -> compact
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AdaptiveMediaGrid(
    items: List<T>,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
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
    loadingContent: @Composable LazyStaggeredGridItemScope.() -> Unit,
    emptyContent: (@Composable LazyStaggeredGridItemScope.() -> Unit)? = null,
    errorContent: @Composable LazyStaggeredGridItemScope.(String) -> Unit = { msg ->
        DefaultGridError(msg)
    },
    loadMoreContent: @Composable LazyStaggeredGridItemScope.() -> Unit = {
        DefaultGridLoadMore()
    },
    itemContent: @Composable LazyStaggeredGridItemScope.(item: T) -> Unit
) {
    val gridState = rememberLazyStaggeredGridState()
    val shouldLoadMore by remember(gridState, items, loadMoreEnabled) {
        derivedStateOf {
            loadMoreEnabled && !gridState.canScrollForward && items.isNotEmpty()
        }
    }

    LaunchedEffect(shouldLoadMore, loadMoreEnabled, isRefreshing, isLoadingMore) {
        if (loadMoreEnabled && shouldLoadMore && !isRefreshing && !isLoadingMore) {
            onLoadMore()
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        LazyVerticalStaggeredGrid(
            state = gridState,
            columns = StaggeredGridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalItemSpacing = verticalSpacing,
            contentPadding = contentPadding
        ) {
            when {
                items.isEmpty() && isRefreshing -> {
                    items(
                        count = loadingPlaceholderCount,
                        key = { index -> "loading_$index" },
                        contentType = { "loading" }
                    ) {
                        loadingContent()
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

                    items(
                        count = items.size,
                        key = { index -> key(index, items[index]) },
                        contentType = { index -> contentType(index, items[index]) }
                    ) { index ->
                        itemContent(items[index])
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
