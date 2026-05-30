package com.naaammme.bbspace.core.data.player

import android.os.SystemClock
import com.naaammme.bbspace.core.model.PlayReportParams
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.PlaybackSource
import com.naaammme.bbspace.core.model.PlaybackState
import com.naaammme.bbspace.core.model.PlayerSessionState
import kotlin.math.max

internal data class PlaybackReportSession(
    val uid: Long,
    val request: PlaybackRequest,
    val source: PlaybackSource,
    val report: PlayReportParams,
    val sessionId: String,
    val polarisActionId: String,
    val reportEnabled: Boolean,
    val playbackHistoryStartTs: Long,
    val startElapsedMs: Long,
    var lastSampleElapsedMs: Long,
    var quality: Int,
    var lastProgressSec: Int,
    var maxProgressSec: Int,
    var totalTimeMs: Long = 0L,
    var playedTimeMs: Long = 0L,
    var actualPlayedTimeMs: Long = 0L,
    var pausedTimeMs: Long = 0L,
    var heartbeatStartTs: Long = 0L,
    var heartbeatStarted: Boolean = false,
    var heartbeatEnded: Boolean = false,
    var playbackHistoryStarted: Boolean = false,
    var finalized: Boolean = false,
    var lastIsPlaying: Boolean = false,
    var lastPlayWhenReady: Boolean = false,
    var lastPlaybackState: PlaybackState = PlaybackState.Idle
)

internal fun PlaybackReportSession.sync(
    positionMs: Long,
    state: PlayerSessionState?
) {
    val nowElapsed = SystemClock.elapsedRealtime()
    val deltaMs = (nowElapsed - lastSampleElapsedMs).coerceAtLeast(0L)
    if (deltaMs > 0L) {
        when {
            lastIsPlaying -> {
                playedTimeMs += deltaMs
                actualPlayedTimeMs += deltaMs
            }

            heartbeatStarted && lastPlaybackState == PlaybackState.Ready -> {
                pausedTimeMs += deltaMs
            }
        }
    }
    totalTimeMs = (nowElapsed - startElapsedMs).coerceAtLeast(0L)
    lastProgressSec = msToSec(positionMs)
    maxProgressSec = max(maxProgressSec, lastProgressSec)
    lastSampleElapsedMs = nowElapsed
    state?.currentStream?.quality?.takeIf { it > 0 }?.let { quality = it }
}

internal fun PlaybackReportSession.durationSec(): Int {
    return msToSec(source.durationMs).coerceAtLeast(0)
}

internal fun PlaybackReportSession.progressSec(
    positionMs: Long,
    completePlayback: Boolean
): Int {
    if (completePlayback) return -1
    return msToSec(positionMs).coerceAtLeast(0)
}

internal fun PlaybackReportSession.isComplete(
    positionMs: Long,
    playbackState: PlaybackState,
    allowEndedOnly: Boolean
): Boolean {
    if (source.isPreview) return false
    if (playbackState == PlaybackState.Ended) return true
    if (allowEndedOnly) return false
    val durationMs = source.durationMs
    if (durationMs <= 0L) return false
    return durationMs - positionMs <= COMPLETE_THRESHOLD_MS
}

internal fun samePlaybackSource(
    left: PlaybackSource,
    right: PlaybackSource?
): Boolean {
    return right != null &&
            left.videoId.aid == right.videoId.aid &&
            left.videoId.cid == right.videoId.cid
}

internal fun msToSec(value: Long): Int {
    return (value.coerceAtLeast(0L) / 1000L).toInt()
}

internal fun nowSec(): Long {
    return System.currentTimeMillis() / 1000L
}

private const val COMPLETE_THRESHOLD_MS = 3_000L
