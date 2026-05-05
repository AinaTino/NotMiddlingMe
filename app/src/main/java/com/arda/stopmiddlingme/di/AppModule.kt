package com.arda.stopmiddlingme.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arda.stopmiddlingme.data.db.AppDatabase
import com.arda.stopmiddlingme.data.db.dao.BaselineDao
import com.arda.stopmiddlingme.data.db.dao.SessionDao
import com.arda.stopmiddlingme.data.db.dao.SignalDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "stopmiddlingme.db"
        )
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(MIGRATION_1_2)
            .build()

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE network_baseline ADD COLUMN security TEXT NOT NULL DEFAULT 'WPA2'"
            )
        }
    }

    @Provides
    fun provideBaselineDao(db: AppDatabase): BaselineDao = db.baselineDao()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideSignalDao(db: AppDatabase): SignalDao = db.signalDao()
}
