package com.naaammme.bbspace.feature.dynamic

import androidx.compose.runtime.Immutable
import com.naaammme.bbspace.core.model.DynamicCursor
import com.naaammme.bbspace.core.model.DynamicItem

@Immutable
data class DynamicUiState(
    val items: List<DynamicItem> = emptyList(),
    val cursor: DynamicCursor = DynamicCursor(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val errorOnLoadMore: Boolean = false
) {
    val canLoadMore: Boolean
        get() = hasMore && !isLoading && !isRefreshing && !isLoadingMore && !errorOnLoadMore
}
