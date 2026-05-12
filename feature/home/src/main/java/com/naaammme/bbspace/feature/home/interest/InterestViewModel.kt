package com.naaammme.bbspace.feature.home.interest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.domain.feed.InterestRepository
import com.naaammme.bbspace.core.model.UinterestResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InterestUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val response: UinterestResponse? = null
)

@HiltViewModel
class InterestViewModel @Inject constructor(
    private val repo: InterestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InterestUiState())
    val uiState: StateFlow<InterestUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun refresh() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val response = repo.fetchUinterest()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = null,
                    response = response
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "加载兴趣标签失败" }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = e.message ?: "加载兴趣标签失败"
                )
            }
        }
    }

    private companion object {
        const val TAG = "InterestViewModel"
    }
}
