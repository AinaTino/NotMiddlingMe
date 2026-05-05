package com.arda.stopmiddlingme.data.repository

import com.arda.stopmiddlingme.data.db.dao.BaselineDao
import com.arda.stopmiddlingme.data.db.dao.SessionDao
import com.arda.stopmiddlingme.data.db.dao.SignalDao
import com.arda.stopmiddlingme.data.db.entity.AlertSession
import com.arda.stopmiddlingme.data.db.entity.NetworkBaseline
import com.arda.stopmiddlingme.data.db.entity.SignalInstance
import com.arda.stopmiddlingme.domain.model.AlertLevel
import com.arda.stopmiddlingme.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val signalDao: SignalDao,
    private val baselineDao: BaselineDao
) {
    private val createMutex = Mutex()

    // ── Observation (UI) ────────────────────────────────────────────────────

    fun observeOpenSession(ssid: String): Flow<AlertSession?> =
        sessionDao.observeOpenSession(ssid)

    fun observeAllSessions(): Flow<List<AlertSession>> =
        sessionDao.observeAll()

    fun observeSessionsByNetwork(ssid: String): Flow<List<AlertSession>> =
        sessionDao.observeByNetwork(ssid)

    fun observeSignals(sessionId: String): Flow<List<SignalInstance>> =
        signalDao.observeBySession(sessionId)

    // ── Accès synchrone (ScoreEngine) ───────────────────────────────────────

    suspend fun getOpenSession(ssid: String): AlertSession? =
        sessionDao.getOpenSession(ssid)

    suspend fun getActiveSignals(sessionId: String): List<SignalInstance> =
        signalDao.getActiveSignals(sessionId, System.currentTimeMillis())

    // ── Écriture ────────────────────────────────────────────────────────────

    suspend fun saveSession(session: AlertSession) {
        sessionDao.update(session)
    }

    suspend fun addSignal(signal: SignalInstance) {
        signalDao.insert(signal)
    }

    // Crée une nouvelle session OPEN pour un réseau donné
    suspend fun createSession(ssid: String, windowMillis: Long = 30_000L): AlertSession = createMutex.withLock {
        // Sécurité : ne pas créer de session pour un SSID inconnu
        require(ssid != "—" && ssid.isNotBlank() && ssid != "<unknown ssid>") {
            "SSID invalide pour la création de session"
        }

        // Double check après lock pour éviter les doublons
        val existingSession = sessionDao.getOpenSession(ssid)
        if (existingSession != null) return@withLock existingSession

        val now = System.currentTimeMillis()
        val session = AlertSession(
            id = UUID.randomUUID().toString(),
            networkSsid = ssid,
            status = SessionStatus.OPEN,
            totalScore = 0,
            finalLevel = AlertLevel.SAFE,
            openedAt = now,
            autoCloseAt = now + windowMillis
        )

        // On utilise la transaction atomique pour garantir l'intégrité de la Foreign Key
        val baseline = baselineDao.get(ssid) ?: NetworkBaseline(
            ssid = ssid,
            bssid = "—",
            gatewayIp = "—",
            gatewayMac = "—",
            dnsServers = "",
            createdAt = now
        )

        sessionDao.insertWithBaseline(session, baseline)

        return@withLock session
    }

    // L'utilisateur marque l'alerte comme résolue
    // La session est fermée et persistée pour l'historique — le score n'est PAS effacé
    // Si de nouveaux signaux arrivent, une NOUVELLE session s'ouvrira avec score 0
    suspend fun resolveSession(sessionId: String) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.update(
            session.copy(
                status = SessionStatus.RESOLVED,
                closedAt = System.currentTimeMillis()
            )
        )
    }

    // Ferme les sessions dont le timer a expiré (appelé périodiquement)
    suspend fun closeExpiredSessions() {
        sessionDao.closeExpiredSessions(System.currentTimeMillis())
    }

    // Purge les signaux expirés (appelé périodiquement)
    suspend fun purgeExpiredSignals() {
        signalDao.deleteExpired(System.currentTimeMillis())
    }

    suspend fun deleteSession(sessionId: String) {
        sessionDao.delete(sessionId)
    }

    suspend fun clearAllSessions() {
        sessionDao.deleteAll()
    }
}
