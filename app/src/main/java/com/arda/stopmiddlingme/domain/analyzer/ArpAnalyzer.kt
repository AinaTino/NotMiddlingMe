package com.arda.stopmiddlingme.domain.analyzer

import com.arda.stopmiddlingme.data.db.entity.NetworkBaseline
import com.arda.stopmiddlingme.data.repository.BaselineRepository
import com.arda.stopmiddlingme.domain.engine.ScoreEngine
import com.arda.stopmiddlingme.domain.model.ArpEntry
import com.arda.stopmiddlingme.domain.model.SignalType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArpAnalyzer @Inject constructor(
    private val baselineRepo: BaselineRepository,
    private val scoreEngine: ScoreEngine
) {
    private var lastAnalyzedSsid: String? = null

    // Historique des MACs observées pour chaque IP
    // Permet de détecter les alternances (flip-flop = MITM probable)
    private val macHistory = mutableMapOf<String, MutableList<String>>()

    // Derniers MACs connus pour chaque IP
    private val lastKnownMac = mutableMapOf<String, String>()

    suspend fun analyze(table: List<ArpEntry>, ssid: String) {
        val baseline = baselineRepo.get(ssid) ?: return

        if (ssid != lastAnalyzedSsid) {
            // Réseau changé : reset l'historique
            lastAnalyzedSsid = ssid
            macHistory.clear()
            lastKnownMac.clear()
        }

        checkGatewayMac(table, baseline, ssid)
        checkMacDuplicates(table, ssid)
    }

    private fun checkGatewayMac(
        table: List<ArpEntry>,
        baseline: NetworkBaseline,
        ssid: String
    ) {
        val currentMac = table.find { it.ip == baseline.gatewayIp }?.mac ?: return

        // Gérer l'historique pour détecter les alternances
        val history = macHistory.getOrPut(baseline.gatewayIp) { mutableListOf() }

        val lastMac = lastKnownMac[baseline.gatewayIp]

        if (currentMac != lastMac) {
            // MAC a changé
            lastKnownMac[baseline.gatewayIp] = currentMac
            history.add(currentMac)

            // Garder seulement les 5 derniers MACs
            while (history.size > 5) history.removeAt(0)

            // Alerte 1 : changement par rapport à la baseline
            if (currentMac != baseline.gatewayMac) {
                scoreEngine.addSignal(
                    ssid = ssid,
                    type = SignalType.ARP_GATEWAY_CHANGE,
                    detail = "Gateway ${baseline.gatewayIp}: ${baseline.gatewayMac} → $currentMac"
                )
            }

            // Alerte 2 : alternance anormale (flip-flop entre 2+ MACs différents)
            if (history.size >= 3 && history.distinct().size >= 2) {
                scoreEngine.addSignal(
                    ssid = ssid,
                    type = SignalType.ARP_GATEWAY_CHANGE,
                    detail = "Gateway ${baseline.gatewayIp}: alternance MAC détectée (ARP flood) ${history.distinct().joinToString(" ↔ ")}"
                )
            }
        }
    }

    private fun checkMacDuplicates(table: List<ArpEntry>, ssid: String) {
        val duplicates = table.groupBy { it.mac }
            .filter { (_, entries) ->
                entries.size > 1 && entries.none { e -> e.mac == "00:00:00:00:00:00" }
            }

        duplicates.forEach { (mac, entries) ->
            val ips = entries.joinToString(", ") { it.ip }
            scoreEngine.addSignal(
                ssid = ssid,
                type = SignalType.ARP_MAC_DUPLICATE,
                detail = "MAC $mac répond pour : $ips (ARP Spoofing probable)"
            )
        }
    }
}
