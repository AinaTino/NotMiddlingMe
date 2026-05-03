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

        // 4. IPv6 Rogue RA
        val hasIPv6Gateway = lp.routes.any { it.isDefaultRoute && it.gateway?.hostAddress?.contains(":") == true }
        val hadIPv6Gateway = baseline.gatewayIp.contains(":") // Simplification

        if (hasIPv6Gateway && !hadIPv6Gateway) {
            scoreEngine.addSignal(
                ssid = ssid,
                type = SignalType.IPV6_ROGUE_RA,
                detail = "Apparition d'une gateway IPv6 sur un réseau IPv4"
            )
        }
    }

    private fun isPrivateIp(ip: String?): Boolean {
        if (ip == null) return false
        return ip.startsWith("192.168.") ||
               ip.startsWith("10.") ||
               ip.startsWith("172.") // Simplifié pour la démonstration
    }
}
