package com.naaammme.bbspace.feature.im.feed

import androidx.compose.runtime.Immutable
import com.naaammme.bbspace.core.model.MsgFeedFilter
import com.naaammme.bbspace.core.model.MsgFeedTab
import com.naaammme.bbspace.core.model.im.MsgFeedCursor
import com.naaammme.bbspace.core.model.im.MsgFeedItem

@Immutable
data class MsgFeedUiState(
    val tab: MsgFeedTab = MsgFeedTab.COMMENTS,
    val filterType: MsgFeedFilter = MsgFeedFilter.ALL,
    val items: List<MsgFeedItem> = emptyList(),
    val cursor: MsgFeedCursor? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreError: String? = null
)
