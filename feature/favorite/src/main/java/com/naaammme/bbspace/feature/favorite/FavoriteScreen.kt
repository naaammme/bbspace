package com.naaammme.bbspace.feature.favorite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.BiliPullToRefreshBox
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.core.designsystem.component.FilledTabRow
import com.naaammme.bbspace.core.designsystem.component.StateMessageCard
import com.naaammme.bbspace.core.designsystem.component.VideoListCardSkeleton
import com.naaammme.bbspace.core.model.FavoriteContentTarget
import com.naaammme.bbspace.feature.favorite.folder.FavoriteFolderList
import com.naaammme.bbspace.feature.favorite.item.FavoriteContentList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteScreen(
    onBack: () -> Unit,
    onOpenContent: (FavoriteContentTarget) -> Unit,
    onOpenFolder: (Long) -> Unit,
    viewModel: FavoriteViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val contentListState = rememberLazyListState()
    val folderListState = rememberLazyListState()

    CollapsingTopBarScaffold(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = { Text("收藏") },
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
                tabs = FavoriteTab.entries.map { it.title },
                selectedIndex = state.tab.ordinal,
                onSelect = { index -> viewModel.selectTab(FavoriteTab.entries[index]) },
                modifier = Modifier
            )

            BiliPullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f)
            ) {
                FavoriteBody(
                    state = state,
                    contentListState = contentListState,
                    folderListState = folderListState,
                    onOpenContent = onOpenContent,
                    onOpenFolder = onOpenFolder,
                    onRetry = viewModel::refresh,
                    onLoadMore = viewModel::loadMore
                )
            }
        }
    }
}

@Composable
private fun FavoriteBody(
    state: FavoriteUiState,
    contentListState: LazyListState,
    folderListState: LazyListState,
    onOpenContent: (FavoriteContentTarget) -> Unit,
    onOpenFolder: (Long) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    val content = state.content
    val folder = state.folder
    val hasCurrentItems = when (state.tab) {
        FavoriteTab.CONTENT -> content.items.isNotEmpty()
        FavoriteTab.FOLDER -> folder.folders.isNotEmpty()
    }
    when {
        ((state.tab == FavoriteTab.CONTENT && content.isLoading) ||
            (state.tab == FavoriteTab.FOLDER && folder.isLoading)) && !hasCurrentItems -> {
            FavoriteLoading(modifier = Modifier.fillMaxSize())
        }

        ((state.tab == FavoriteTab.CONTENT && content.errorMessage != null) ||
            (state.tab == FavoriteTab.FOLDER && folder.errorMessage != null)) && !hasCurrentItems -> {
            StateMessageCard(
                text = when (state.tab) {
                    FavoriteTab.CONTENT -> content.errorMessage.orEmpty()
                    FavoriteTab.FOLDER -> folder.errorMessage.orEmpty()
                }.ifBlank { "加载收藏失败" },
                modifier = Modifier.fillMaxSize(),
                isError = true,
                actionText = "重试",
                onAction = onRetry
            )
        }

        !hasCurrentItems -> {
            StateMessageCard(
                text = when (state.tab) {
                    FavoriteTab.CONTENT -> "暂无收藏内容"
                    FavoriteTab.FOLDER -> "暂无收藏夹"
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        state.tab == FavoriteTab.CONTENT -> {
            FavoriteContentList(
                items = content.items,
                isLoadingMore = content.isLoadingMore,
                errorMessage = content.errorMessage,
                errorOnLoadMore = content.errorOnLoadMore,
                canLoadMore = content.canLoadMore,
                onLoadMore = onLoadMore,
                onRetry = if (content.errorOnLoadMore) onLoadMore else onRetry,
                onOpenContent = onOpenContent,
                listState = contentListState
            )
        }

        state.tab == FavoriteTab.FOLDER -> {
            FavoriteFolderList(
                folders = folder.folders,
                listState = folderListState,
                onOpenFolder = onOpenFolder
            )
        }
    }
}

@Composable
fun FavoriteLoading(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            count = LOADING_COUNT,
            key = { index -> "favorite_loading_$index" },
            contentType = { "loading" }
        ) {
            VideoListCardSkeleton()
        }
    }
}

private const val LOADING_COUNT = 8
