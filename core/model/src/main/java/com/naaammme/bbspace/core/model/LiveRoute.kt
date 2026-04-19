package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class LiveRoute(
    val roomId: Long,
    val title: String? = null,
    val cover: String? = null,
    val ownerName: String? = null,
    val onlineText: String? = null,
    val jumpFrom: Int = LiveRouteTool.JUMP_FROM_UNKNOWN
)

object LiveRouteTool {
    const val JUMP_FROM_UNKNOWN = 99998
    const val JUMP_FROM_LIVE_RECOMMEND = 24000
    const val JUMP_FROM_HOME_RECOMMEND = 29000

    fun normalizeJumpFrom(raw: Int?): Int {
        return raw?.takeIf { it > 0 } ?: JUMP_FROM_UNKNOWN
    }
}
