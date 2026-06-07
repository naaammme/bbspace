package com.naaammme.bbspace.feature.favorite.folderdetail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.BiliPullToRefreshBox
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.core.model.FavoriteContentTarget
import com.naaammme.bbspace.feature.favorite.FavoriteEmptyState
import com.naaammme.bbspace.feature.favorite.FavoriteErrorState
import com.naaammme.bbspace.feature.favorite.FavoriteLoading
import com.naaammme.bbspace.feature.favorite.item.FavoriteContentList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteFolderDetailScreen(
    onBack: () -> Unit,
    fid: Long,
    onOpenContent: (FavoriteContentTarget) -> Unit,
    viewModel: FavoriteFolderDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    CollapsingTopBarScaffold(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = {
                    Text(
                        text = if (state.totalCount > 0) {
                            "收藏夹 ${state.totalCount}"
                        } else {
                            "收藏夹"
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
                scrollBehavior = scrollBehavior
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
                    FavoriteLoading(modifier = Modifier.fillMaxSize())
                }

                state.errorMessage != null && state.items.isEmpty() -> {
                    FavoriteErrorState(
                        message = state.errorMessage.orEmpty(),
                        onRetry = viewModel::refresh,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                state.items.isEmpty() -> {
                    FavoriteEmptyState(
                        text = if (state.hasInvalid) "收藏夹暂无可用视频" else "收藏夹暂无内容",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    FavoriteContentList(
                        items = state.items,
                        isLoadingMore = state.isLoadingMore,
                        errorMessage = state.errorMessage,
                        errorOnLoadMore = state.errorOnLoadMore,
                        canLoadMore = state.canLoadMore,
                        onLoadMore = viewModel::loadMore,
                        onRetry = if (state.errorOnLoadMore) viewModel::loadMore else viewModel::refresh,
                        onOpenContent = onOpenContent,
                        listState = listState
                    )
                }
            }
        }
    }
}
