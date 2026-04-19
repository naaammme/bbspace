package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class LiveRoute(
    val roomId: Long,
    val title: String? = null,
    val cover: String? = null,
    val ownerName: String? = null,
    val onlineText: String? = null
)
