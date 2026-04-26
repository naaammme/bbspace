package com.naaammme.bbspace.feature.download.model

import com.naaammme.bbspace.core.domain.download.VideoDownloadRepository
import com.naaammme.bbspace.core.model.DanmakuItem
import com.naaammme.bbspace.core.model.DownloadPlaybackState
import com.naaammme.bbspace.core.model.VOD_DANMAKU_SEGMENT_DURATION_MS
import com.naaammme.bbspace.feature.danmaku.DanmakuSessionState
import com.naaammme.bbspace.feature.danmaku.DanmakuWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class OfflineDanmakuSession(
    private val scope: CoroutineScope,
    private val repository: VideoDownloadRepository
) {
    private val _state = MutableStateFlow(DanmakuSessionState())
    val state: StateFlow<DanmakuSessionState> = _state.asStateFlow()

    private var observerJob: Job? = null
    private var sourceKey: String? = null
    private var groupedItems: Map<Long, List<DanmakuItem>> = emptyMap()
    private var maxItemWindowId = 1L
    private var centerWindowId = -1L
    private var taskDurationMs = 0L

    fun bind(
        taskId: Long,
        playbackStateFlow: Flow<DownloadPlaybackState>
    ) {
        if (observerJob?.isActive == true) return
        observerJob = scope.launch {
            load(taskId)
            playbackStateFlow.collect { playback ->
                publishWindows(
                    durationMs = playback.durationMs,
                    positionMs = playback.positionMs
                )
            }
        }
    }

    fun clear() {
        observerJob?.cancel()
        observerJob = null
        reset()
    }

    private suspend fun load(taskId: Long) {
        if (taskId <= 0L) {
            reset()
            return
        }
        val task = repository.getTask(taskId)
        taskDurationMs = task?.durationMs ?: 0L
        val fallbackKey = buildSourceKey(
            taskId = taskId,
            aid = task?.aid ?: 0L,
            cid = task?.cid ?: 0L
        )
        val cache = runCatching {
            repository.loadDanmaku(taskId)
        }.getOrElse { error ->
            reset()
            _state.value = DanmakuSessionState(
                sourceKey = fallbackKey,
                lastError = error.message ?: "加载离线弹幕失败"
            )
            return
        } ?: run {
            sourceKey = fallbackKey
            return
        }

        sourceKey = buildSourceKey(
            taskId = taskId,
            aid = cache.aid,
            cid = cache.cid
        )
        groupedItems = withContext(Dispatchers.Default) {
            cache.items.groupByWindowId()
        }
        maxItemWindowId = groupedItems.keys.maxOrNull() ?: 1L
        centerWindowId = -1L
    }

    private fun publishWindows(
        durationMs: Long,
        positionMs: Long
    ) {
        val sourceKey = sourceKey ?: return
        val maxWindowId = maxOf(
            maxItemWindowId,
            taskDurationMs.toDanmakuWindowId(),
            durationMs.toDanmakuWindowId(),
            positionMs.toDanmakuWindowId(),
            1L
        )
        val centerWindowId = positionMs.toDanmakuWindowId().coerceIn(1L, maxWindowId)
        val startWindowId = (centerWindowId - 1L).coerceAtLeast(1L)
        val endWindowId = (centerWindowId + 1L).coerceAtMost(maxWindowId)
        val current = _state.value
        if (
            current.sourceKey == sourceKey &&
            this.centerWindowId == centerWindowId &&
            current.windows.keys.firstOrNull() == startWindowId &&
            current.windows.keys.lastOrNull() == endWindowId &&
            current.lastError == null
        ) {
            return
        }
        this.centerWindowId = centerWindowId
        _state.value = DanmakuSessionState(
            sourceKey = sourceKey,
            windows = (startWindowId..endWindowId).associateWith { windowId ->
                DanmakuWindow(
                    id = windowId,
                    items = groupedItems[windowId].orEmpty()
                )
            }
        )
    }

    private fun reset() {
        sourceKey = null
        groupedItems = emptyMap()
        maxItemWindowId = 1L
        centerWindowId = -1L
        taskDurationMs = 0L
        _state.value = DanmakuSessionState()
    }

    private fun buildSourceKey(
        taskId: Long,
        aid: Long,
        cid: Long
    ): String {
        return "download:$taskId:$aid:$cid"
    }

    private fun List<DanmakuItem>.groupByWindowId(): Map<Long, List<DanmakuItem>> {
        val grouped = linkedMapOf<Long, MutableList<DanmakuItem>>()
        sortedBy { it.progressMs }.forEach { item ->
            val windowId = item.progressMs.coerceAtLeast(0).toLong().toDanmakuWindowId()
            grouped.getOrPut(windowId) { mutableListOf() }.add(item)
        }
        return grouped
    }

    private fun Long.toDanmakuWindowId(): Long {
        return (coerceAtLeast(0L) / VOD_DANMAKU_SEGMENT_DURATION_MS) + 1L
    }
}
