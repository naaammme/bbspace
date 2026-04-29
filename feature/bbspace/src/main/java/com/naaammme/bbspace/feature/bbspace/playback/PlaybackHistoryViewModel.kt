package com.naaammme.bbspace.feature.bbspace.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.history.PlaybackHistoryRepository
import com.naaammme.bbspace.core.model.PlaybackHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class PlaybackHistoryState(
    val items: List<PlaybackHistory> = emptyList()
)

@HiltViewModel
class PlaybackHistoryViewModel @Inject constructor(
    private val repo: PlaybackHistoryRepository
) : ViewModel() {

    val uiState: StateFlow<PlaybackHistoryState> = repo.observeVideos()
        .map { list -> PlaybackHistoryState(items = list) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlaybackHistoryState()
        )

    fun delete(item: PlaybackHistory) {
        viewModelScope.launch {
            repo.deleteVideo(item.id)
        }
    }

    fun clear() {
        viewModelScope.launch {
            repo.clearVideos()
        }
    }

    fun export(items: List<PlaybackHistory>): String {
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
