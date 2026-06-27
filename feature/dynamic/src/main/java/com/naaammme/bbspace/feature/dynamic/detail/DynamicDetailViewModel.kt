package com.naaammme.bbspace.feature.dynamic.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.dynamic.DynamicRepository
import com.naaammme.bbspace.feature.dynamic.navigation.DYNAMIC_DETAIL_OPUS_ID_ARG
import com.naaammme.bbspace.feature.dynamic.navigation.DYNAMIC_DETAIL_OPUS_TYPE_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DynamicDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: DynamicRepository
) : ViewModel() {

    private val opusId: String = savedStateHandle.get<String>(DYNAMIC_DETAIL_OPUS_ID_ARG) ?: ""
    private val opusType: Int = savedStateHandle.get<Int>(DYNAMIC_DETAIL_OPUS_TYPE_ARG) ?: 0

    private val _uiState = MutableStateFlow(DynamicDetailUiState())
    val uiState: StateFlow<DynamicDetailUiState> = _uiState.asStateFlow()

    init {
        if (opusId.isNotBlank()) {
            load()
        }
    }

    fun retry() {
        if (opusId.isNotBlank()) {
            load()
        }
    }

    private fun load() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val detail = repo.fetchOpusDetail(opusId, opusType)
                _uiState.update {
                    it.copy(detail = detail, isLoading = false, errorMessage = null)
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "加载动态详情失败" }
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "加载失败")
                }
            }
        }
    }

    private companion object {
        const val TAG = "DynamicDetailViewModel"
    }
}
