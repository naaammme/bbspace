package com.naaammme.bbspace.feature.dynamic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.BiliPullToRefreshBox
import com.naaammme.bbspace.core.designsystem.component.StateMessageCard
import com.naaammme.bbspace.feature.dynamic.feed.DynamicFeed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicScreen(
    viewModel: DynamicViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(
        state.items.size,
        state.canLoadMore,
        state.isLoadingMore,
        state.loadMoreError,
        listState
    ) {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            state.canLoadMore &&
                state.loadMoreError.isNullOrBlank() &&
                total > 0 &&
                last >= total - LOAD_MORE_TRIGGER_OFFSET
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
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
                !state.isLoggedIn -> {
                    StateMessageCard(
                        text = "请先登录后查看动态",
                        modifier = Modifier.fillMaxSize().padding(24.dp)
                    )
                }

                state.isLoading && state.items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.errorMessage != null && state.items.isEmpty() -> {
                    StateMessageCard(
                        text = state.errorMessage.orEmpty().ifBlank { "加载动态失败" },
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        isError = true
                    )
                }

                state.items.isEmpty() -> {
                    StateMessageCard(
                        text = "暂无动态",
                        modifier = Modifier.fillMaxSize().padding(24.dp)
                    )
                }

                else -> {
                    DynamicFeed(
                        upList = state.upList,
                        items = state.items,
                        listState = listState,
                        isLoadingMore = state.isLoadingMore,
                        errorMessage = state.errorMessage,
                        loadMoreError = state.loadMoreError,
                        onRetryRefresh = viewModel::refresh,
                        onRetryLoadMore = viewModel::loadMore,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private const val LOAD_MORE_TRIGGER_OFFSET = 4
