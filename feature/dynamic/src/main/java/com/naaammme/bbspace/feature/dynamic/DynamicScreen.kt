package com.naaammme.bbspace.feature.dynamic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.BiliPullToRefreshBox
import com.naaammme.bbspace.core.designsystem.component.DynamicFeedSkeleton
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.feature.dynamic.feed.DynamicFeed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicScreen(
    onOpenVideo: (VideoTarget) -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onOpenLive: (LiveRoute) -> Unit,
    viewModel: DynamicViewModel = hiltViewModel()
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
                        total > 0 &&
                        last >= total - LOAD_MORE_TRIGGER_OFFSET
            }
            .collect {
                viewModel.loadMore()
            }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("动态") }
            )
        }
    ) { padding ->
        BiliPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading && state.items.isEmpty() -> {
                    DynamicFeedSkeleton(modifier = Modifier.fillMaxSize())
                }

                state.errorMessage != null && state.items.isEmpty() -> {
                    DynamicEmptyState(
                        text = state.errorMessage.orEmpty().ifBlank { "加载动态失败" },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                state.items.isEmpty() -> {
                    DynamicEmptyState(
                        text = "暂无动态",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    DynamicFeed(
                        upList = state.upList,
                        items = state.items,
                        listState = listState,
                        isLoadingMore = state.isLoadingMore,
                        errorMessage = state.errorMessage,
                        errorOnLoadMore = state.errorOnLoadMore,
                        onRetry = if (state.errorOnLoadMore) viewModel::loadMore else viewModel::refresh,
                        onOpenVideo = onOpenVideo,
                        onOpenSpace = onOpenSpace,
                        onOpenLive = onOpenLive,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun DynamicEmptyState(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val LOAD_MORE_TRIGGER_OFFSET = 4
