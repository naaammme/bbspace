package com.naaammme.bbspace.infra.player

sealed interface EngineSource {
    val title: String?
    val subtitle: String?

    data class LiveFlv(
        val url: String,
        override val title: String? = null,
        override val subtitle: String? = null
    ) : EngineSource

    data class Dash(
        val videoUrl: String,
        val audioUrl: String? = null,
        override val title: String? = null,
        override val subtitle: String? = null
    ) : EngineSource

    data class ProgressiveSegment(
        val url: String,
        val durationMs: Long?
    )

    data class Progressive(
        val segments: List<ProgressiveSegment>,
        override val title: String? = null,
        override val subtitle: String? = null
    ) : EngineSource
}
