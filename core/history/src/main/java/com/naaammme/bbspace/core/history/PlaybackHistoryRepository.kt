package com.naaammme.bbspace.core.history

import android.content.Context
import androidx.room.Room
import com.naaammme.bbspace.core.model.PlaybackHistory
import com.naaammme.bbspace.core.model.PlaybackHistoryKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PlaybackHistoryRepository @Inject constructor(
    private val dao: PlaybackHistoryDao
) {

    suspend fun upsertVideo(item: PlaybackHistory) {
        dao.upsertAndTrim(item.toEntity(), MAX_VIDEOS_PER_UID)
    }

    suspend fun getVideo(
        uid: Long,
        key: String
    ): PlaybackHistory? {
        return dao.getById(PlaybackHistoryKey.videoId(uid, key))?.toModel()
    }

    fun observeVideos(): Flow<List<PlaybackHistory>> {
        return dao.observeVideos().map { list -> list.map(PlaybackHistoryEntity::toModel) }
    }

    suspend fun deleteVideo(id: String) {
        dao.deleteById(id)
    }

    suspend fun clearVideos() {
        dao.clear()
    }

    private companion object {
        const val MAX_VIDEOS_PER_UID = 1000
    }
}

@Module
@InstallIn(SingletonComponent::class)
object PlaybackHistoryModule {
    @Provides
    @Singleton
    fun providePlaybackHistoryDb(
        @ApplicationContext context: Context
    ): PlaybackHistoryDb {
        return Room.databaseBuilder(
            context,
            PlaybackHistoryDb::class.java,
            "playback_history.db"
        )
            .fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    fun providePlaybackHistoryDao(db: PlaybackHistoryDb): PlaybackHistoryDao {
        return db.dao()
    }
}
