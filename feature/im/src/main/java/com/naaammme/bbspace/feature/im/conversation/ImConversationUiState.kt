package com.naaammme.bbspace.feature.im.conversation

import androidx.compose.runtime.Immutable
import com.naaammme.bbspace.core.model.ImMessage

@Immutable
data class ImConversationUiState(
    val title: String = "",
    val avatar: String? = null,
    val messages: List<ImMessage> = emptyList(),
    val hasMoreHistory: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false
)
