package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class DanmakuWindow(
    val id: Long,
    val items: List<DanmakuItem>
)

@Immutable
data class DanmakuSessionState(
    val sourceKey: String? = null,
    val window: DanmakuWindow? = null,
    val lastError: String? = null,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val speed: Float = 1f,
    val seekEventId: Long = 0L,
    val hasSource: Boolean = false
)
