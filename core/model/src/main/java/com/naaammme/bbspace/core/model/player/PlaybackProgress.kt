package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val durationMs: Long = 0L
)
