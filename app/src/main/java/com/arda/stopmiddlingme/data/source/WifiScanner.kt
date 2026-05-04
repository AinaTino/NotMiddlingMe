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
    fun getFullNetworkInfo(): NetworkInfo? {
        try {
            val network = connectivityManager.activeNetwork ?: return null
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
            
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

            val linkProperties = connectivityManager.getLinkProperties(network)
            val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                capabilities.transportInfo as? WifiInfo
            } else {
                @Suppress("DEPRECATION")
                wifiManager.connectionInfo
            }

            val ssid = wifiInfo?.ssid?.removeSurrounding("\"")?.let {
                if (it == "<unknown ssid>" || it == "0x") null else it
            } ?: "Non connecté"

            val bssid = wifiInfo?.bssid ?: "—"
            
            val gatewayIp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                linkProperties?.dhcpServerAddress?.hostAddress
            } else {
                null
            } ?: linkProperties?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress 
              ?: "—"
            
            val dnsServers = linkProperties?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()

            return NetworkInfo(
                ssid = ssid,
                bssid = bssid,
                gatewayIp = gatewayIp,
                gatewayMac = "—",
                dnsServers = dnsServers,
                isConnected = true
            )
        } catch (e: Exception) {
            return null
        }
    }

    @SuppressLint("MissingPermission")
    fun getCurrentSsid(): String? {
        return getFullNetworkInfo()?.ssid.takeIf { it != "Non connecté" }
    }

    @SuppressLint("MissingPermission")
    fun getCurrentBssid(): String? {
        return getFullNetworkInfo()?.bssid
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
