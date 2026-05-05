package com.arda.stopmiddlingme.domain.analyzer

import com.arda.stopmiddlingme.domain.engine.ScoreEngine
import com.arda.stopmiddlingme.domain.model.SignalType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.xbill.DNS.ARecord
import org.xbill.DNS.Lookup
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DnsAnalyzer @Inject constructor(
    private val scoreEngine: ScoreEngine
) {
    private val trustedDns = "1.1.1.1"
    private val privateRanges = listOf(
        Regex("^10\\..*"),
        Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\..*"),
        Regex("^192\\.168\\..*"),
        Regex("^127\\..*"),
        Regex("^169\\.254\\..*")
    )

    fun analyze(resolvedIp: String, domain: String, ssid: String, scope: CoroutineScope) {
        // Règle 0 : Ne pas analyser si on n'est pas sur un WiFi connu (évite les alertes en 4G)
        if (ssid == "—" || ssid == "Unknown_WiFi" || ssid == "<unknown ssid>") return

        // Règle 1 — IP privée pour domaine public (ex: google.com -> 192.168.1.50)
        if (isPrivateIp(resolvedIp)) {
            scoreEngine.addSignal(
                ssid = ssid,
                type = SignalType.DNS_PRIVATE_IP,
                detail = "$domain résolu en IP privée : $resolvedIp"
            )
            return
        }

        // Règle 2 — Comparaison avec DNS de confiance (Cloudflare 1.1.1.1)
        scope.launch(Dispatchers.IO) {
            try {
                val trustedIp = queryTrustedDns(domain)
                
                // On ne compare que si on a une IP de confiance et qu'elle diffère de la locale
                if (trustedIp != null && resolvedIp != trustedIp) {
                    // On ne lève un signal que si les deux IPs ne sont pas dans le même /16
                    // pour tolérer les variations normales de CDN
                    if (!sameSubnet16(resolvedIp, trustedIp)) {
                        scoreEngine.addSignal(
                            ssid = ssid,
                            type = SignalType.DNS_SERVER_CHANGED,
                            detail = "$domain : local=$resolvedIp vs confiance=$trustedIp"
                        )
                    }
                }
            } catch (e: Exception) {
                // Pas de signal si le DNS de confiance est injoignable
            }
        }
    }

    private fun isPrivateIp(ip: String) = privateRanges.any { it.matches(ip) }

    private fun sameSubnet16(ip1: String, ip2: String): Boolean {
        val p1 = ip1.split(".")
        val p2 = ip2.split(".")
        if (p1.size < 2 || p2.size < 2) return false
        return p1[0] == p2[0] && p1[1] == p2[1]
    }

    private fun queryTrustedDns(domain: String): String? {
        return try {
            val lookup = Lookup(domain, Type.A)
            lookup.setResolver(SimpleResolver(trustedDns))
            val records = lookup.run()
            if (lookup.result == Lookup.SUCCESSFUL && records != null && records.isNotEmpty()) {
                val aRecord = records[0] as? ARecord
                aRecord?.address?.hostAddress
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
