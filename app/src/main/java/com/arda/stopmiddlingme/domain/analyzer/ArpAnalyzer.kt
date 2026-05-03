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

    suspend fun analyze(table: List<ArpEntry>, ssid: String) {
        val baseline = baselineRepo.get(ssid) ?: return

        checkGatewayMac(table, baseline, ssid)
        checkMacDuplicates(table, ssid)
    }

    private fun checkGatewayMac(
        table: List<ArpEntry>,
        baseline: NetworkBaseline,
        ssid: String
    ) {
        val currentGatewayEntry = table.find { it.ip == baseline.gatewayIp }
            ?: return

        if (currentGatewayEntry.mac != baseline.gatewayMac) {
            scoreEngine.addSignal(
                ssid = ssid,
                type = SignalType.ARP_GATEWAY_CHANGE,
                detail = "Gateway ${baseline.gatewayIp}: " +
                        "${baseline.gatewayMac} → ${currentGatewayEntry.mac}"
            )
        }
    }

    private fun checkMacDuplicates(table: List<ArpEntry>, ssid: String) {
        val macToIps = table.groupBy { it.mac }

        macToIps.forEach { (mac, entries) ->
            if (entries.size > 1) {
                val ips = entries.joinToString(", ") { it.ip }
                scoreEngine.addSignal(
                    ssid = ssid,
                    type = SignalType.ARP_MAC_DUPLICATE,
                    detail = "MAC $mac répond pour : $ips"
                )
            }
        }
    }
}
