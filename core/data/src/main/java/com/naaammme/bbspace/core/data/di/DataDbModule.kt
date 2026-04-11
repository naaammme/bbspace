package com.naaammme.bbspace.core.data.di

import android.content.Context
import androidx.room.Room
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
        ).build()
    }

    @Provides
    fun provideLocalHistoryDao(db: LocalHistoryDb): LocalHistoryDao {
        return db.dao()
    }
}
