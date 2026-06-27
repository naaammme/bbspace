package com.naaammme.bbspace.core.playback

import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.PlaybackSource

interface VideoPlayerRepository {
    suspend fun fetchPlaybackSource(request: PlaybackRequest): PlaybackSource
}

