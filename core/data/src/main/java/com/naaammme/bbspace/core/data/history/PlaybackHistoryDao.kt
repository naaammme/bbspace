package com.naaammme.bbspace.core.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PlaybackHistoryEntity)

    @Query("SELECT * FROM playback_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PlaybackHistoryEntity?

    @Query("SELECT COUNT(*) FROM playback_history")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM playback_history ORDER BY updatedAt DESC, id DESC")
    fun observeVideos(): Flow<List<PlaybackHistoryEntity>>

    @Query("DELETE FROM playback_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM playback_history")
    suspend fun clear()
}
