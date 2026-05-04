package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

sealed interface WebLinkTarget {
    @Immutable data class ToVideo(val target: VideoTarget) : WebLinkTarget
    @Immutable data class ToSpace(val mid: Long) : WebLinkTarget
    @Immutable data class ToLive(val roomId: Long) : WebLinkTarget
    @Immutable data object External : WebLinkTarget
    @Immutable data object Stay : WebLinkTarget
}
