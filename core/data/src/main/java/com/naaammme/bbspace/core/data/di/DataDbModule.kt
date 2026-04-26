package com.naaammme.bbspace.core.data.di

import android.content.Context
import androidx.room.Room
import com.naaammme.bbspace.core.data.download.VideoDownloadDao
import com.naaammme.bbspace.core.data.download.VideoDownloadDb
import com.naaammme.bbspace.core.data.history.LocalHistoryDao
import com.naaammme.bbspace.core.data.history.LocalHistoryDb
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
    fun provideLocalHistoryDb(
        @ApplicationContext context: Context
    ): LocalHistoryDb {
        return Room.databaseBuilder(
            context,
            LocalHistoryDb::class.java,
            "local_history.db"
        ).fallbackToDestructiveMigration(false)
            .build()
    }

    @Provides
    fun provideLocalHistoryDao(db: LocalHistoryDb): LocalHistoryDao {
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
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideVideoDownloadDao(db: VideoDownloadDb): VideoDownloadDao {
        return db.dao()
    }
}
