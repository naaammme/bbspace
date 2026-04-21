package com.naaammme.bbspace.feature.danmaku

import android.view.View
import com.naaammme.bbspace.core.model.DanmakuConfig
import com.naaammme.bbspace.core.model.DanmakuItem
import com.naaammme.bbspace.core.model.VOD_DANMAKU_SEGMENT_DURATION_MS
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import master.flame.danmaku.api.DanmakuSegmentData
import master.flame.danmaku.api.SegmentDanmakuSession
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.controller.IDanmakuView
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.android.DanmakuContext

class DanmakuOverlayState internal constructor(
    internal val danmakuView: View,
    private val danmakuCtrl: IDanmakuView,
    private val danmakuContext: DanmakuContext,
    private val timeProvider: DanmakuPlayerTimeProvider,
    private val session: SegmentDanmakuSession<DanmakuItem>,
    private val onDanmakuTick: (Long) -> Unit
) {
    private val released = AtomicBoolean(false)
    private var lastSourceKey: String? = null
    private var pendingSeek = true
    private var lastCfgState: DanmakuCfgState? = null
    private var lastPlayState: DanmakuPlayState? = null
    private var lastPlaybackSpeed = 1f
    private var lastSeekEventId = 0L
    private val appliedWindowIds = linkedSetOf<Long>()
    private val callback = object : DrawHandler.Callback {
        override fun prepared() = Unit

        override fun updateTimer(timer: DanmakuTimer) {
            onDanmakuTick(timer.currMillisecond.coerceAtLeast(0L))
        }

        override fun danmakuShown(danmaku: BaseDanmaku) = Unit

        override fun drawingFinished() = Unit
    }

    fun prepare() {
        if (released.get()) return
        session.setCallback(callback)
        session.prepare()
    }

    fun sync(
        danmakuState: DanmakuSessionState,
        config: DanmakuConfig,
        positionMs: Long,
        isPlaying: Boolean,
        speed: Float,
        seekEventId: Long,
        hasSource: Boolean
    ) {
        if (released.get()) return
        val clampedPositionMs = positionMs.coerceAtLeast(0L)
        val clampedSpeed = speed.coerceIn(0.25f, 3f)
        val requiredWindowId = clampedPositionMs.toDanmakuWindowId()
        syncSource(danmakuState.sourceKey)
        val hasSeek = consumeSeekEvent(seekEventId)
        val hasSpeedChange = lastPlaybackSpeed != clampedSpeed
        lastPlaybackSpeed = clampedSpeed
        if (hasSeek) {
            pendingSeek = true
        }
        applyConfig(config)
        syncWindows(
            windows = danmakuState.windows,
            targetWindowId = requiredWindowId,
            requireTargetWindow = pendingSeek || hasSeek
        )
        val curReady = appliedWindowIds.contains(requiredWindowId)
        if (config.enabled && hasSource) {
            syncPosition(
                positionMs = clampedPositionMs,
                hasDiscontinuity = hasSeek,
                curReady = curReady
            )
        }
        val canPlay = isPlaying && !pendingSeek
        val needStateOverride = hasSeek ||
            hasSpeedChange ||
            !canPlay ||
            !hasSource ||
            (canPlay && lastPlayState?.isPlaying != true)
        if (needStateOverride) {
            val anchorMs = if (hasSeek) {
                clampedPositionMs
            } else {
                timeProvider.getCurrentTimeMs()
            }
            timeProvider.overrideState(anchorMs, canPlay, clampedSpeed)
        }
        syncPlayback(
            enabled = config.enabled,
            hasSource = hasSource,
            isPlaying = canPlay
        )
    }

    private fun syncPlayback(
        enabled: Boolean,
        hasSource: Boolean,
        isPlaying: Boolean
    ) {
        val nextState = DanmakuPlayState(enabled, hasSource, isPlaying)
        if (lastPlayState == nextState) {
            return
        }
        lastPlayState = nextState

        if (!enabled || !hasSource) {
            session.hide()
            session.pause()
            danmakuCtrl.clearDanmakusOnScreen()
            pendingSeek = true
            return
        }

        session.show()
        if (isPlaying) {
            session.resume()
        } else {
            session.pause()
        }
    }

    private fun syncPosition(
        positionMs: Long,
        hasDiscontinuity: Boolean,
        curReady: Boolean
    ) {
        val shouldSeek = pendingSeek || hasDiscontinuity
        if (!shouldSeek) return

        if (!curReady) {
            pendingSeek = true
            return
        }
        session.seekTo(positionMs)
        pendingSeek = false
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        appliedWindowIds.clear()
        session.setCallback(null)
        session.setPlayerTimeProvider(null)
        session.pause()
        timeProvider.release()
        thread(
            start = true,
            isDaemon = true,
            name = "DanmakuRelease"
        ) {
            session.release()
        }
    }

    private fun syncSource(sourceKey: String?) {
        if (lastSourceKey == sourceKey) return

        lastSourceKey = sourceKey
        lastSeekEventId = 0L
        pendingSeek = true
        appliedWindowIds.clear()
        session.clearSegments()
    }

    private fun applyConfig(
        config: DanmakuConfig
    ) {
        val nextState = DanmakuCfgState(config)
        if (lastCfgState == nextState) return

        danmakuContext.applyConfig(config)
        lastCfgState = nextState
        danmakuCtrl.forceRender()
    }

    private fun syncWindows(
        windows: Map<Long, DanmakuWindow>,
        targetWindowId: Long,
        requireTargetWindow: Boolean
    ) {
        val nextWindows = windows.entries.sortedBy { it.key }
        if (nextWindows.isEmpty()) {
            if (appliedWindowIds.isEmpty()) return

            appliedWindowIds.clear()
            session.clearSegments()
            return
        }

        val curReady = nextWindows.any { it.key == targetWindowId }
        if (requireTargetWindow && !curReady) {
            if (appliedWindowIds.isNotEmpty()) {
                appliedWindowIds.clear()
                session.clearSegments()
            }
            return
        }

        val nextWindowIds = linkedSetOf<Long>().apply {
            addAll(nextWindows.map { it.key })
        }
        if (appliedWindowIds.any { it !in nextWindowIds }) {
            session.replaceSegments(
                nextWindows.map { (windowId, window) ->
                    DanmakuSegmentData(windowId, window.items)
                }
            )
            appliedWindowIds.clear()
            appliedWindowIds.addAll(nextWindowIds)
            return
        }

        nextWindows.forEach { (windowId, window) ->
            if (appliedWindowIds.add(windowId)) {
                session.appendSegment(DanmakuSegmentData(windowId, window.items))
            }
        }
    }

    private fun consumeSeekEvent(seekEventId: Long): Boolean {
        if (seekEventId == 0L || seekEventId == lastSeekEventId) {
            return false
        }
        lastSeekEventId = seekEventId
        return true
    }
}

private data class DanmakuCfgState(
    val config: DanmakuConfig
)

private data class DanmakuPlayState(
    val enabled: Boolean,
    val hasSource: Boolean,
    val isPlaying: Boolean
)

private fun Long.toDanmakuWindowId(): Long {
    return (coerceAtLeast(0L) / VOD_DANMAKU_SEGMENT_DURATION_MS) + 1L
}
