package com.naaammme.bbspace.feature.dynamic.detail

import androidx.compose.runtime.Immutable
import com.naaammme.bbspace.core.model.DynamicDetail

@Immutable
data class DynamicDetailUiState(
    val detail: DynamicDetail? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
