package com.naaammme.bbspace.feature.video

import com.naaammme.bbspace.core.model.DanmakuElem
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
    private val session: SegmentDanmakuSession<DanmakuElem>
) {
    private var lastVideoId: VideoPlaybackId? = null
    private var lastObservedPositionMs: Long? = null
    private var pendingResync = true
    private var lastAppliedConfig: VideoDanmakuConfig? = null
    private val appliedSegmentIndices = linkedSetOf<Long>()

    fun prepare() {
        session.prepare()
    }

    fun updateConfig(config: VideoDanmakuConfig) {
        if (config == lastAppliedConfig) return

        danmakuContext.applyConfig(config)
        lastAppliedConfig = config
        pendingResync = true
        danmakuView.forceRender()
    }

    fun syncSegments(danmakuState: VideoDanmakuState) {
        if (lastVideoId != danmakuState.videoId) {
            lastVideoId = danmakuState.videoId
            lastObservedPositionMs = null
            pendingResync = true
            appliedSegmentIndices.clear()
            session.clearSegments()
        }

        val nextSegments = danmakuState.loadedSegments.entries.sortedBy { it.key }

        if (nextSegments.isEmpty()) {
            if (appliedSegmentIndices.isNotEmpty()) {
                appliedSegmentIndices.clear()
                pendingResync = true
                session.clearSegments()
            }
            return
        }

        val nextSegmentIndices = linkedSetOf<Long>().apply {
            addAll(nextSegments.map { it.key })
        }
        val requiresFullReload = appliedSegmentIndices.any { it !in nextSegmentIndices }

        if (requiresFullReload) {
            session.replaceSegments(
                nextSegments.map { (segmentIndex, segment) ->
                    DanmakuSegmentData(segmentIndex, segment.elems)
                }
            )
            pendingResync = true
            appliedSegmentIndices.clear()
            appliedSegmentIndices.addAll(nextSegmentIndices)
        } else {
            nextSegments.forEach { (segmentIndex, segment) ->
                if (appliedSegmentIndices.add(segmentIndex)) {
                    session.appendSegment(DanmakuSegmentData(segmentIndex, segment.elems))
                }
            }
        }
    }

    fun updatePlaybackState(
        enabled: Boolean,
        hasSource: Boolean,
        isPlaying: Boolean
    ) {
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

    fun syncPosition(
        videoId: VideoPlaybackId?,
        positionMs: Long,
        force: Boolean
    ) {
        val clampedPositionMs = positionMs.coerceAtLeast(0L)
        if (lastVideoId != videoId) {
            lastVideoId = videoId
            lastObservedPositionMs = null
            pendingResync = true
        }

        val shouldSeek = force || hasPositionDiscontinuity(clampedPositionMs)
        lastObservedPositionMs = clampedPositionMs
        if (!shouldSeek) return

        session.seekTo(clampedPositionMs)
        pendingResync = false
    }

    fun hasPendingResync(): Boolean {
        return pendingResync
    }

    fun release() {
        appliedSegmentIndices.clear()
        session.release()
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
