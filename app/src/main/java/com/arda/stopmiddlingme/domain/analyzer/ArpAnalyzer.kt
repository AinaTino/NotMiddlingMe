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
    private var lastReportedGatewayMac: String? = null
    private val reportedDuplicateMacs = mutableSetOf<String>()

    suspend fun analyze(table: List<ArpEntry>, ssid: String) {
        val baseline = baselineRepo.get(ssid) ?: return

        if (ssid != lastAnalyzedSsid) {
            lastReportedGatewayMac = null
            reportedDuplicateMacs.clear()
            lastAnalyzedSsid = ssid
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

        if (currentMac != baseline.gatewayMac && currentMac != lastReportedGatewayMac) {
            lastReportedGatewayMac = currentMac
            scoreEngine.addSignal(
                ssid = ssid,
                type = SignalType.ARP_GATEWAY_CHANGE,
                detail = "Gateway ${baseline.gatewayIp}: ${baseline.gatewayMac} → $currentMac"
            )
        }

        if (currentMac == baseline.gatewayMac) {
            lastReportedGatewayMac = null
        }
    }

    private fun checkMacDuplicates(table: List<ArpEntry>, ssid: String) {
        val duplicates = table.groupBy { it.mac }
            .filter { (_, entries) -> entries.size > 1 }

        duplicates.forEach { (mac, entries) ->
            if (mac !in reportedDuplicateMacs) {
                reportedDuplicateMacs.add(mac)
                val ips = entries.joinToString(", ") { it.ip }
                scoreEngine.addSignal(
                    ssid = ssid,
                    type = SignalType.ARP_MAC_DUPLICATE,
                    detail = "MAC $mac répond pour : $ips"
                )
            }
        }

        reportedDuplicateMacs.retainAll(duplicates.keys)
    }
}
