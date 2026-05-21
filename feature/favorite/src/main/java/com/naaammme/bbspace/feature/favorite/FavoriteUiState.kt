package com.naaammme.bbspace.feature.favorite

import androidx.compose.runtime.Immutable
import com.naaammme.bbspace.core.model.FavoriteContentCursor
import com.naaammme.bbspace.core.model.FavoriteContentItem
import com.naaammme.bbspace.core.model.FavoriteFolder

enum class FavoriteTab(val title: String) {
    CONTENT("全部收藏"),
    FOLDER("收藏夹")
}

@Immutable
data class FavoriteContentUiState(
    val items: List<FavoriteContentItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val cursor: FavoriteContentCursor = FavoriteContentCursor(),
    val errorMessage: String? = null,
    val errorOnLoadMore: Boolean = false
) {
    val canLoadMore: Boolean
        get() = hasMore &&
            !isLoading &&
            !isRefreshing &&
            !isLoadingMore
}

@Immutable
data class FavoriteFolderUiState(
    val folders: List<FavoriteFolder> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

@Immutable
data class FavoriteUiState(
    val tab: FavoriteTab = FavoriteTab.CONTENT,
    val content: FavoriteContentUiState = FavoriteContentUiState(),
    val folder: FavoriteFolderUiState = FavoriteFolderUiState()
) {
    val isRefreshing: Boolean
        get() = when (tab) {
            FavoriteTab.CONTENT -> content.isRefreshing
            FavoriteTab.FOLDER -> folder.isRefreshing
        }
}
