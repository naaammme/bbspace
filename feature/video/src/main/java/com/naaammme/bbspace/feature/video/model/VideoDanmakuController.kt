package com.naaammme.bbspace.feature.video.model

import com.naaammme.bbspace.core.domain.danmaku.DanmakuRepository
import com.naaammme.bbspace.core.model.DanmakuRequest
import com.naaammme.bbspace.core.model.DanmakuSegment
import com.naaammme.bbspace.core.model.PlaybackSource
import com.naaammme.bbspace.core.model.VIDEO_DANMAKU_SEGMENT_DURATION_MS
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

    private val loadedSegments = linkedMapOf<Long, DanmakuSegment>()
    private val loadingSegments = linkedSetOf<Long>()
    private var currentVideoId: VideoPlaybackId? = null
    private var currentSegmentIndex: Long? = null
    private var lastPositionMs: Long? = null
    private var observerJob: Job? = null

    fun bind(
        playbackSourceFlow: Flow<PlaybackSource?>,
        snapshotFlow: Flow<PlaybackSnapshot>
    ) {
        if (observerJob != null) return
        observerJob = scope.launch {
            combine(playbackSourceFlow, snapshotFlow) { source, snapshot ->
                source to snapshot
            }.collect { (source, snapshot) ->
                handlePlaybackUpdate(source, snapshot)
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
        snapshot: PlaybackSnapshot
    ) {
        if (source == null) {
            reset()
            return
        }

        val durationMs = source.durationMs.takeIf { it > 0 } ?: snapshot.durationMs.coerceAtLeast(0L)
        val positionMs = snapshot.positionMs.coerceAtLeast(0L)
        if (currentVideoId != source.videoId) {
            reset(source.videoId)
        }

        val request = DanmakuRequest(
            videoId = source.videoId,
            positionMs = positionMs,
            durationMs = durationMs
        )
        val nextSegmentIndex = request.segmentIndex

        currentSegmentIndex = nextSegmentIndex
        if (shouldTrimForSeek(positionMs) && trimCacheAround(nextSegmentIndex)) {
            publishState()
        }

        ensureSegmentLoaded(request)
        maybePrefetchNextSegment(
            videoId = source.videoId,
            durationMs = durationMs,
            positionMs = positionMs,
            currentSegmentIndex = nextSegmentIndex
        )

        lastPositionMs = positionMs
    }

    private fun shouldTrimForSeek(positionMs: Long): Boolean {
        val last = lastPositionMs ?: return false
        return positionMs < last || abs(positionMs - last) >= SEEK_RESET_THRESHOLD_MS
    }

    private fun maybePrefetchNextSegment(
        videoId: VideoPlaybackId,
        durationMs: Long,
        positionMs: Long,
        currentSegmentIndex: Long
    ) {
        val nextSegmentStartMs = currentSegmentIndex * VIDEO_DANMAKU_SEGMENT_DURATION_MS
        if (nextSegmentStartMs >= durationMs) return
        if (nextSegmentStartMs - positionMs > PREFETCH_WINDOW_MS) return

        ensureSegmentLoaded(
            DanmakuRequest(
                videoId = videoId,
                positionMs = nextSegmentStartMs,
                durationMs = durationMs
            )
        )
    }

    private fun ensureSegmentLoaded(request: DanmakuRequest) {
        val segmentIndex = request.segmentIndex
        if (loadedSegments.containsKey(segmentIndex) || !loadingSegments.add(segmentIndex)) {
            return
        }
        clearError()

        scope.launch {
            runCatching {
                repository.fetchSegment(request)
            }.onSuccess { segment ->
                loadingSegments.remove(segmentIndex)
                if (segment.request.videoId != currentVideoId) return@onSuccess
                loadedSegments[segmentIndex] = segment
                trimCacheAround(currentSegmentIndex)
                publishState(lastError = null)
            }.onFailure { error ->
                loadingSegments.remove(segmentIndex)
                if (request.videoId != currentVideoId) return@onFailure
                publishState(lastError = error.message ?: "Failed to load danmaku segment")
            }
        }
    }

    private fun trimCacheAround(centerSegmentIndex: Long?): Boolean {
        if (centerSegmentIndex == null) return false
        val keep = setOf(
            centerSegmentIndex - 1L,
            centerSegmentIndex,
            centerSegmentIndex + 1L
        )
        val changed = loadedSegments.keys.any { it !in keep }
        if (!changed) return false

        loadedSegments.entries.removeAll { (segmentIndex, _) -> segmentIndex !in keep }
        return true
    }

    private fun publishState(
        videoId: VideoPlaybackId? = _state.value.videoId,
        lastError: String? = _state.value.lastError
    ) {
        val nextState = VideoDanmakuState(
            videoId = videoId,
            loadedSegments = loadedSegments.toMap(),
            lastError = lastError
        )
        if (nextState == _state.value) return

        _state.value = nextState
    }

    private fun clearError() {
        if (_state.value.lastError != null) {
            publishState(lastError = null)
        }
    }

    private fun reset(videoId: VideoPlaybackId? = null) {
        currentVideoId = videoId
        currentSegmentIndex = null
        lastPositionMs = null
        loadedSegments.clear()
        loadingSegments.clear()
        publishState(
            videoId = videoId,
            lastError = null
        )
    }

    private companion object {
        const val PREFETCH_WINDOW_MS = 30_000L
        const val SEEK_RESET_THRESHOLD_MS = 10_000L
    }
}
