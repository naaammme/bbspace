package com.naaammme.bbspace.core.data.player

import android.os.SystemClock
import com.naaammme.bbspace.core.model.PlayReportParams
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.PlaybackSource
import com.naaammme.bbspace.core.model.PlayerSessionState
import com.naaammme.bbspace.infra.player.EnginePlaybackState
import com.naaammme.bbspace.infra.player.PlaybackSnapshot
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
    var lastSnapshot: PlaybackSnapshot = PlaybackSnapshot()
)

internal fun PlaybackReportSession.sync(
    snapshot: PlaybackSnapshot,
    state: PlayerSessionState?
) {
    val nowElapsed = SystemClock.elapsedRealtime()
    val deltaMs = (nowElapsed - lastSampleElapsedMs).coerceAtLeast(0L)
    if (deltaMs > 0L) {
        when {
            lastSnapshot.isPlaying -> {
                playedTimeMs += deltaMs
                actualPlayedTimeMs += deltaMs
            }

            heartbeatStarted && lastSnapshot.playbackState == EnginePlaybackState.Ready -> {
                pausedTimeMs += deltaMs
            }
        }
    }
    totalTimeMs = (nowElapsed - startElapsedMs).coerceAtLeast(0L)
    lastProgressSec = msToSec(snapshot.positionMs)
    maxProgressSec = max(maxProgressSec, lastProgressSec)
    lastSampleElapsedMs = nowElapsed
    state?.currentStream?.quality?.takeIf { it > 0 }?.let { quality = it }
}

internal fun PlaybackReportSession.durationSec(): Int {
    return msToSec(source.durationMs).coerceAtLeast(0)
}

internal fun PlaybackReportSession.progressSec(
    snapshot: PlaybackSnapshot,
    complete: Boolean
): Int {
    if (complete && isComplete(snapshot, allowEndedOnly = false)) return -1
    return msToSec(snapshot.positionMs).coerceAtLeast(0)
}

internal fun PlaybackReportSession.isComplete(
    snapshot: PlaybackSnapshot,
    allowEndedOnly: Boolean
): Boolean {
    if (source.isPreview) return false
    if (snapshot.playbackState == EnginePlaybackState.Ended) return true
    if (allowEndedOnly) return false
    val durationMs = source.durationMs
    if (durationMs <= 0L) return false
    return durationMs - snapshot.positionMs <= COMPLETE_THRESHOLD_MS
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
