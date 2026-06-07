package com.naaammme.bbspace.feature.favorite.folderdetail

import androidx.compose.runtime.Immutable
import com.naaammme.bbspace.core.model.FavoriteContentItem

@Immutable
data class FavoriteFolderDetailUiState(
    val items: List<FavoriteContentItem> = emptyList(),
    val page: Int = 1,
    val totalCount: Int = 0,
    val hasInvalid: Boolean = false,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val errorOnLoadMore: Boolean = false
) {
    val canLoadMore: Boolean
        get() = hasMore &&
            !isLoading &&
            !isRefreshing &&
            !isLoadingMore
}
