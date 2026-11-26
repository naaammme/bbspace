package com.naaammme.bbspace.feature.space.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.model.SpaceVideo
import com.naaammme.bbspace.core.space.SpaceRepository
import com.naaammme.bbspace.feature.space.navigation.SPACE_MID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
class SpaceSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: SpaceRepository
) : ViewModel() {

    private val mid = savedStateHandle.get<Long>(SPACE_MID_ARG) ?: 0L
    private var nextPage = 1
    private var searchJob: Job? = null

    var input by mutableStateOf("")
        private set
    var query by mutableStateOf("")
        private set
    var videos by mutableStateOf<List<SpaceVideo>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun updateInput(value: String) {
        input = value
    }

    fun submit() {
        val keyword = input.trim()
        if (mid <= 0L || keyword.isBlank()) return
        searchJob?.cancel()
        nextPage = 1
        input = keyword
        query = keyword
        videos = emptyList()
        hasMore = false
        error = null
        load(keyword, reset = true)
    }

    fun loadMore() {
        if (mid <= 0L || !hasMore || isLoading || error != null || query.isBlank()) return
        load(query, reset = false)
    }

    fun retry() {
        if (query.isBlank() || isLoading) return
        error = null
        load(query, reset = videos.isEmpty())
    }

    private fun load(keyword: String, reset: Boolean) {
        val pageNo = if (reset) 1 else nextPage
        isLoading = true
        searchJob = viewModelScope.launch {
            try {
                val page = repo.searchArchive(mid, keyword, pageNo)
                nextPage = pageNo + 1
                val merged = if (reset) page.videos else (videos + page.videos).distinctBy { it.aid to it.cid }
                hasMore = page.hasMore && (reset || merged.size > videos.size)
                videos = merged
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(TAG, e) { "搜索空间视频失败" }
                error = e.message ?: "搜索失败"
            } finally {
                isLoading = false
            }
        }
    }

    private companion object {
        const val TAG = "SpaceSearchViewModel"
    }
}
