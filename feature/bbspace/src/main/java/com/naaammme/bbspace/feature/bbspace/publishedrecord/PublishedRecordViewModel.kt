package com.naaammme.bbspace.feature.bbspace.publishedrecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.published.PublishedRecordRepository
import com.naaammme.bbspace.core.model.PublishedRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PublishedRecordViewModel @Inject constructor(
    private val repo: PublishedRecordRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PublishedRecordUiState())
    val uiState: StateFlow<PublishedRecordUiState> = _uiState.asStateFlow()

    private var lastItem: PublishedRecord? = null
    private var queryKeyword = ""
    private var queryVersion = 0
    private var hasLoaded = false

    fun loadIfNeeded() {
        if (hasLoaded) return
        hasLoaded = true
        viewModelScope.launch {
            refresh(reloadCount = true)
        }
    }

    suspend fun exportJson(): String = repo.exportJson()

    suspend fun importJson(json: String): Int {
        val count = repo.importJson(json)
        refresh(reloadCount = true)
        return count
    }

    suspend fun delete(key: String) {
        repo.delete(key)
        refresh(reloadCount = true)
    }

    fun updateKeywordInput(value: String) {
        if (value == uiState.value.keywordInput) return
        _uiState.update { it.copy(keywordInput = value) }
    }

    fun search() {
        queryKeyword = uiState.value.keywordInput.trim()
        _uiState.update { it.copy(hasQuery = queryKeyword.isNotEmpty()) }
        viewModelScope.launch {
            refresh(reloadCount = false)
        }
    }

    fun toggleSort() {
        _uiState.update { it.copy(sortDesc = !it.sortDesc) }
        viewModelScope.launch {
            refresh(reloadCount = false)
        }
    }

    fun loadMore() {
        val state = uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val currentLastItem = lastItem ?: return
        val requestVersion = queryVersion
        val sortDesc = state.sortDesc
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingMore = true
                )
            }
            runCatching {
                repo.getRecords(
                    keyword = queryKeyword,
                    sortDesc = sortDesc,
                    limit = PAGE_SIZE,
                    lastItem = currentLastItem
                )
            }.onSuccess { items ->
                if (requestVersion != queryVersion) return@onSuccess
                lastItem = items.lastOrNull()
                _uiState.update {
                    it.copy(
                        items = it.items + items,
                        isLoadingMore = false,
                        hasMore = items.size >= PAGE_SIZE
                    )
                }
            }.onFailure {
                if (requestVersion != queryVersion) return@onFailure
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        hasMore = false
                    )
                }
            }
        }
    }

    private suspend fun refresh(reloadCount: Boolean) {
        lastItem = null
        val requestVersion = ++queryVersion
        val state = uiState.value
        val sortDesc = state.sortDesc
        _uiState.update {
            it.copy(
                isLoading = true,
                isLoadingMore = false,
                error = null,
                hasMore = false
            )
        }
        runCatching {
            val count = if (reloadCount) {
                repo.getCount()
            } else {
                null
            }
            val items = repo.getRecords(
                keyword = queryKeyword,
                sortDesc = sortDesc,
                limit = PAGE_SIZE
            )
            count to items
        }.onSuccess { (count, items) ->
            if (requestVersion != queryVersion) return@onSuccess
            lastItem = items.lastOrNull()
            _uiState.update {
                it.copy(
                    totalCount = count ?: it.totalCount,
                    items = items,
                    isLoading = false,
                    hasMore = items.size >= PAGE_SIZE,
                    error = null
                )
            }
        }.onFailure { err ->
            if (requestVersion != queryVersion) return@onFailure
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = err.message ?: "加载记录失败"
                )
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
