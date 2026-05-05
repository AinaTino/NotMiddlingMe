package com.arda.stopmiddlingme.data.source

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.arda.stopmiddlingme.domain.model.NetworkInfo
import com.arda.stopmiddlingme.domain.model.WifiAp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @SuppressLint("MissingPermission")
    fun getScanResults(): List<WifiAp> {
        return try {
            wifiManager.scanResults.map { result ->
                WifiAp(
                    ssid = result.SSID ?: "",
                    bssid = result.BSSID ?: "",
                    security = parseCapabilities(result.capabilities),
                    signalLevel = result.level
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun getFullNetworkInfo(): NetworkInfo {
        return try {
            val network = connectivityManager.activeNetwork
            val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            if (!isWifi) {
                // Pas sur WiFi — retourne un état "déconnecté" plutôt que null
                return NetworkInfo(
                    ssid       = "—",
                    bssid      = "—",
                    gatewayIp  = "—",
                    gatewayMac = "—",
                    dnsServers = emptyList(),
                    isConnected = false
                )
            }

            val linkProperties = connectivityManager.getLinkProperties(network)
            val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                capabilities.transportInfo as? WifiInfo
            } else {
                @Suppress("DEPRECATION")
                wifiManager.connectionInfo
            }

            val ssid = wifiInfo?.ssid
                ?.removeSurrounding("\"")
                ?.takeIf { it != "<unknown ssid>" && it != "0x" && it.isNotEmpty() }
                ?: "Permission manquante"

            val gatewayIp = linkProperties?.routes
                ?.firstOrNull { it.isDefaultRoute && it.gateway != null }
                ?.gateway?.hostAddress ?: "—"

            val dnsServers = linkProperties?.dnsServers
                ?.mapNotNull { it.hostAddress }
                ?: emptyList()

            // Récupérer la sécurité via les scanResults pour le BSSID actuel
            val currentBssid = wifiInfo?.bssid
            val security = if (currentBssid != null) {
                wifiManager.scanResults.find { it.BSSID == currentBssid }?.let { 
                    parseCapabilities(it.capabilities) 
                } ?: "WPA2"
            } else "WPA2"

            NetworkInfo(
                ssid       = ssid,
                bssid      = currentBssid ?: "—",
                gatewayIp  = gatewayIp,
                gatewayMac = "—",
                dnsServers = dnsServers,
                security = security,
                isConnected = true
            )
        } catch (e: Exception) {
            NetworkInfo(
                ssid = "Erreur",
                bssid = "—",
                gatewayIp = "—",
                gatewayMac = "—",
                dnsServers = emptyList(),
                isConnected = false
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun getCurrentSsid(): String? {
        return getFullNetworkInfo().ssid.takeIf { it != "Non connecté" }
    }

    @SuppressLint("MissingPermission")
    fun getCurrentBssid(): String? {
        return getFullNetworkInfo().bssid
    }

    private fun parseCapabilities(capabilities: String): String {
        return when {
            capabilities.contains("WPA3", ignoreCase = true) -> "WPA3"
            capabilities.contains("WPA2", ignoreCase = true) -> "WPA2"
            capabilities.contains("WPA", ignoreCase = true) -> "WPA"
            capabilities.contains("WEP", ignoreCase = true) -> "WEP"
            capabilities.contains("OPEN", ignoreCase = true) || capabilities.isBlank() -> "OPEN"
            else -> "UNKNOWN"
        }
    }
}
