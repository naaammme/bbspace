package com.naaammme.bbspace.infra.player.danmaku

import android.view.View
import com.naaammme.bbspace.core.model.DanmakuConfig
import com.naaammme.bbspace.core.model.DanmakuItem
import com.naaammme.bbspace.core.model.DanmakuSessionState
import com.naaammme.bbspace.core.model.DanmakuWindow
import com.naaammme.bbspace.core.model.toDanmakuWindowId
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import master.flame.danmaku.api.DanmakuSegmentData
import master.flame.danmaku.api.SegmentDanmakuSession
import master.flame.danmaku.controller.IDanmakuView
import master.flame.danmaku.danmaku.model.android.DanmakuContext

class DanmakuOverlayState internal constructor(
    internal val danmakuView: View,
    private val danmakuCtrl: IDanmakuView,
    private val danmakuContext: DanmakuContext,
    private val timeProvider: DanmakuPlayerTimeProvider,
    private val session: SegmentDanmakuSession<DanmakuItem>
) {
    private val released = AtomicBoolean(false)
    private var lastSourceKey: String? = null
    private var pendingSeek = true
    private var lastCfgState: DanmakuCfgState? = null
    private var lastPlayState: DanmakuPlayState? = null
    private var lastPlaybackSpeed = 1f
    private var lastSeekEventId = 0L
    private var appliedWindowId: Long? = null

    fun prepare() {
        if (released.get()) return
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
        syncWindow(
            window = danmakuState.window,
            targetWindowId = requiredWindowId,
            requireTargetWindow = pendingSeek || hasSeek
        )
        val curReady = appliedWindowId == requiredWindowId
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
            danmakuView.visibility = View.INVISIBLE
            session.hide()
            session.pause()
            danmakuCtrl.clearDanmakusOnScreen()
            pendingSeek = true
            return
        }

        danmakuView.visibility = View.VISIBLE
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
        appliedWindowId = null
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
        appliedWindowId = null
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

    private fun syncWindow(
        window: DanmakuWindow?,
        targetWindowId: Long,
        requireTargetWindow: Boolean
    ) {
        val targetWindow = window?.takeIf { it.id == targetWindowId }
        if (targetWindow == null) {
            if (requireTargetWindow || appliedWindowId != null) {
                appliedWindowId = null
                session.clearSegments()
            }
            pendingSeek = true
            return
        }
        if (appliedWindowId == targetWindow.id) {
            return
        }
        session.replaceSegments(
            listOf(DanmakuSegmentData(targetWindow.id, targetWindow.items))
        )
        appliedWindowId = targetWindow.id
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
