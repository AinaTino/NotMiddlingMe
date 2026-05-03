package com.arda.stopmiddlingme.domain.analyzer

import com.arda.stopmiddlingme.domain.engine.ScoreEngine
import com.arda.stopmiddlingme.domain.model.SignalType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SslStripAnalyzer @Inject constructor(
    private val scoreEngine: ScoreEngine
) {
    // Liste simplifiée de domaines connus pour être HSTS (HTTPS-only)
    private val hstsDomains = setOf(
        "google.com", "www.google.com",
        "facebook.com", "www.facebook.com",
        "twitter.com", "www.twitter.com",
        "github.com", "www.github.com",
        "paypal.com", "www.paypal.com",
        "amazon.com", "www.amazon.com",
        "bankofamerica.com", "chase.com"
    )

    /**
     * Analyse une tentative de connexion HTTP (port 80)
     */
    fun analyze(host: String, ssid: String) {
        if (isHstsDomain(host)) {
            scoreEngine.addSignal(
                ssid = ssid,
                type = SignalType.SSL_STRIP,
                detail = "Tentative de connexion HTTP vers domaine HSTS: $host"
            )
        }
    }

    private fun isHstsDomain(host: String): Boolean {
        val cleanHost = host.lowercase().trim()
        return hstsDomains.any { cleanHost == it || cleanHost.endsWith(".$it") }
    }
}
