package com.naaammme.bbspace.feature.home.live

import androidx.compose.runtime.Immutable
import com.naaammme.bbspace.core.model.LiveRecommendItem
import com.naaammme.bbspace.core.model.LiveRecommendUpList

@Immutable
data class HomeLiveUiState(
    val upList: LiveRecommendUpList? = null,
    val items: List<LiveRecommendItem> = emptyList(),
    val isRefreshing: Boolean = true,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null
)
