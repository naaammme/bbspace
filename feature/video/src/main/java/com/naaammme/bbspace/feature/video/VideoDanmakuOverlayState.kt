package com.naaammme.bbspace.feature.video

import com.naaammme.bbspace.core.model.DanmakuElem
import com.naaammme.bbspace.core.model.DanmakuSegment
import com.naaammme.bbspace.core.model.VideoPlaybackId
import com.naaammme.bbspace.feature.video.model.VideoDanmakuConfig
import com.naaammme.bbspace.feature.video.model.VideoDanmakuState
import master.flame.danmaku.api.DanmakuSegmentData
import master.flame.danmaku.api.SegmentDanmakuSession
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.ui.widget.DanmakuView
import kotlin.math.abs

internal class VideoDanmakuOverlayState(
    internal val danmakuView: DanmakuView,
    private val danmakuContext: DanmakuContext,
    private val timeProvider: PlayerSessionTimeProvider,
    private val session: SegmentDanmakuSession<DanmakuElem>
) {
    private var lastVideoId: VideoPlaybackId? = null
    private var lastObservedPositionMs: Long? = null
    private var pendingResync = true
    private var lastAppliedConfig: VideoDanmakuConfig? = null
    private var lastPlaybackEnabled: Boolean? = null
    private var lastPlaybackSource: Boolean? = null
    private var lastPlayingState: Boolean? = null
    private val appliedSegmentIndices = linkedSetOf<Long>()

    fun prepare() {
        session.prepare()
    }

    fun sync(
        danmakuState: VideoDanmakuState,
        config: VideoDanmakuConfig,
        positionMs: Long,
        isPlaying: Boolean,
        hasSource: Boolean
    ) {
        timeProvider.update(positionMs, isPlaying)
        syncVideo(danmakuState.videoId)
        applyConfig(config)
        syncSegments(danmakuState.loadedSegments)
        syncPlayback(
            enabled = config.enabled,
            hasSource = hasSource,
            isPlaying = isPlaying
        )
        if (config.enabled && hasSource) {
            syncPosition(positionMs)
        }
    }

    private fun syncPlayback(
        enabled: Boolean,
        hasSource: Boolean,
        isPlaying: Boolean
    ) {
        if (
            lastPlaybackEnabled == enabled &&
            lastPlaybackSource == hasSource &&
            lastPlayingState == isPlaying
        ) {
            return
        }
        lastPlaybackEnabled = enabled
        lastPlaybackSource = hasSource
        lastPlayingState = isPlaying

        if (!enabled || !hasSource) {
            session.hide()
            session.pause()
            danmakuView.clearDanmakusOnScreen()
            lastObservedPositionMs = null
            pendingResync = true
            return
        }

        session.show()
        if (isPlaying) {
            session.resume()
        } else {
            session.pause()
        }
    }

    private fun syncPosition(positionMs: Long) {
        val clampedPositionMs = positionMs.coerceAtLeast(0L)
        val shouldSeek = pendingResync || hasPositionDiscontinuity(clampedPositionMs)
        lastObservedPositionMs = clampedPositionMs
        if (!shouldSeek) return

        if (appliedSegmentIndices.isEmpty()) return
        session.seekTo(clampedPositionMs)
        pendingResync = false
    }

    fun release() {
        appliedSegmentIndices.clear()
        session.release()
    }

    private fun syncVideo(videoId: VideoPlaybackId?) {
        if (lastVideoId == videoId) return

        lastVideoId = videoId
        lastObservedPositionMs = null
        pendingResync = true
        appliedSegmentIndices.clear()
        session.clearSegments()
    }

    private fun applyConfig(config: VideoDanmakuConfig) {
        if (config == lastAppliedConfig) return

        danmakuContext.applyConfig(config)
        lastAppliedConfig = config
        pendingResync = true
        danmakuView.forceRender()
    }

    private fun syncSegments(loadedSegments: Map<Long, DanmakuSegment>) {
        val nextSegments = loadedSegments.entries.sortedBy { it.key }
        if (nextSegments.isEmpty()) {
            if (appliedSegmentIndices.isEmpty()) return

            appliedSegmentIndices.clear()
            pendingResync = true
            session.clearSegments()
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
            pendingResync = true
            appliedSegmentIndices.clear()
            appliedSegmentIndices.addAll(nextSegmentIndices)
            return
        }

        nextSegments.forEach { (segmentIndex, segment) ->
            if (appliedSegmentIndices.add(segmentIndex)) {
                session.appendSegment(DanmakuSegmentData(segmentIndex, segment.elems))
                pendingResync = true
            }
        }
    }

    private fun hasPositionDiscontinuity(positionMs: Long): Boolean {
        val lastPositionMs = lastObservedPositionMs ?: return false
        if (positionMs < lastPositionMs) {
            return true
        }
        return abs(positionMs - lastPositionMs) >= DANMAKU_POSITION_DISCONTINUITY_MS
    }
}

private const val DANMAKU_POSITION_DISCONTINUITY_MS = 1_500L
