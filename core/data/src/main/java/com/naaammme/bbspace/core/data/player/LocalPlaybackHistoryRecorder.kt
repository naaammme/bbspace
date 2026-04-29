package com.naaammme.bbspace.core.data.player

import com.naaammme.bbspace.core.domain.history.PlaybackHistoryRepository
import com.naaammme.bbspace.core.model.PlaybackHistory
import com.naaammme.bbspace.core.model.PlaybackHistoryKey
import com.naaammme.bbspace.core.model.PlaybackHistoryMeta
import com.naaammme.bbspace.infra.player.PlaybackSnapshot
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalPlaybackHistoryRecorder @Inject constructor(
    private val playbackHistoryRepo: PlaybackHistoryRepository
) {
    internal suspend fun record(
        active: PlaybackReportSession,
        meta: PlaybackHistoryMeta?,
        snapshot: PlaybackSnapshot
    ) {
        val key = PlaybackHistoryKey.video(active.report)
        val nowMs = System.currentTimeMillis()
        val finished = active.isComplete(snapshot, allowEndedOnly = false)
        val biz = active.report.biz.name.lowercase(Locale.ROOT)
        playbackHistoryRepo.upsertVideo(
            PlaybackHistory(
                uid = active.uid,
                key = key,
                biz = biz,
                aid = active.report.aid,
                cid = active.report.cid,
                epId = active.report.epId,
                seasonId = active.report.seasonId,
                title = meta?.title.orEmpty(),
                cover = meta?.cover,
                part = meta?.part,
                partTitle = meta?.partTitle,
                ownerUid = meta?.ownerUid,
                ownerName = meta?.ownerName,
                durationMs = active.source.durationMs.coerceAtLeast(0L),
                progressMs = snapshot.positionMs.coerceAtLeast(0L),
                watchMs = active.actualPlayedTimeMs.coerceAtLeast(0L),
                updatedAt = nowMs,
                finished = finished
            )
        )
    }
}
