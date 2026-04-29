package com.naaammme.bbspace.feature.live

import androidx.compose.runtime.Immutable
import com.naaammme.bbspace.core.model.LivePlaybackViewState
import com.naaammme.bbspace.core.model.LiveRoute

@Immutable
data class LiveUiState(
    val route: LiveRoute? = null,
    val playbackState: LivePlaybackViewState = LivePlaybackViewState(),
    val backgroundPlaybackEnabled: Boolean = false
)
