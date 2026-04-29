package com.naaammme.bbspace.core.data.repository

import com.naaammme.bbspace.core.data.history.PlaybackHistoryDao
import com.naaammme.bbspace.core.data.history.PlaybackHistoryEntity
import com.naaammme.bbspace.core.domain.history.PlaybackHistoryRepository
import com.naaammme.bbspace.core.model.PlaybackHistory
import com.naaammme.bbspace.core.model.PlaybackHistoryKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PlaybackHistoryRepoImpl @Inject constructor(
    private val dao: PlaybackHistoryDao
) : PlaybackHistoryRepository {

    override suspend fun upsertVideo(item: PlaybackHistory) {
         dao.upsert(item.toEntity())
    }

    override suspend fun getVideo(
        uid: Long,
        key: String
    ): PlaybackHistory? {
        return dao.getById(PlaybackHistoryKey.videoId(uid, key))?.toModel()
    }

    override fun observeVideoCount(): Flow<Int> {
        return dao.observeCount()
    }

    override fun observeVideos(): Flow<List<PlaybackHistory>> {
        return dao.observeVideos().map { list -> list.map(PlaybackHistoryEntity::toModel) }
    }

    override suspend fun deleteVideo(id: String) {
        dao.deleteById(id)
    }

    override suspend fun clearVideos() {
        dao.clear()
    }
}

private fun PlaybackHistory.toEntity() = PlaybackHistoryEntity(
    id = PlaybackHistoryKey.videoId(uid, key),
    uid = uid,
    biz = biz,
    aid = aid,
    cid = cid,
    epId = epId,
    seasonId = seasonId,
    title = title,
    cover = cover,
    part = part,
    partTitle = partTitle,
    ownerUid = ownerUid,
    ownerName = ownerName,
    durationMs = durationMs,
    progressMs = progressMs,
    watchMs = watchMs,
    updatedAt = updatedAt,
    finished = finished
)

private fun PlaybackHistoryEntity.toModel() = PlaybackHistory(
    uid = uid,
    key = PlaybackHistoryKey.video(
        biz = biz,
        aid = aid,
        cid = cid,
        epId = epId
    ),
    biz = biz,
    aid = aid,
    cid = cid,
    epId = epId,
    seasonId = seasonId,
    title = title,
    cover = cover,
    part = part,
    partTitle = partTitle,
    ownerUid = ownerUid,
    ownerName = ownerName,
    durationMs = durationMs,
    progressMs = progressMs,
    watchMs = watchMs,
    updatedAt = updatedAt,
    finished = finished
)
