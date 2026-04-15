package com.naaammme.bbspace.feature.video

import android.view.View
import com.naaammme.bbspace.core.model.DanmakuElem
import com.naaammme.bbspace.core.model.DanmakuSegment
import com.naaammme.bbspace.core.model.VIDEO_DANMAKU_SEGMENT_DURATION_MS
import com.naaammme.bbspace.core.model.VideoPlaybackId
import com.naaammme.bbspace.feature.video.model.VideoDanmakuConfig
import com.naaammme.bbspace.feature.video.model.VideoDanmakuState
import com.naaammme.bbspace.infra.player.EngineDiscontinuityReason
import com.naaammme.bbspace.infra.player.PlaybackSnapshot
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.controller.IDanmakuView
import master.flame.danmaku.api.DanmakuSegmentData
import master.flame.danmaku.api.SegmentDanmakuSession
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal class VideoDanmakuOverlayState(
    internal val danmakuView: View,
    private val danmakuCtrl: IDanmakuView,
    private val danmakuContext: DanmakuContext,
    private val timeProvider: PlayerSessionTimeProvider,
    private val session: SegmentDanmakuSession<DanmakuElem>,
    private val onDanmakuTick: (Long) -> Unit
) {
    private val released = AtomicBoolean(false)
    private var lastVideoId: VideoPlaybackId? = null
    private var lastObservedPositionMs: Long? = null
    private var pendingSeek = true
    private var lastCfgState: DanmakuCfgState? = null
    private var lastPlayState: DanmakuPlayState? = null
    private var lastPlaybackSpeed = 1f
    private var lastDiscSeq = 0L
    private val appliedSegmentIndices = linkedSetOf<Long>()
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
        danmakuState: VideoDanmakuState,
        config: VideoDanmakuConfig,
        snapshot: PlaybackSnapshot,
        hasSource: Boolean,
    ) {
        if (released.get()) return
        val clampedPositionMs = snapshot.positionMs.coerceAtLeast(0L)
        val clampedSpeed = snapshot.speed.coerceIn(0.25f, 3f)
        val currentSegmentIndex = clampedPositionMs.toDanmakuSegmentIndex()
        syncVideo(danmakuState.videoId, snapshot.discontinuitySeq)
        val hasSeek = consumeSeekDiscontinuity(snapshot)
        val hasSpeedChange = lastPlaybackSpeed != clampedSpeed
        lastPlaybackSpeed = clampedSpeed
        if (hasSeek) {
            pendingSeek = true
        }
        applyConfig(config)
        syncSegments(
            loadedSegments = danmakuState.loadedSegments,
            currentSegmentIndex = currentSegmentIndex,
            requireCurrentSegment = pendingSeek || hasSeek
        )
        val curReady = currentSegmentIndex in appliedSegmentIndices
        if (config.enabled && hasSource) {
            syncPosition(
                positionMs = clampedPositionMs,
                hasDiscontinuity = hasSeek,
                curReady = curReady
            )
        }
        val canPlay = snapshot.isPlaying && !pendingSeek
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
            lastObservedPositionMs = null
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
        lastObservedPositionMs = positionMs
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
        appliedSegmentIndices.clear()
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

    private fun syncVideo(
        videoId: VideoPlaybackId?,
        discontinuitySeq: Long
    ) {
        if (lastVideoId == videoId) return

        lastVideoId = videoId
        lastObservedPositionMs = null
        lastDiscSeq = discontinuitySeq
        pendingSeek = true
        appliedSegmentIndices.clear()
        session.clearSegments()
    }

    private fun applyConfig(
        config: VideoDanmakuConfig
    ) {
        val nextState = DanmakuCfgState(config)
        if (lastCfgState == nextState) return

        danmakuContext.applyConfig(config)
        lastCfgState = nextState
        danmakuCtrl.forceRender()
    }

    private fun syncSegments(
        loadedSegments: Map<Long, DanmakuSegment>,
        currentSegmentIndex: Long,
        requireCurrentSegment: Boolean
    ) {
        val nextSegments = loadedSegments.entries.sortedBy { it.key }
        if (nextSegments.isEmpty()) {
            if (appliedSegmentIndices.isEmpty()) return

            appliedSegmentIndices.clear()
            session.clearSegments()
            return
        }

        val curReady = nextSegments.any { it.key == currentSegmentIndex }
        if (requireCurrentSegment && !curReady) {
            if (appliedSegmentIndices.isNotEmpty()) {
                appliedSegmentIndices.clear()
                session.clearSegments()
            }
            return
        }

        val nextSegmentIndices = linkedSetOf<Long>().apply {
            addAll(nextSegments.map { it.key })
        }
        if (appliedSegmentIndices.any { it !in nextSegmentIndices }) {
            session.replaceSegments(
                nextSegments.map { (segmentIndex, segment) ->
                    DanmakuSegmentData(segmentIndex, segment.elems)
                }
            )
            appliedSegmentIndices.clear()
            appliedSegmentIndices.addAll(nextSegmentIndices)
            return
        }

        nextSegments.forEach { (segmentIndex, segment) ->
            if (appliedSegmentIndices.add(segmentIndex)) {
                session.appendSegment(DanmakuSegmentData(segmentIndex, segment.elems))
            }
        }
    }

    private fun consumeSeekDiscontinuity(snapshot: PlaybackSnapshot): Boolean {
        val seq = snapshot.discontinuitySeq
        if (seq == 0L || seq == lastDiscSeq) {
            return false
        }
        lastDiscSeq = seq
        return snapshot.discontinuityReason == EngineDiscontinuityReason.Seek ||
                snapshot.discontinuityReason == EngineDiscontinuityReason.SeekAdjustment
    }
}

private data class DanmakuCfgState(
    val config: VideoDanmakuConfig
)

private data class DanmakuPlayState(
    val enabled: Boolean,
    val hasSource: Boolean,
    val isPlaying: Boolean
)

private fun Long.toDanmakuSegmentIndex(): Long {
    return (coerceAtLeast(0L) / VIDEO_DANMAKU_SEGMENT_DURATION_MS) + 1L
}
