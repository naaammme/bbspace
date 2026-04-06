package com.naaammme.bbspace.feature.video.model

import com.naaammme.bbspace.core.domain.danmaku.DanmakuRepository
import com.naaammme.bbspace.core.model.DanmakuRequest
import com.naaammme.bbspace.core.model.DanmakuSegment
import com.naaammme.bbspace.core.model.PlaybackSource
import com.naaammme.bbspace.core.model.VideoPlaybackId
import com.naaammme.bbspace.infra.player.PlaybackSnapshot
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class VideoDanmakuState(
    val videoId: VideoPlaybackId? = null,
    val loadedSegments: Map<Long, DanmakuSegment> = emptyMap(),
    val lastError: String? = null
)

internal class VideoDanmakuController(
    private val scope: CoroutineScope,
    private val repository: DanmakuRepository
) {
    private val _state = MutableStateFlow(VideoDanmakuState())
    val state: StateFlow<VideoDanmakuState> = _state.asStateFlow()

    private val loadingJobs = mutableMapOf<Long, Job>()
    private var currentVideoId: VideoPlaybackId? = null
    private var currentSegmentIndex: Long? = null
    private var lastPositionMs: Long? = null
    private var observerJob: Job? = null

    fun bind(
        playbackSourceFlow: Flow<PlaybackSource?>,
        snapshotFlow: Flow<PlaybackSnapshot>,
        enabledFlow: Flow<Boolean>
    ) {
        if (observerJob?.isActive == true) return
        observerJob = scope.launch {
            combine(playbackSourceFlow, snapshotFlow, enabledFlow) { source, snapshot, enabled ->
                Triple(source, snapshot, enabled)
            }.collect { (source, snapshot, enabled) ->
                handlePlaybackUpdate(source, snapshot, enabled)
            }
        }
    }

    fun clear() {
        observerJob?.cancel()
        observerJob = null
        reset()
    }

    private fun handlePlaybackUpdate(
        source: PlaybackSource?,
        snapshot: PlaybackSnapshot,
        enabled: Boolean
    ) {
        if (source == null) {
            reset()
            return
        }

        if (!enabled) {
            reset(source.videoId)
            return
        }

        val durationMs = source.durationMs.takeIf { it > 0 } ?: snapshot.durationMs.coerceAtLeast(0L)
        val positionMs = snapshot.positionMs.coerceAtLeast(0L)
        if (currentVideoId != source.videoId) {
            reset(source.videoId)
        }
        if (_state.value.loadedSegments.isEmpty() && positionMs < START_DELAY_MS) {
            lastPositionMs = positionMs
            return
        }

        val request = DanmakuRequest(
            videoId = source.videoId,
            positionMs = positionMs,
            durationMs = durationMs
        )
        val segmentIndex = request.segmentIndex

        currentSegmentIndex = segmentIndex
        if (shouldTrimForSeek(positionMs)) {
            trimCacheAround(segmentIndex)
        }

        ensureSegmentLoaded(request)
        maybePrefetchNextSegment(request)

        lastPositionMs = positionMs
    }

    private fun shouldTrimForSeek(positionMs: Long): Boolean {
        val last = lastPositionMs ?: return false
        return positionMs < last || abs(positionMs - last) >= SEEK_RESET_THRESHOLD_MS
    }

    private fun maybePrefetchNextSegment(request: DanmakuRequest) {
        val nextSegmentStartMs = request.segmentEndMsExclusive
        if (nextSegmentStartMs >= request.durationMs) return
        if (nextSegmentStartMs - request.positionMs > PREFETCH_WINDOW_MS) return

        ensureSegmentLoaded(
            request.copy(positionMs = nextSegmentStartMs)
        )
    }

    private fun ensureSegmentLoaded(request: DanmakuRequest) {
        val segmentIndex = request.segmentIndex
        if (_state.value.loadedSegments.containsKey(segmentIndex)) return
        if (loadingJobs.containsKey(segmentIndex)) return
        clearError()

        loadingJobs[segmentIndex] = scope.launch {
            runCatching {
                repository.fetchSegment(request)
            }.onSuccess { segment ->
                loadingJobs.remove(segmentIndex)
                if (segment.request.videoId != currentVideoId) return@onSuccess
                _state.update { state ->
                    val updated = state.loadedSegments.toMutableMap()
                    updated[segmentIndex] = segment
                    trimSegments(updated, currentSegmentIndex)
                    state.copy(loadedSegments = updated, lastError = null)
                }
            }.onFailure { error ->
                loadingJobs.remove(segmentIndex)
                if (request.videoId != currentVideoId) return@onFailure
                _state.update { it.copy(lastError = error.message ?: "Failed to load danmaku segment") }
            }
        }
    }

    private fun trimCacheAround(centerSegmentIndex: Long) {
        _state.update { state ->
            val keep = setOf(
                centerSegmentIndex - 1L,
                centerSegmentIndex,
                centerSegmentIndex + 1L
            )
            val trimmed = state.loadedSegments.filterKeys { it in keep }
            if (trimmed.size == state.loadedSegments.size) return@update state
            state.copy(loadedSegments = trimmed)
        }
    }

    private fun trimSegments(segments: MutableMap<Long, DanmakuSegment>, centerIndex: Long?) {
        if (centerIndex == null) return
        val keep = setOf(centerIndex - 1L, centerIndex, centerIndex + 1L)
        segments.keys.removeAll { it !in keep }
    }

    private fun clearError() {
        _state.update { if (it.lastError != null) it.copy(lastError = null) else it }
    }

    private fun reset(videoId: VideoPlaybackId? = null) {
        currentVideoId = videoId
        currentSegmentIndex = null
        lastPositionMs = null
        loadingJobs.values.forEach { it.cancel() }
        loadingJobs.clear()
        _state.update { VideoDanmakuState(videoId = videoId) }
    }

    private companion object {
        const val START_DELAY_MS = 800L
        const val PREFETCH_WINDOW_MS = 30_000L
        const val SEEK_RESET_THRESHOLD_MS = 10_000L
    }
}
