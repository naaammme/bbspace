package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class PlaybackDisplayMeta(
    val title: String = "",
    val subtitle: String? = null,
    val cover: String? = null
)
