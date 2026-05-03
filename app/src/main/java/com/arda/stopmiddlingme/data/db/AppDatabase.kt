package com.arda.stopmiddlingme.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.arda.stopmiddlingme.data.db.dao.BaselineDao
import com.arda.stopmiddlingme.data.db.dao.SessionDao
import com.arda.stopmiddlingme.data.db.dao.SignalDao
import com.arda.stopmiddlingme.data.db.entity.AlertSession
import com.arda.stopmiddlingme.data.db.entity.NetworkBaseline
import com.arda.stopmiddlingme.data.db.entity.SignalInstance

@Database(
    entities = [
        NetworkBaseline::class,
        AlertSession::class,
        SignalInstance::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun baselineDao(): BaselineDao
    abstract fun sessionDao(): SessionDao
    abstract fun signalDao(): SignalDao
}
