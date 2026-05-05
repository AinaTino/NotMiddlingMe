package com.arda.stopmiddlingme.domain.engine

import com.arda.stopmiddlingme.data.db.entity.AlertSession
import com.arda.stopmiddlingme.data.db.entity.SignalInstance
import com.arda.stopmiddlingme.data.repository.SessionRepository
import com.arda.stopmiddlingme.domain.model.AlertLevel
import com.arda.stopmiddlingme.domain.model.SessionStatus
import com.arda.stopmiddlingme.domain.model.SignalType
import com.arda.stopmiddlingme.util.AlertNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScoreEngine @Inject constructor(
    private val sessionRepo: SessionRepository,
    private val notifManager: AlertNotificationManager
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun addSignal(ssid: String, type: SignalType, detail: String) {
        scope.launch {
            val now = System.currentTimeMillis()

            // 1. Récupérer ou créer la session ouverte pour ce réseau (SÉCURISÉ)
            val session = sessionRepo.createSession(ssid)

            // 2. Créer l'instance de signal
            val signal = SignalInstance(
                id = UUID.randomUUID().toString(),
                sessionId = session.id,
                type = type,
                detail = detail,
                timestamp = now,
                expireAt = type.expiresAt(now)
            )

            // 3. Persister le signal
            sessionRepo.addSignal(signal)

            // 4. Recalculer le score (signaux non expirés uniquement)
            val activeSignals = sessionRepo.getActiveSignals(session.id)
            val newScore = activeSignals.sumOf { it.type.poids }
            val hasStandalone = activeSignals.any { it.type.standalone }

            // 5. Calculer le niveau
            val newLevel = computeLevel(newScore, hasStandalone)
            val oldLevel = session.finalLevel

            // 6. Reset fenêtre glissante (30 secondes)
            val newAutoClose = now + 30_000L

            // 7. Mettre à jour la session
            sessionRepo.saveSession(
                session.copy(
                    totalScore = newScore,
                    finalLevel = newLevel,
                    autoCloseAt = newAutoClose
                )
            )

            // 8. Notifier si niveau a changé ou empiré
            if (newLevel > oldLevel) {
                notifManager.notify(session.id, newLevel, type.description)
            }
        }
    }

    private fun computeLevel(score: Int, hasStandalone: Boolean): AlertLevel {
        if (hasStandalone && score >= 4) return AlertLevel.CRITIQUE
        return when (score) {
            in 0..3 -> AlertLevel.SAFE
            in 4..6 -> AlertLevel.SUSPECT
            in 7..9 -> AlertLevel.WARNING
            else -> AlertLevel.CRITIQUE
        }
    }
}
