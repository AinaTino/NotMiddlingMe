package com.arda.stopmiddlingme.domain.analyzer

import com.arda.stopmiddlingme.domain.engine.ScoreEngine
import com.arda.stopmiddlingme.domain.model.SignalType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    fun analyze(resolvedIp: String, domain: String, ssid: String) {
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val trustedIp = queryTrustedDns(domain)
                if (trustedIp != null && !sameSubnet16(resolvedIp, trustedIp)) {
                    scoreEngine.addSignal(
                        ssid = ssid,
                        type = SignalType.DNS_PRIVATE_IP,
                        detail = "$domain : divergence locale ($resolvedIp) vs de confiance ($trustedIp)"
                    )
                }
            } catch (e: Exception) {
                // Pas de signal si le DNS de confiance est injoignable (réseau peut-être coupé)
            }
        }
    }

    private fun isPrivateIp(ip: String) = privateRanges.any { it.matches(ip) }

    private fun sameSubnet16(ip1: String, ip2: String): Boolean {
        val parts1 = ip1.split(".")
        val parts2 = ip2.split(".")
        if (parts1.size < 2 || parts2.size < 2) return false
        return parts1[0] == parts2[0] && parts1[1] == parts2[1]
    }

    private fun queryTrustedDns(domain: String): String? {
        return try {
            val lookup = Lookup(domain, Type.A)
            lookup.setResolver(SimpleResolver(trustedDns))
            val records = lookup.run()
            if (lookup.result == Lookup.SUCCESSFUL && records != null && records.isNotEmpty()) {
                records[0].toString().split(" ").last()
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
