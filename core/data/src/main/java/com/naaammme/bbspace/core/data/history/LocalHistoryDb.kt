package com.naaammme.bbspace.core.data.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [LocalHistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class LocalHistoryDb : RoomDatabase() {
    abstract fun dao(): LocalHistoryDao
}
