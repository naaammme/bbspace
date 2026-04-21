package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class DanmakuConfig(
    val enabled: Boolean = true,
    val areaPercent: Int = 100,
    val opacity: Float = 1f,
    val textScale: Float = 1f,
    val speed: Float = 1f,
    val densityLevel: Int = 1,
    val mergeDuplicates: Boolean = false,
    val showTop: Boolean = true,
    val showBottom: Boolean = true,
    val showScrollRl: Boolean = true
)
