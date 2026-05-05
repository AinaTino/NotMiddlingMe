package com.arda.stopmiddlingme.domain.analyzer

import android.net.LinkProperties
import com.arda.stopmiddlingme.data.repository.BaselineRepository
import com.arda.stopmiddlingme.domain.engine.ScoreEngine
import com.arda.stopmiddlingme.domain.model.SignalType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GatewayMonitor @Inject constructor(
    private val baselineRepo: BaselineRepository,
    private val scoreEngine: ScoreEngine
) {

    suspend fun onNetworkChanged(lp: LinkProperties, ssid: String, isUnsolicited: Boolean) {
        val baseline = baselineRepo.get(ssid) ?: return

        // 1. Détection DHCP non sollicité
        if (isUnsolicited) {
            scoreEngine.addSignal(
                ssid = ssid,
                type = SignalType.GATEWAY_CHANGE_UNSOLICITED,
                detail = "Changement de LinkProperties détecté sans reconnexion"
            )
        }

        // 2. Vérification IP Gateway
        val currentGateway = lp.routes.find { it.isDefaultRoute }?.gateway?.hostAddress
        if (currentGateway != null && currentGateway != baseline.gatewayIp) {
            scoreEngine.addSignal(
                ssid = ssid,
                type = SignalType.GATEWAY_IP_CHANGED,
                detail = "Gateway IP: ${baseline.gatewayIp} -> $currentGateway"
            )
        }

        // 3. Vérification DNS (IP privées inconnues)
        val currentDns = lp.dnsServers.map { it.hostAddress }
        val baselineDns = baselineRepo.parseDnsServers(baseline.dnsServers)

        currentDns.forEach { dns ->
            if (dns != null && isPrivateIp(dns) && dns !in baselineDns) {
                scoreEngine.addSignal(
                    ssid = ssid,
                    type = SignalType.DNS_SERVER_UNKNOWN,
                    detail = "Nouveau serveur DNS privé détecté : $dns"
                )
            }
        }

        // 4. IPv6 Rogue RA — Limite les faux positifs
        val currentIPv6Gateways = lp.routes
            .filter { it.isDefaultRoute && it.gateway?.hostAddress?.contains(":") == true }
            .mapNotNull { it.gateway?.hostAddress }

        // On ne fire que si une gateway IPv6 apparaît sur un réseau qui n'en avait pas en baseline
        val hadIPv6InBaseline = baseline.gatewayIp.contains(":")
        
        if (currentIPv6Gateways.isNotEmpty() && !hadIPv6InBaseline) {
            scoreEngine.addSignal(
                ssid = ssid,
                type = SignalType.IPV6_ROGUE_RA,
                detail = "Gateway IPv6 apparue : ${currentIPv6Gateways.first()}"
            )
        }
    }

    private fun isPrivateIp(ip: String): Boolean {
        val privateRanges = listOf(
            Regex("^10\\..*"),
            Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\..*"),  // 172.16 → 172.31 uniquement
            Regex("^192\\.168\\..*"),
            Regex("^127\\..*"),
            Regex("^169\\.254\\..*")
        )
        return privateRanges.any { it.matches(ip) }
    }
}
