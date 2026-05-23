package com.naaammme.bbspace.feature.im

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.BiliPullToRefreshBox
import com.naaammme.bbspace.core.designsystem.component.FilledTabRow
import com.naaammme.bbspace.core.model.ImSessionItem
import com.naaammme.bbspace.feature.im.component.ImSessionCard
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImScreen(
    onOpenConversation: (ImSessionItem) -> Unit,
    vm: ImViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("消息") })
        }
    ) { padding ->
        BiliPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!state.isLoggedIn) {
                ImCenterState(text = "请先登录后查看消息")
                return@BiliPullToRefreshBox
            }

            if (state.sessions.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    FilledTabRow(
                        tabs = state.tabs.map { it.title },
                        selectedIndex = state.tabs.indexOf(state.currentTab).coerceAtLeast(0),
                        onSelect = { index -> state.tabs.getOrNull(index)?.let(vm::selectTab) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                    when {
                        state.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        !state.errorMessage.isNullOrBlank() -> {
                            ImCenterState(text = state.errorMessage.orEmpty())
                        }

                        else -> {
                            ImCenterState(text = "暂无消息")
                        }
                    }
                }
                return@BiliPullToRefreshBox
            }

            val listState = rememberLazyListState()
            LaunchedEffect(
                listState,
                state.sessions.size,
                state.canLoadMore,
                state.isLoadingMore,
                state.loadMoreError
            ) {
                snapshotFlow {
                    val total = listState.layoutInfo.totalItemsCount
                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    state.canLoadMore &&
                        state.loadMoreError.isNullOrBlank() &&
                        total > 0 &&
                        last >= total - 4
                }
                    .distinctUntilChanged()
                    .collect { shouldLoadMore ->
                        if (shouldLoadMore) vm.loadMore()
                    }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "im_tabs") {
                    FilledTabRow(
                        tabs = state.tabs.map { it.title },
                        selectedIndex = state.tabs.indexOf(state.currentTab).coerceAtLeast(0),
                        onSelect = { index -> state.tabs.getOrNull(index)?.let(vm::selectTab) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                items(
                    items = state.sessions,
                    key = { it.key }
                ) { item ->
                    ImSessionCard(
                        item = item,
                        onClick = if (item.talkerId != null && item.sessionType != null) {
                            { onOpenConversation(item) }
                        } else {
                            null
                        }
                    )
                }

                if (!state.errorMessage.isNullOrBlank()) {
                    item(key = "im_error_footer") {
                        ImInlineError(text = state.errorMessage.orEmpty())
                    }
                }

                if (state.isLoadingMore) {
                    item(key = "im_loading_more") {
                        ImLoadingMore()
                    }
                } else if (!state.loadMoreError.isNullOrBlank()) {
                    item(key = "im_load_more_error") {
                        ImLoadMoreError(
                            text = state.loadMoreError.orEmpty(),
                            onRetry = vm::loadMore
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImCenterState(
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ImInlineError(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun ImLoadingMore(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ImLoadMoreError(
    text: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onRetry),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "重试",
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

