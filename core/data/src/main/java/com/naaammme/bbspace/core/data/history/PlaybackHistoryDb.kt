package com.naaammme.bbspace.core.data.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PlaybackHistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class PlaybackHistoryDb : RoomDatabase() {
    abstract fun dao(): PlaybackHistoryDao
}
