package com.arda.stopmiddlingme.data.db.dao

import androidx.room.*
import com.arda.stopmiddlingme.data.db.entity.AlertSession
import com.arda.stopmiddlingme.data.db.entity.NetworkBaseline
import com.arda.stopmiddlingme.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SessionDao {

    @Query("""
        SELECT * FROM alert_session 
        WHERE networkSsid = :ssid AND status = 'OPEN'
        LIMIT 1
    """)
    abstract fun observeOpenSession(ssid: String): Flow<AlertSession?>

    @Query("""
        SELECT * FROM alert_session 
        WHERE networkSsid = :ssid AND status = 'OPEN'
        LIMIT 1
    """)
    abstract suspend fun getOpenSession(ssid: String): AlertSession?

    @Query("SELECT * FROM alert_session ORDER BY openedAt DESC")
    abstract fun observeAll(): Flow<List<AlertSession>>

    @Query("""
        SELECT * FROM alert_session 
        WHERE networkSsid = :ssid 
        ORDER BY openedAt DESC
    """)
    abstract fun observeByNetwork(ssid: String): Flow<List<AlertSession>>

    @Query("SELECT * FROM alert_session WHERE id = :id")
    abstract suspend fun getSessionById(id: String): AlertSession?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(session: AlertSession)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertBaseline(baseline: NetworkBaseline)

    @Update
    abstract suspend fun update(session: AlertSession)

    @Transaction
    open suspend fun insertWithBaseline(session: AlertSession, baseline: NetworkBaseline) {
        insertBaseline(baseline)
        insert(session)
    }

    @Query("""
        UPDATE alert_session 
        SET status = 'CLOSED', closedAt = :now
        WHERE status = 'OPEN' AND autoCloseAt <= :now
    """)
    abstract suspend fun closeExpiredSessions(now: Long)

    @Query("DELETE FROM alert_session WHERE id = :id")
    abstract suspend fun delete(id: String)

    @Query("DELETE FROM alert_session")
    abstract suspend fun deleteAll()
}
