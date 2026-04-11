package com.naaammme.bbspace.core.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocalHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalHistoryEntity)

    @Query("SELECT * FROM local_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LocalHistoryEntity?
}
