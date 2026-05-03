package com.arda.stopmiddlingme.data.db.dao

import androidx.room.*
import com.arda.stopmiddlingme.data.db.entity.SignalInstance
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalDao {

    @Query("SELECT * FROM signal_instance WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeBySession(sessionId: String): Flow<List<SignalInstance>>

    // Signaux actifs = non expirés → ceux qui contribuent encore au score
    @Query("""
        SELECT * FROM signal_instance 
        WHERE sessionId = :sessionId AND expireAt > :now
        ORDER BY timestamp ASC
    """)
    suspend fun getActiveSignals(sessionId: String, now: Long): List<SignalInstance>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(signal: SignalInstance)

    // Purge les signaux expirés pour ne pas polluer la DB indéfiniment
    @Query("DELETE FROM signal_instance WHERE expireAt <= :now")
    suspend fun deleteExpired(now: Long)
}
