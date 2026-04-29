package com.naaammme.bbspace.feature.bbspace.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.history.PlaybackHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BbSpaceState(
    val playbackCount: Int = 0
)

@HiltViewModel
class BbSpaceViewModel @Inject constructor(
    repo: PlaybackHistoryRepository
) : ViewModel() {

    val uiState: StateFlow<BbSpaceState> = repo.observeVideoCount()
        .map { count -> BbSpaceState(playbackCount = count) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BbSpaceState()
        )
}
