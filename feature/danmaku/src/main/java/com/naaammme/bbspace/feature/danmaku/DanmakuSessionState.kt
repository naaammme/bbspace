package com.naaammme.bbspace.feature.danmaku

import androidx.compose.runtime.Immutable
import com.naaammme.bbspace.core.model.DanmakuItem

@Immutable
data class DanmakuWindow(
    val id: Long,
    val items: List<DanmakuItem>
)

@Immutable
data class DanmakuSessionState(
    val sourceKey: String? = null,
    val windows: Map<Long, DanmakuWindow> = emptyMap(),
    val lastError: String? = null
)
