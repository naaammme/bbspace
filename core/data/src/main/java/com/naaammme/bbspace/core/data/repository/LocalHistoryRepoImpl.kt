package com.naaammme.bbspace.core.data.repository

import com.naaammme.bbspace.core.data.history.LocalHistoryDao
import com.naaammme.bbspace.core.domain.history.LocalHistoryRepository
import com.naaammme.bbspace.core.model.LocalHistoryKey
import com.naaammme.bbspace.core.model.VideoHistory
import javax.inject.Inject
import javax.inject.Singleton

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
}

private fun VideoHistory.toEntity() = com.naaammme.bbspace.core.data.history.LocalHistoryEntity(
    id = LocalHistoryKey.videoId(uid, key),
    uid = uid,
    kind = LocalHistoryKey.KIND_VIDEO,
    hKey = key,
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
    watchAt = watchAt,
    updatedAt = updatedAt,
    finished = finished
)

private fun com.naaammme.bbspace.core.data.history.LocalHistoryEntity.toModel() = VideoHistory(
    uid = uid,
    key = hKey,
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
    watchAt = watchAt,
    updatedAt = updatedAt,
    finished = finished
)
