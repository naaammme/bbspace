package com.naaammme.bbspace.core.data.download

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [VideoDownloadEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VideoDownloadDb : RoomDatabase() {
    abstract fun dao(): VideoDownloadDao
}
