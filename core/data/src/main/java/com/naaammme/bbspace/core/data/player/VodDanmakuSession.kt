package com.naaammme.bbspace.core.data.player

import com.naaammme.bbspace.core.domain.danmaku.VodDanmakuRepository
import com.naaammme.bbspace.core.model.DanmakuSessionState
import com.naaammme.bbspace.core.model.DanmakuWindow
import com.naaammme.bbspace.core.model.PlaybackSource
import com.naaammme.bbspace.core.model.PlaybackViewState
import com.naaammme.bbspace.core.model.VideoPlaybackId
import com.naaammme.bbspace.core.model.VodDanmakuRequest
import com.naaammme.bbspace.core.model.danmakuWindowStartMs
import com.naaammme.bbspace.core.model.toDanmakuWindowId
import kotlinx.coroutines.CancellationException
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

    private var loadingJob: Job? = null
    private var loadingWindowId: Long? = null
    private var currentSource: PlaybackSource? = null
    private var currentVideoId: VideoPlaybackId? = null
    private var currentDurationMs = 0L
    private var currentWindowId: Long? = null
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

    fun onTick(positionMs: Long) {
        val clampedPositionMs = positionMs.coerceAtLeast(0L)
        val second = clampedPositionMs / 1000L
        if (second == lastTickSec) return
        lastTickSec = second
        val source = currentSource ?: return
        val windowId = clampedPositionMs.toDanmakuWindowId()
        currentWindowId = windowId
        updateCursor(source.videoId.toDanmakuSourceKey())
        ensureWindowLoaded(windowId)
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
        val windowId = positionMs.toDanmakuWindowId()
        currentWindowId = windowId
        updateCursor(sourceKey)

        if (!playbackState.hasRenderedFirstFrame && _state.value.window == null) {
            lastTickSec = positionMs / 1000L
            return
        }

        ensureWindowLoaded(windowId)
        lastTickSec = positionMs / 1000L
    }

    private fun ensureWindowLoaded(windowId: Long) {
        if (_state.value.window?.id == windowId) return
        if (loadingWindowId == windowId && loadingJob?.isActive == true) return
        buildRequest(windowId)?.let(::loadWindow)
    }

    private fun buildRequest(windowId: Long): VodDanmakuRequest? {
        val source = currentSource ?: return null
        val startMs = danmakuWindowStartMs(windowId)
        val positionMs = if (currentDurationMs > 0L) {
            startMs.coerceAtMost((currentDurationMs - 1L).coerceAtLeast(0L))
        } else {
            startMs
        }
        return VodDanmakuRequest(
            videoId = source.videoId,
            positionMs = positionMs,
            durationMs = currentDurationMs
        )
    }

    private fun loadWindow(request: VodDanmakuRequest) {
        val windowId = request.segmentIndex
        if (loadingWindowId != windowId) {
            loadingJob?.cancel()
        }
        loadingWindowId = windowId
        clearError()

        loadingJob = scope.launch {
            runCatching {
                repository.fetchSegment(request)
            }.onSuccess { segment ->
                if (segment.request.videoId != currentVideoId || windowId != currentWindowId) {
                    return@onSuccess
                }
                _state.update { state ->
                    state.copy(
                        window = DanmakuWindow(
                            id = windowId,
                            items = segment.items
                        ),
                        lastError = null
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                if (request.videoId != currentVideoId || windowId != currentWindowId) {
                    return@onFailure
                }
                _state.update { it.copy(lastError = error.message ?: "Failed to load danmaku segment") }
            }
            if (loadingWindowId == windowId) {
                loadingWindowId = null
                loadingJob = null
            }
        }
    }

    private fun clearError() {
        _state.update { if (it.lastError != null) it.copy(lastError = null) else it }
    }

    private fun updateCursor(sourceKey: String) {
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
        currentWindowId = null
        lastTickSec = -1L
        loadingJob?.cancel()
        loadingJob = null
        loadingWindowId = null
        _state.update { DanmakuSessionState(sourceKey = sourceKey) }
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
