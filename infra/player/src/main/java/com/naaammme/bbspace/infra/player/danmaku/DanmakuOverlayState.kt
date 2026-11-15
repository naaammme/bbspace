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
    private var appliedWindowSignature: Int? = null
    private var blockColored = false
    private var appendedWindowCount = 0
    private val itemMapper = DefaultDanmakuItemMapper()

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
            lastPlayState?.isPlaying != true
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

    fun syncLive(
        config: DanmakuConfig,
        isPlaying: Boolean,
        speed: Float,
        hasSource: Boolean
    ) {
        if (released.get()) return
        val clampedSpeed = speed.coerceIn(0.25f, 3f)
        val hasSpeedChange = lastPlaybackSpeed != clampedSpeed
        lastPlaybackSpeed = clampedSpeed
        applyConfig(config)
        val needStateOverride = hasSpeedChange ||
            !isPlaying ||
            !hasSource ||
            lastPlayState?.isPlaying != true
        if (needStateOverride) {
            timeProvider.overrideState(
                positionMs = timeProvider.getCurrentTimeMs(),
                isPlaying = isPlaying,
                speed = clampedSpeed
            )
        }
        syncPlayback(
            enabled = config.enabled,
            hasSource = hasSource,
            isPlaying = isPlaying
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

    fun clearLiveDanmakus() {
        if (released.get()) return
        lastSourceKey = null
        lastSeekEventId = 0L
        pendingSeek = true
        appliedWindowId = null
        appliedWindowSignature = null
        appendedWindowCount = 0
        session.clearSegments()
    }

    fun appendDanmaku(item: DanmakuItem) {
        if (released.get()) return
        if (blockColored && item.color != DANMAKU_COLOR_WHITE) return
        val danmaku = itemMapper.map(item, danmakuContext) ?: return
        danmaku.time = danmaku.time.coerceAtLeast(timeProvider.getCurrentTimeMs()) + LIVE_DANMAKU_LEAD_MS
        danmaku.priority = LIVE_DANMAKU_PRIORITY
        danmakuCtrl.addDanmaku(danmaku)
    }

    private fun syncSource(sourceKey: String?) {
        if (lastSourceKey == sourceKey) return

        lastSourceKey = sourceKey
        lastSeekEventId = 0L
        pendingSeek = true
        appliedWindowId = null
        appliedWindowSignature = null
        appendedWindowCount = 0
        session.clearSegments()
    }

    private fun applyConfig(
        config: DanmakuConfig
    ) {
        val nextState = DanmakuCfgState(config)
        if (lastCfgState == nextState) return

        danmakuContext.applyConfig(config)
        // 屏蔽彩色开关变化后，当前分段需要按新规则重新过滤
        if (lastCfgState != null &&
            lastCfgState?.config?.blockColored != config.blockColored
        ) {
            appliedWindowSignature = null
        }
        blockColored = config.blockColored
        lastCfgState = nextState
    }

    private fun syncWindow(
        window: DanmakuWindow?,
        targetWindowId: Long,
        requireTargetWindow: Boolean
    ) {
        val targetWindow = window?.takeIf { it.id == targetWindowId }
        val appliedId = appliedWindowId
        if (targetWindow == null) {
            // 顺序播放刚跨入下一分段、新分段还在加载时不清屏，
            // 保留在屏弹幕等窗口到位后平滑追加
            val awaitingNextWindow = !requireTargetWindow &&
                appliedId != null &&
                targetWindowId == appliedId + 1L
            if (awaitingNextWindow) {
                return
            }
            if (appliedWindowId != null) {
                appliedWindowId = null
                appliedWindowSignature = null
                session.clearSegments()
            }
            pendingSeek = true
            return
        }
        val nextItems = filterColored(targetWindow.items)
        val nextSignature = nextItems.windowSignature()
        if (appliedWindowId == targetWindow.id && appliedWindowSignature == nextSignature) {
            return
        }
        // 顺序推进到相邻分段：用追加代替整段替换，避免把在屏弹幕全部清空
        val naturalAdvance = !requireTargetWindow &&
            appliedId != null &&
            targetWindow.id == appliedId + 1L &&
            appendedWindowCount < MAX_APPENDED_SEGMENTS
        if (naturalAdvance) {
            session.appendSegment(DanmakuSegmentData(targetWindow.id, nextItems))
            appendedWindowCount++
        } else {
            session.replaceSegments(listOf(DanmakuSegmentData(targetWindow.id, nextItems)))
            appendedWindowCount = 0
        }
        appliedWindowId = targetWindow.id
        appliedWindowSignature = nextSignature
    }

    private fun filterColored(items: List<DanmakuItem>): List<DanmakuItem> {
        if (!blockColored) return items
        return items.filter { it.color == DANMAKU_COLOR_WHITE }
    }

    private fun consumeSeekEvent(seekEventId: Long): Boolean {
        if (seekEventId == 0L || seekEventId == lastSeekEventId) {
            return false
        }
        lastSeekEventId = seekEventId
        return true
    }
}

private fun List<DanmakuItem>.windowSignature(): Int {
    var result = size
    for (item in this) {
        result = 31 * result + item.id.hashCode()
        result = 31 * result + item.progressMs
    }
    return result
}

private data class DanmakuCfgState(
    val config: DanmakuConfig
)

private data class DanmakuPlayState(
    val enabled: Boolean,
    val hasSource: Boolean,
    val isPlaying: Boolean
)

private const val LIVE_DANMAKU_LEAD_MS = 1_200L
private const val LIVE_DANMAKU_PRIORITY: Byte = 1

/** 顺序播放最多连续追加的分段数，超过后整体替换一次以控制内存 */
private const val MAX_APPENDED_SEGMENTS = 8

/** B 站弹幕默认白色（RGB），屏蔽彩色时只放行这个颜色 */
private const val DANMAKU_COLOR_WHITE = 0xFFFFFF
