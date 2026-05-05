package com.arda.stopmiddlingme.domain.model

data class ArpEntry(
    val ip: String,
    val mac: String,
    val device: String // interface réseau ex: wlan0
)

data class NetworkInfo(
    val ssid: String,
    val bssid: String,
    val localIp: String = "—",
    val gatewayIp: String,
    val gatewayMac: String,
    val dnsServers: List<String>,
    val security: String = "WPA2",
    val isConnected: Boolean
)

data class WifiAp(
    val ssid: String,
    val bssid: String,
    val security: String, // OPEN, WEP, WPA, WPA2, WPA3
    val signalLevel: Int  // -100 à 0 dBm
)

data class LanDevice(
    val ip: String,
    val mac: String,
    val manufacturer: String?, // résolu depuis OUI
    val isGateway: Boolean,
    val isSelf: Boolean
)
