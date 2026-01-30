package com.morningmindful.di

import android.content.Context
import com.morningmindful.data.AppDatabase
import com.morningmindful.data.dao.JournalEntryDao
import com.morningmindful.data.repository.JournalRepository
import com.morningmindful.data.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing application-level dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideJournalEntryDao(database: AppDatabase): JournalEntryDao {
        return database.journalEntryDao()
    }

    @Provides
    @Singleton
    fun provideJournalRepository(dao: JournalEntryDao): JournalRepository {
        return JournalRepository(dao)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository {
        return SettingsRepository(context)
    }
}
