package com.naaammme.bbspace.infra.player

sealed interface EngineSource {
    data class LiveFlv(
        val url: String
    ) : EngineSource

    data class Dash(
        val videoUrl: String,
        val audioUrl: String? = null
    ) : EngineSource

    data class ProgressiveSegment(
        val url: String,
        val durationMs: Long?
    )

    data class Progressive(
        val segments: List<ProgressiveSegment>
    ) : EngineSource
}
