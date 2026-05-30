package com.naaammme.bbspace.core.data.player

import com.naaammme.bbspace.core.domain.danmaku.VodDanmakuRepository
import com.naaammme.bbspace.core.model.DanmakuSessionState
import com.naaammme.bbspace.core.model.DanmakuWindow
import com.naaammme.bbspace.core.model.PlaybackSource
import com.naaammme.bbspace.core.model.VideoPlaybackId
import com.naaammme.bbspace.core.model.VodDanmakuRequest
import com.naaammme.bbspace.core.model.danmakuWindowStartMs
import com.naaammme.bbspace.core.model.toDanmakuWindowId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private var currentDurationMs = 0L
    private var currentWindowId: Long? = null

    fun setSource(source: PlaybackSource?) {
        if (source == null) {
            reset()
            return
        }

        if (currentSource?.videoId != source.videoId) {
            resetSource(source)
        } else {
            currentSource = source
            currentDurationMs = source.durationMs.coerceAtLeast(0L)
            updateSourceKey(source.videoId.toDanmakuSourceKey())
        }
    }

    fun seekTo(positionMs: Long) {
        currentWindowId = null
        ensureWindowAt(positionMs)
    }

    fun onProgress(positionMs: Long) {
        ensureWindowAt(positionMs)
    }

    private fun ensureWindowAt(positionMs: Long) {
        val source = currentSource ?: return
        val windowId = positionMs.coerceAtLeast(0L).toDanmakuWindowId()
        if (windowId == currentWindowId) return
        currentWindowId = windowId
        updateSourceKey(source.videoId.toDanmakuSourceKey())
        ensureWindowLoaded(windowId)
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
                if (segment.request.videoId != currentSource?.videoId || windowId != currentWindowId) {
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
                if (request.videoId != currentSource?.videoId || windowId != currentWindowId) {
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

    fun clear() {
        reset()
    }

    private fun resetSource(source: PlaybackSource) {
        loadingJob?.cancel()
        loadingJob = null
        loadingWindowId = null
        currentSource = source
        currentDurationMs = source.durationMs.coerceAtLeast(0L)
        currentWindowId = null
        _state.value = DanmakuSessionState(sourceKey = source.videoId.toDanmakuSourceKey())
    }

    private fun clearError() {
        _state.update { if (it.lastError != null) it.copy(lastError = null) else it }
    }

    private fun updateSourceKey(sourceKey: String) {
        _state.update { state ->
            if (state.sourceKey == sourceKey) state else state.copy(sourceKey = sourceKey)
        }
    }

    private fun reset() {
        currentSource = null
        currentDurationMs = 0L
        currentWindowId = null
        loadingJob?.cancel()
        loadingJob = null
        loadingWindowId = null
        _state.value = DanmakuSessionState()
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
