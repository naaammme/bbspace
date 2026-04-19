package com.naaammme.bbspace.feature.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.domain.search.SearchRepository
import com.naaammme.bbspace.core.model.SearchFilter
import com.naaammme.bbspace.core.model.SearchOrder
import com.naaammme.bbspace.core.model.SearchPage
import com.naaammme.bbspace.core.model.SearchReq
import com.naaammme.bbspace.core.model.SearchTime
import com.naaammme.bbspace.core.model.SearchVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: SearchRepository
) : ViewModel() {

    var input by mutableStateOf("")
        private set

    var keyword by mutableStateOf("")
        private set

    var order by mutableStateOf(SearchOrder.DEFAULT)
        private set

    var filters by mutableStateOf<List<SearchFilter>>(emptyList())
        private set

    var time by mutableStateOf(SearchTime())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    val canLoadMore: Boolean
        get() = next.isNotBlank()

    val hasActiveFilter: Boolean
        get() = sel.isNotEmpty() || time.isActive

    private val _videos = MutableStateFlow<List<SearchVideo>>(emptyList())
    val videos = _videos.asStateFlow()

    private var next = ""
    private var sel by mutableStateOf<Map<String, Set<String>>>(emptyMap())

    fun selectedOf(key: String): Set<String> {
        return sel[key].orEmpty()
    }

    fun updateInput(value: String) {
        input = value
    }

    fun submitSearch() {
        val query = input.trim()
        if (query.isEmpty() || isLoading) return
        val fresh = query != keyword
        if (fresh) {
            clearSearchState()
        }
        viewModelScope.launch {
            search(query, reset = true)
        }
    }

    fun applyFilter(key: String, params: Set<String>, nextTime: SearchTime = time) {
        val filter = filters.find { it.key == key } ?: return
        val nextSel = sel.toMutableMap().apply {
            val valid = normalizeSel(filter, params)
            if (valid.isEmpty()) remove(key) else put(key, valid)
        }
        val timeValue = if (key == SINCE_KEY) nextTime else time
        applyFilters(nextSel, timeValue)
    }

    fun applyFilters(nextSel: Map<String, Set<String>>, nextTime: SearchTime = time) {
        val normalizedSel = buildMap {
            filters.forEach { filter ->
                val valid = normalizeSel(filter, nextSel[filter.key].orEmpty())
                if (valid.isEmpty()) return@forEach
                put(filter.key, valid)
            }
        }
        val timeValue = if (normalizedSel[SINCE_KEY]?.singleOrNull() == CUSTOM_TIME) {
            nextTime
        } else {
            SearchTime()
        }
        val nextOrder = SearchOrder.fromParam(normalizedSel[SORT_KEY]?.firstOrNull())
        if (sel == normalizedSel && time == timeValue && order == nextOrder) return
        sel = normalizedSel
        time = timeValue
        order = nextOrder
        if (keyword.isBlank()) return
        viewModelScope.launch {
            search(keyword, reset = true)
        }
    }

    fun clearFilters() {
        if (!hasActiveFilter) return
        applyFilters(emptyMap(), SearchTime())
    }

    fun loadMore() {
        if (keyword.isBlank() || next.isBlank() || isLoading || isLoadingMore) return
        viewModelScope.launch {
            try {
                isLoadingMore = true
                errorMessage = null
                val page = repo.search(buildReq(keyword, next))
                next = page.next
                _videos.value = _videos.value + page.videos
                if (page.filters.isNotEmpty()) {
                    syncFilters(page.filters)
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "搜索翻页失败" }
                errorMessage = e.message
            } finally {
                isLoadingMore = false
            }
        }
    }

    private suspend fun search(query: String, reset: Boolean) {
        try {
            if (reset) {
                isLoading = true
                next = ""
                _videos.value = emptyList()
            } else {
                isLoadingMore = true
            }
            errorMessage = null
            val page = repo.search(buildReq(query, if (reset) "" else next))
            keyword = page.keyword
            input = page.keyword
            next = page.next
            syncFilters(page.filters)
            _videos.value = if (reset) page.videos else _videos.value + page.videos
        } catch (e: Exception) {
            Logger.e(TAG, e) { "搜索失败" }
            errorMessage = e.message
        } finally {
            if (reset) {
                isLoading = false
            } else {
                isLoadingMore = false
            }
        }
    }

    private fun buildReq(query: String, next: String): SearchReq {
        val filterMap = buildMap {
            filters.forEach { filter ->
                val picked = sel[filter.key].orEmpty()
                if (picked.isEmpty()) return@forEach
                val value = filter.ops
                    .asSequence()
                    .map { it.param }
                    .filter { it in picked }
                    .joinToString(",")
                if (value.isNotBlank()) {
                    put(filter.key, value)
                }
            }
        }
        val timeValue = if (sel[SINCE_KEY]?.singleOrNull() == CUSTOM_TIME && time.isActive) {
            time
        } else {
            SearchTime()
        }
        return SearchReq(
            keyword = query,
            next = next,
            order = order,
            filterMap = filterMap,
            time = timeValue
        )
    }

    private fun syncFilters(nextFilters: List<SearchFilter>) {
        filters = nextFilters
        val nextSel = buildMap {
            nextFilters.forEach { filter ->
                val picked = sel[filter.key].orEmpty()
                val valid = filter.ops
                    .asSequence()
                    .map { it.param }
                    .filter { it in picked }
                    .toCollection(linkedSetOf())
                if (valid.isEmpty()) return@forEach
                if (filter.single) {
                    put(filter.key, setOf(valid.first()))
                } else {
                    put(filter.key, valid)
                }
            }
        }
        sel = nextSel
        if (sel[SINCE_KEY]?.singleOrNull() != CUSTOM_TIME) {
            time = SearchTime()
        }
        order = SearchOrder.fromParam(sel[SORT_KEY]?.firstOrNull())
    }

    private fun normalizeSel(filter: SearchFilter, params: Set<String>): Set<String> {
        val valid = filter.ops
            .asSequence()
            .filter { !it.isDefault }
            .map { it.param }
            .filter { it in params }
            .toList()
        if (valid.isEmpty()) return emptySet()
        return if (filter.single) {
            setOf(valid.first())
        } else {
            valid.toSet()
        }
    }

    private fun clearSearchState() {
        keyword = ""
        next = ""
        order = SearchOrder.DEFAULT
        filters = emptyList()
        time = SearchTime()
        sel = emptyMap()
        errorMessage = null
        _videos.value = emptyList()
    }

    private companion object {
        const val TAG = "SearchViewModel"
        const val SORT_KEY = "sort"
        const val SINCE_KEY = "since"
        const val CUSTOM_TIME = "custom"
    }
}
