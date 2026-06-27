package com.naaammme.bbspace.core.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlaybackHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsert(item: PlaybackHistoryEntity)

    @Query("SELECT * FROM playback_history WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: String): PlaybackHistoryEntity?

    @Query("SELECT * FROM playback_history ORDER BY updatedAt DESC, id DESC")
    abstract fun observeVideos(): Flow<List<PlaybackHistoryEntity>>

    @Query("DELETE FROM playback_history WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query(
        """
        DELETE FROM playback_history
        WHERE uid = :uid
          AND id NOT IN (
            SELECT id FROM playback_history
            WHERE uid = :uid
            ORDER BY updatedAt DESC, id DESC
            LIMIT :limit
          )
        """
    )
    protected abstract suspend fun trimByUid(
        uid: Long,
        limit: Int
    )

    @Transaction
    open suspend fun upsertAndTrim(
        item: PlaybackHistoryEntity,
        limit: Int
    ) {
        upsert(item)
        trimByUid(item.uid, limit)
    }

    @Query("DELETE FROM playback_history")
    abstract suspend fun clear()
}
