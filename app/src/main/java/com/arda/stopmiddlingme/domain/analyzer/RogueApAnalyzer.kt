package com.arda.stopmiddlingme.domain.analyzer

import com.arda.stopmiddlingme.data.repository.BaselineRepository
import com.arda.stopmiddlingme.domain.engine.ScoreEngine
import com.arda.stopmiddlingme.domain.model.SignalType
import com.arda.stopmiddlingme.domain.model.WifiAp
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RogueApAnalyzer @Inject constructor(
    private val baselineRepo: BaselineRepository,
    private val scoreEngine: ScoreEngine
) {

    suspend fun analyze(networks: List<WifiAp>, currentSsid: String) {
        val baseline = baselineRepo.get(currentSsid) ?: return

        // 1. Chercher des BSSID différents pour le même SSID (Rogue AP classique)
        val duplicates = networks.filter { it.ssid == currentSsid && it.bssid != baseline.bssid }
        
        duplicates.forEach { rogue ->
            scoreEngine.addSignal(
                ssid = currentSsid,
                type = SignalType.ROGUE_AP_BSSID,
                detail = "Autre AP trouvé pour $currentSsid: BSSID=${rogue.bssid} (Baseline=${baseline.bssid})"
            )

            // 2. Vérifier si la sécurité est dégradée
            if (isSecurityDegraded(baseline.security, rogue.security)) {
                scoreEngine.addSignal(
                    ssid = currentSsid,
                    type = SignalType.ROGUE_AP_SECURITY,
                    detail = "Sécurité dégradée sur ${rogue.bssid}: ${rogue.security} (Baseline=${baseline.security})"
                )
            }
        }
    }

    private fun isSecurityDegraded(originalSecurity: String, newSecurity: String): Boolean {
        // Logique simplifiée : OPEN est toujours une dégradation si l'original ne l'était pas
        return newSecurity == "OPEN" && originalSecurity != "OPEN"
    }
}
