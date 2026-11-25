package com.naaammme.bbspace.feature.listen.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.listen.ListenPlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class ListenDetailViewModel @Inject constructor(
    private val playbackController: ListenPlaybackController
) : ViewModel() {
    val uiState = playbackController.state

    fun load(oid: Long, itemType: Int, subId: Long, title: String, author: String) {
        viewModelScope.launch {
            playbackController.open(oid, itemType, subId, title, author)
        }
    }

    fun togglePlayPause() = playbackController.togglePlayPause()

    fun seekTo(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    override fun onCleared() {
        playbackController.release()
        super.onCleared()
    }
}
