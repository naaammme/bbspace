package com.naaammme.bbspace.feature.bbspace.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.history.LocalHistoryRepository
import com.naaammme.bbspace.core.model.VideoHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class BbSpaceState(
    val videos: List<VideoHistory> = emptyList()
)

@HiltViewModel
class BbSpaceViewModel @Inject constructor(
    private val repo: LocalHistoryRepository
) : ViewModel() {

    val uiState: StateFlow<BbSpaceState> = repo.observeVideos()
        .map { list -> BbSpaceState(videos = list) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            BbSpaceState()
        )

    fun deleteVideo(item: VideoHistory) {
        viewModelScope.launch {
            repo.deleteVideo(item.id)
        }
    }

    fun clearVideos() {
        viewModelScope.launch {
            repo.clearVideos()
        }
    }

    fun exportVideos(items: List<VideoHistory>): String {
        return JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("uid", item.uid)
                        put("key", item.key)
                        put("biz", item.biz)
                        put("aid", item.aid)
                        put("cid", item.cid)
                        put("bvid", item.bvid)
                        put("epId", item.epId)
                        put("seasonId", item.seasonId)
                        put("title", item.title)
                        put("cover", item.cover)
                        put("part", item.part)
                        put("partTitle", item.partTitle)
                        put("ownerUid", item.ownerUid)
                        put("ownerName", item.ownerName)
                        put("durationMs", item.durationMs)
                        put("progressMs", item.progressMs)
                        put("watchMs", item.watchMs)
                        put("updatedAt", item.updatedAt)
                        put("finished", item.finished)
                    }
                )
            }
        }.toString(2)
    }
}
