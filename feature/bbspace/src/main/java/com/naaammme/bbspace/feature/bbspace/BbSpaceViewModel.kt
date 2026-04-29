package com.naaammme.bbspace.feature.bbspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.history.PlaybackHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class BbSpaceViewModel @Inject constructor(
    repo: PlaybackHistoryRepository
) : ViewModel() {

    val uiState: StateFlow<BbSpaceUiState> = repo.observeVideoCount()
        .map { count -> BbSpaceUiState(playbackCount = count) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BbSpaceUiState()
        )
}
