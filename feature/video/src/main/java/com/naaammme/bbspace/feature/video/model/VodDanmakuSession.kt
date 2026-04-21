package com.naaammme.bbspace.feature.video.model

import com.naaammme.bbspace.core.domain.danmaku.VodDanmakuRepository
import com.naaammme.bbspace.core.model.PlaybackSource
import com.naaammme.bbspace.core.model.PlaybackViewState
import com.naaammme.bbspace.core.model.VodDanmakuRequest
import com.naaammme.bbspace.core.model.VideoPlaybackId
import com.naaammme.bbspace.feature.danmaku.DanmakuSessionState
import com.naaammme.bbspace.feature.danmaku.DanmakuWindow
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

internal class VodDanmakuSession(
    private val scope: CoroutineScope,
    private val repository: VodDanmakuRepository
) {
    private val _state = MutableStateFlow(DanmakuSessionState())
    val state: StateFlow<DanmakuSessionState> = _state.asStateFlow()

    private val loadingJobs = mutableMapOf<Long, Job>()
    private var currentSource: PlaybackSource? = null
    private var currentVideoId: VideoPlaybackId? = null
    private var currentDurationMs = 0L
    private var activeWindowId: Long? = null
    private var lastPositionMs: Long? = null
    @Volatile
    private var lastTickSec = -1L
    private var observerJob: Job? = null

    fun bind(
        playbackStateFlow: Flow<PlaybackViewState>,
        enabledFlow: Flow<Boolean>
    ) {
        if (observerJob?.isActive == true) return
        observerJob = scope.launch {
            combine(playbackStateFlow, enabledFlow) { playbackState, enabled ->
                playbackState to enabled
            }.collect { (playbackState, enabled) ->
                handlePlaybackUpdate(playbackState = playbackState, enabled = enabled)
            }
        }
    }

    fun clear() {
        observerJob?.cancel()
        observerJob = null
        reset()
    }

    private fun handlePlaybackUpdate(
        playbackState: PlaybackViewState,
        enabled: Boolean
    ) {
        val source = playbackState.playbackSource
        if (source == null) {
            reset()
            return
        }

        val sourceKey = source.videoId.toDanmakuSourceKey()
        if (!enabled) {
            reset(source.videoId, sourceKey)
            return
        }

        if (currentVideoId != source.videoId) {
            reset(source.videoId, sourceKey)
        }
        val durationMs = source.durationMs.takeIf { it > 0 } ?: playbackState.durationMs.coerceAtLeast(0L)
        val positionMs = playbackState.positionMs.coerceAtLeast(0L)
        currentSource = source
        currentDurationMs = durationMs
        val request = VodDanmakuRequest(
            videoId = source.videoId,
            positionMs = positionMs,
            durationMs = durationMs
        )
        val windowId = request.segmentIndex

        activeWindowId = windowId
        updateCursor(sourceKey)
        val shouldTrim = run {
            val last = lastPositionMs
            last != null && (positionMs < last || abs(positionMs - last) >= SEEK_RESET_THRESHOLD_MS)
        }
        if (shouldTrim) {
            trimCacheAround(windowId)
        }

        if (!playbackState.hasRenderedFirstFrame && _state.value.windows.isEmpty()) {
            lastPositionMs = positionMs
            return
        }

        ensureSegmentLoaded(request)

        lastPositionMs = positionMs
        lastTickSec = positionMs / 1000L
    }

    fun onTick(positionMs: Long) {
        val clampedPositionMs = positionMs.coerceAtLeast(0L)
        val second = clampedPositionMs / 1000L
        if (second == lastTickSec) return
        lastTickSec = second
        scope.launch {
            val source = currentSource ?: return@launch
            val request = VodDanmakuRequest(
                videoId = source.videoId,
                positionMs = clampedPositionMs,
                durationMs = currentDurationMs
            )
            activeWindowId = request.segmentIndex
            updateCursor(source.videoId.toDanmakuSourceKey())
            ensureSegmentLoaded(request)
        }
    }

    private fun ensureSegmentLoaded(request: VodDanmakuRequest) {
        val windowId = request.segmentIndex
        if (_state.value.windows.containsKey(windowId)) return
        if (loadingJobs.containsKey(windowId)) return
        clearError()

        loadingJobs[windowId] = scope.launch {
            runCatching {
                repository.fetchSegment(request)
            }.onSuccess { segment ->
                loadingJobs.remove(windowId)
                if (segment.request.videoId != currentVideoId) return@onSuccess
                _state.update { state ->
                    val updated = state.windows.toMutableMap()
                    updated[windowId] = DanmakuWindow(
                        id = windowId,
                        items = segment.items
                    )
                    trimWindows(updated, activeWindowId)
                    state.copy(windows = updated, lastError = null)
                }
            }.onFailure { error ->
                loadingJobs.remove(windowId)
                if (request.videoId != currentVideoId) return@onFailure
                _state.update { it.copy(lastError = error.message ?: "Failed to load danmaku segment") }
            }
        }
    }

    private fun trimCacheAround(centerWindowId: Long) {
        _state.update { state ->
            val keep = setOf(
                centerWindowId - 1L,
                centerWindowId,
                centerWindowId + 1L
            )
            val trimmed = state.windows.filterKeys { it in keep }
            if (trimmed.size == state.windows.size) return@update state
            state.copy(windows = trimmed)
        }
    }

    private fun trimWindows(
        windows: MutableMap<Long, DanmakuWindow>,
        centerWindowId: Long?
    ) {
        if (centerWindowId == null) return
        val keep = setOf(centerWindowId - 1L, centerWindowId, centerWindowId + 1L)
        windows.keys.removeAll { it !in keep }
    }

    private fun clearError() {
        _state.update { if (it.lastError != null) it.copy(lastError = null) else it }
    }

    private fun updateCursor(
        sourceKey: String
    ) {
        _state.update { state ->
            if (state.sourceKey == sourceKey) {
                state
            } else {
                state.copy(sourceKey = sourceKey)
            }
        }
    }

    private fun reset(
        videoId: VideoPlaybackId? = null,
        sourceKey: String? = null
    ) {
        currentSource = null
        currentVideoId = videoId
        currentDurationMs = 0L
        activeWindowId = null
        lastPositionMs = null
        lastTickSec = -1L
        loadingJobs.values.forEach { it.cancel() }
        loadingJobs.clear()
        _state.update { DanmakuSessionState(sourceKey = sourceKey) }
    }

    private companion object {
        const val SEEK_RESET_THRESHOLD_MS = 10_000L
    }
}

private fun VideoPlaybackId.toDanmakuSourceKey(): String {
    return buildString {
        append("vod:")
        append(aid)
        append(':')
        append(cid)
        bvid?.takeIf(String::isNotBlank)?.let {
            append(':')
            append(it)
        }
    }
}
