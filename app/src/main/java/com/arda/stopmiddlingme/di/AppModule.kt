package com.arda.stopmiddlingme.di

import android.content.Context
import androidx.room.Room
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
        ).build()

    @Provides
    fun provideBaselineDao(db: AppDatabase): BaselineDao = db.baselineDao()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideSignalDao(db: AppDatabase): SignalDao = db.signalDao()
}
