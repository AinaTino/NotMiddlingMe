package com.arda.stopmiddlingme.domain.model

enum class SignalType(
    val poids: Int,
    // Ce signal seul suffit-il à déclencher CRITIQUE ?
    // true  = anomalie quasi-impossible légitimement (ex: cert self-signed sur domaine public)
    // false = cause légitime possible, pertinent seulement en corrélation avec d'autres signaux
    val standalone: Boolean,
    val decaySeconds: Int,
    val description: String
) {
    // ── ARP ──────────────────────────────────────────────────────────────────
    ARP_GATEWAY_CHANGE(
        poids = 5,
        standalone = true,
        decaySeconds = 60,
        description = "MAC de la gateway modifié"
    ),
    ARP_MAC_DUPLICATE(
        poids = 5,
        standalone = true,
        decaySeconds = 60,
        description = "Même MAC pour plusieurs IPs"
    ),
    ARP_NEW_ENTRY(
        poids = 1,
        standalone = false,
        decaySeconds = 30,
        description = "Nouvelle entrée ARP inattendue"
    ),

    // ── DHCP / Gateway ───────────────────────────────────────────────────────
    // LinkProperties change SANS onAvailable() préalable → DHCP non sollicité
    GATEWAY_CHANGE_UNSOLICITED(
        poids = 3,
        standalone = false,
        decaySeconds = 60,
        description = "Gateway changée sans événement de connexion"
    ),
    // Gateway différente de la baseline APRÈS connexion normale — signal faible,
    // cause légitime possible (admin a changé le routeur), ne vaut rien seul
    GATEWAY_IP_CHANGED(
        poids = 2,
        standalone = false,
        decaySeconds = 60,
        description = "IP de gateway différente de la baseline"
    ),
    // DNS configuré = IP privée jamais vue en baseline
    DNS_SERVER_UNKNOWN(
        poids = 3,
        standalone = false,
        decaySeconds = 60,
        description = "Serveur DNS configuré inconnu (IP privée)"
    ),

    // ── IPv6 ─────────────────────────────────────────────────────────────────
    // standalone=false car certains réseaux activent SLAAC IPv6 par défaut
    IPV6_ROGUE_RA(
        poids = 3,
        standalone = false,
        decaySeconds = 90,
        description = "Gateway IPv6 inconnue détectée (Rogue RA possible)"
    ),

    // ── Rogue AP ─────────────────────────────────────────────────────────────
    ROGUE_AP_BSSID(
        poids = 4,
        standalone = true,
        decaySeconds = 120,
        description = "BSSID différent pour le même SSID"
    ),
    ROGUE_AP_SECURITY(
        poids = 4,
        standalone = true,
        decaySeconds = 120,
        description = "Sécurité WiFi dégradée sur même SSID"
    ),
    ROGUE_AP_SIGNAL(
        poids = 1,
        standalone = false,
        decaySeconds = 60,
        description = "Signal WiFi anormalement fort"
    ),

    // ── DNS (via VpnService) ─────────────────────────────────────────────────
    DNS_PRIVATE_IP(
        poids = 4,
        standalone = true,
        decaySeconds = 30,
        description = "Domaine public résolu en IP privée"
    ),
    DNS_SERVER_CHANGED(
        poids = 3,
        standalone = false,
        decaySeconds = 60,
        description = "Serveur DNS actif a changé"
    ),
    TTL_ANORMAL(
        poids = 1,
        standalone = false,
        decaySeconds = 10,
        description = "TTL DNS anormalement bas"
    ),

    // ── TLS / HTTP (via VpnService) ──────────────────────────────────────────
    SSL_STRIP(
        poids = 3,
        standalone = false,
        decaySeconds = 20,
        description = "HTTP détecté vers domaine HTTPS-only (HSTS)"
    ),
    CERT_SELF_SIGNED(
        poids = 5,
        standalone = true,
        decaySeconds = 30,
        description = "Certificat TLS auto-signé"
    ),
    CERT_UNKNOWN_CA(
        poids = 4,
        standalone = true,
        decaySeconds = 30,
        description = "CA du certificat inconnue ou non standard"
    ),
    CERT_DOMAIN_MISMATCH(
        poids = 5,
        standalone = true,
        decaySeconds = 30,
        description = "Domaine ne correspond pas au certificat TLS"
    ),

    // ── Traffic ──────────────────────────────────────────────────────────────
    TRAFFIC_MIRROR(
        poids = 4,
        standalone = true,
        decaySeconds = 60,
        description = "Connexions dupliquées détectées"
    );

    fun expiresAt(fromMillis: Long): Long = fromMillis + (decaySeconds * 1000L)
}
