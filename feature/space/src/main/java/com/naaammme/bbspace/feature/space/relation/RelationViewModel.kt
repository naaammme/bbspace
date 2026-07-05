package com.naaammme.bbspace.feature.space.relation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.space.SpaceRepository
import com.naaammme.bbspace.core.common.log.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RelationViewModel @Inject constructor(
    private val spaceRepository: SpaceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(RelationUiState())
    val uiState: StateFlow<RelationUiState> = _uiState.asStateFlow()

    val vmid: Long = savedStateHandle.get<Long>("vmid") ?: 0L
    private val initialTab: Int = savedStateHandle.get<Int>("initialTab") ?: 0

    init {
        _uiState.update { it.copy(selectedTab = initialTab) }
        refresh()
    }

    fun switchTab(tabIndex: Int) {
        if (_uiState.value.selectedTab == tabIndex) return
        _uiState.update { it.copy(selectedTab = tabIndex) }
        
        val currentTabState = _uiState.value.currentTabState
        if (currentTabState.users.isEmpty() && currentTabState.hasMore && currentTabState.error == null) {
            refresh()
        }
    }

    fun refresh() {
        if (vmid == 0L) {
            val isFans = _uiState.value.selectedTab == 1
            updateTabState(isFans) { it.copy(error = "用户未找到", isLoading = false, isRefreshing = false) }
            return
        }
        val isFans = _uiState.value.selectedTab == 1
        val tabState = if (isFans) _uiState.value.fansState else _uiState.value.followingsState
        if (tabState.isRefreshing || tabState.isLoading) return

        viewModelScope.launch {
            updateTabState(isFans) { it.copy(isRefreshing = true, error = null) }
            try {
                val users = spaceRepository.fetchRelationUsers(vmid, 1, isFans)
                updateTabState(isFans) {
                    it.copy(
                        users = users,
                        page = 1,
                        isRefreshing = false,
                        hasMore = users.size >= PAGE_SIZE
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Failed to refresh relations (isFans=$isFans) for mid=$vmid" }
                updateTabState(isFans) {
                    it.copy(
                        isRefreshing = false,
                        error = e.localizedMessage ?: "加载失败"
                    )
                }
            }
        }
    }

    fun loadMore() {
        val isFans = _uiState.value.selectedTab == 1
        val tabState = if (isFans) _uiState.value.fansState else _uiState.value.followingsState
        if (tabState.isLoading || tabState.isRefreshing || !tabState.hasMore || vmid == 0L) return

        viewModelScope.launch {
            updateTabState(isFans) { it.copy(isLoading = true) }
            try {
                val nextPage = tabState.page + 1
                val users = spaceRepository.fetchRelationUsers(vmid, nextPage, isFans)
                updateTabState(isFans) {
                    it.copy(
                        users = it.users + users,
                        page = nextPage,
                        isLoading = false,
                        hasMore = users.size >= PAGE_SIZE
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Failed to load more relations (isFans=$isFans) for mid=$vmid" }
                updateTabState(isFans) {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage ?: "加载失败"
                    )
                }
            }
        }
    }

    private fun updateTabState(isFans: Boolean, block: (RelationTabState) -> RelationTabState) {
        _uiState.update { state ->
            if (isFans) {
                state.copy(fansState = block(state.fansState))
            } else {
                state.copy(followingsState = block(state.followingsState))
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val TAG = "RelationViewModel"
    }
}
