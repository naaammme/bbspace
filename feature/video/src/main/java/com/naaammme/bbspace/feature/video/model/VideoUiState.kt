package com.naaammme.bbspace.feature.video.model

import com.naaammme.bbspace.core.model.PlaybackAudio
import com.naaammme.bbspace.core.model.PlaybackError
import com.naaammme.bbspace.core.model.PlaybackSource
import com.naaammme.bbspace.core.model.PlaybackStream
import com.naaammme.bbspace.infra.player.PlaybackSnapshot

data class VideoUiState(
    val isLoading: Boolean = true,
    val playbackSource: PlaybackSource? = null,
    val currentStream: PlaybackStream? = null,
    val currentAudio: PlaybackAudio? = null,
    val snapshot: PlaybackSnapshot = PlaybackSnapshot(),
    val error: PlaybackError? = null
)
