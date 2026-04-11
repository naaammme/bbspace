package com.naaammme.bbspace.core.data.repository

import com.naaammme.bbspace.core.data.history.LocalHistoryDao
import com.naaammme.bbspace.core.data.history.LocalHistoryEntity
import com.naaammme.bbspace.core.domain.history.LocalHistoryRepository
import com.naaammme.bbspace.core.model.LocalHistoryKey
import com.naaammme.bbspace.core.model.VideoHistory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class LocalHistoryRepoImpl @Inject constructor(
    private val dao: LocalHistoryDao
) : LocalHistoryRepository {

    override suspend fun upsertVideo(item: VideoHistory) {
        dao.upsert(item.toEntity())
    }

    override suspend fun getVideo(
        uid: Long,
        key: String
    ): VideoHistory? {
        return dao.getById(LocalHistoryKey.videoId(uid, key))?.toModel()
    }

    override fun observeVideos(): Flow<List<VideoHistory>> {
        return dao.observeVideos().map { list -> list.map(LocalHistoryEntity::toModel) }
    }

    override suspend fun deleteVideo(id: String) {
        dao.deleteById(id)
    }

    override suspend fun clearVideos() {
        dao.clear()
    }
}

private fun VideoHistory.toEntity() = LocalHistoryEntity(
    id = LocalHistoryKey.videoId(uid, key),
    uid = uid,
    biz = biz,
    aid = aid,
    cid = cid,
    bvid = bvid,
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

private fun LocalHistoryEntity.toModel() = VideoHistory(
    uid = uid,
    key = LocalHistoryKey.video(
        biz = biz,
        aid = aid,
        cid = cid,
        epId = epId
    ),
    biz = biz,
    aid = aid,
    cid = cid,
    bvid = bvid,
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
