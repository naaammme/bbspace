package com.naaammme.bbspace.core.data.di

import android.content.Context
import androidx.room.Room
import com.naaammme.bbspace.core.data.download.VideoDownloadDao
import com.naaammme.bbspace.core.data.download.VideoDownloadDb
import com.naaammme.bbspace.core.data.history.PlaybackHistoryDao
import com.naaammme.bbspace.core.data.history.PlaybackHistoryDb
import com.naaammme.bbspace.core.data.search.SearchHistoryDao
import com.naaammme.bbspace.core.data.search.SearchHistoryDb
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataDbModule {
    @Provides
    @Singleton
    fun providePlaybackHistoryDb(
        @ApplicationContext context: Context
    ): PlaybackHistoryDb {
        return Room.databaseBuilder(
            context,
            PlaybackHistoryDb::class.java,
            "playback_history.db"
        ).fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    fun providePlaybackHistoryDao(db: PlaybackHistoryDb): PlaybackHistoryDao {
        return db.dao()
    }

    @Provides
    @Singleton
    fun provideSearchHistoryDb(
        @ApplicationContext context: Context
    ): SearchHistoryDb {
        return Room.databaseBuilder(
            context,
            SearchHistoryDb::class.java,
            "search_history.db"
        ).build()
    }

    @Provides
    fun provideSearchHistoryDao(db: SearchHistoryDb): SearchHistoryDao {
        return db.dao()
    }

    @Provides
    @Singleton
    fun provideVideoDownloadDb(
        @ApplicationContext context: Context
    ): VideoDownloadDb {
        return Room.databaseBuilder(
            context,
            VideoDownloadDb::class.java,
            "video_download.db"
        ).fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    fun provideVideoDownloadDao(db: VideoDownloadDb): VideoDownloadDao {
        return db.dao()
    }
}
