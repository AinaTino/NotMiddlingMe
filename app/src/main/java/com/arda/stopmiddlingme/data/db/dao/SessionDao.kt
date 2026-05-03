package com.arda.stopmiddlingme.data.db.dao

import androidx.room.*
import com.arda.stopmiddlingme.data.db.entity.AlertSession
import com.arda.stopmiddlingme.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    // Session OPEN active sur un réseau donné
    // Il ne peut y en avoir qu'une par SSID à la fois
    @Query("""
        SELECT * FROM alert_session 
        WHERE networkSsid = :ssid AND status = 'OPEN'
        LIMIT 1
    """)
    fun observeOpenSession(ssid: String): Flow<AlertSession?>

    @Query("""
        SELECT * FROM alert_session 
        WHERE networkSsid = :ssid AND status = 'OPEN'
        LIMIT 1
    """)
    suspend fun getOpenSession(ssid: String): AlertSession?

    // Historique complet, trié du plus récent au plus ancien
    @Query("SELECT * FROM alert_session ORDER BY openedAt DESC")
    fun observeAll(): Flow<List<AlertSession>>

    @Query("""
        SELECT * FROM alert_session 
        WHERE networkSsid = :ssid 
        ORDER BY openedAt DESC
    """)
    fun observeByNetwork(ssid: String): Flow<List<AlertSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: AlertSession)

    @Update
    suspend fun update(session: AlertSession)

    // Ferme toutes les sessions OPEN dont le timer a expiré
    @Query("""
        UPDATE alert_session 
        SET status = 'CLOSED', closedAt = :now
        WHERE status = 'OPEN' AND autoCloseAt <= :now
    """)
    suspend fun closeExpiredSessions(now: Long)

    @Query("DELETE FROM alert_session WHERE id = :id")
    suspend fun delete(id: String)
}
