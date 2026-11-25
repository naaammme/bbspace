package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class DanmakuConfig(
    val enabled: Boolean = false,
    val areaPercent: Int = 100,
    val opacity: Float = 1f,
    val textScale: Float = 1f,
    val speed: Float = 1f,
    val densityLevel: Int = 1,
    val weightFilterLevel: Int = 2,
    val mergeDuplicates: Boolean = true,
    val showTop: Boolean = true,
    val showBottom: Boolean = true,
    val showScrollRl: Boolean = true,
    val blockColored: Boolean = false
)
