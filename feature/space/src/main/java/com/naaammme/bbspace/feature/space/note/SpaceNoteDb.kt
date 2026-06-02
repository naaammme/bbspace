package com.naaammme.bbspace.feature.space.note

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "space_note")
data class SpaceNoteEntity(
    @PrimaryKey val uid: Long,
    val name: String,
    val face: String?,
    val content: String
)

@Dao
interface SpaceNoteDao {
    @Upsert
    suspend fun upsert(item: SpaceNoteEntity)

    @Query("SELECT content FROM space_note WHERE uid = :uid")
    fun observeContent(uid: Long): Flow<String?>

    @Query("SELECT * FROM space_note WHERE content != '' ORDER BY uid ASC")
    suspend fun getAllNonBlank(): List<SpaceNoteEntity>

    @Upsert
    suspend fun upsertAll(items: List<SpaceNoteEntity>)

    @Query("DELETE FROM space_note WHERE uid = :uid")
    suspend fun deleteByUid(uid: Long)
}

@Database(
    entities = [SpaceNoteEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SpaceNoteDb : RoomDatabase() {
    abstract fun dao(): SpaceNoteDao
}

@Module
@InstallIn(SingletonComponent::class)
object SpaceNoteDbModule {
    @Provides
    @Singleton
    fun provideSpaceNoteDb(
        @ApplicationContext context: Context
    ): SpaceNoteDb {
        return Room.databaseBuilder(
            context,
            SpaceNoteDb::class.java,
            "space_note.db"
        ).build()
    }

    @Provides
    fun provideSpaceNoteDao(db: SpaceNoteDb): SpaceNoteDao {
        return db.dao()
    }
}