package com.arda.stopmiddlingme.domain.analyzer

import com.arda.stopmiddlingme.domain.engine.ScoreEngine
import com.arda.stopmiddlingme.domain.model.SignalType
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CertAnalyzer @Inject constructor(
    private val scoreEngine: ScoreEngine
) {
    /**
     * Analyse un certificat intercepté
     */
    fun analyze(cert: X509Certificate, domain: String, ssid: String) {
        // 1. Vérifier si auto-signé
        if (isSelfSigned(cert)) {
            scoreEngine.addSignal(
                ssid = ssid,
                type = SignalType.CERT_SELF_SIGNED,
                detail = "Certificat auto-signé pour $domain"
            )
        }

        // 2. Vérifier si le domaine correspond (simplifié)
        if (!domainMatches(cert, domain)) {
            scoreEngine.addSignal(
                ssid = ssid,
                type = SignalType.CERT_DOMAIN_MISMATCH,
                detail = "Le certificat ne correspond pas au domaine $domain"
            )
        }
    }

    private fun isSelfSigned(cert: X509Certificate): Boolean {
        return try {
            cert.verify(cert.publicKey)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun domainMatches(cert: X509Certificate, domain: String): Boolean {
        val commonName = cert.subjectX500Principal.name
        return commonName.contains("CN=$domain", ignoreCase = true) || 
               commonName.contains("CN=*." + domain.substringAfter("."), ignoreCase = true)
    }
}
