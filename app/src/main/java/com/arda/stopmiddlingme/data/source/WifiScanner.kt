package com.arda.stopmiddlingme.data.source

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import com.arda.stopmiddlingme.domain.model.NetworkInfo
import com.arda.stopmiddlingme.domain.model.WifiAp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun getWifiNetwork(): Network? {
        return try {
            connectivityManager.allNetworks.find { 
                val caps = connectivityManager.getNetworkCapabilities(it)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        } catch (e: Exception) {
            null
        }
    }

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

    fun getCurrentSsid(): String? {
        return try {
            val network = getWifiNetwork()
            val capabilities = connectivityManager.getNetworkCapabilities(network ?: connectivityManager.activeNetwork)
            
            val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                capabilities?.transportInfo as? WifiInfo
            } else {
                @Suppress("DEPRECATION")
                wifiManager.connectionInfo
            }

            var ssid = wifiInfo?.ssid?.removeSurrounding("\"")
            if (ssid == "<unknown ssid>" || ssid == null) {
                @Suppress("DEPRECATION")
                ssid = wifiManager.connectionInfo.ssid?.removeSurrounding("\"")
            }
            
            ssid?.takeIf { it != "<unknown ssid>" && !it.isNullOrBlank() }
        } catch (e: Exception) {
            null
        }
    }

    fun getFullNetworkInfo(): NetworkInfo {
        var ssid = "Inconnu"
        var bssid = "—"
        var localIp = "—"
        var gatewayIp = "—"
        var isConnected = false
        var security = "WPA2"

        try {
            val wifiNetwork = getWifiNetwork()
            val lp = connectivityManager.getLinkProperties(wifiNetwork)
            val capabilities = connectivityManager.getNetworkCapabilities(wifiNetwork)

            // 1. IP Locale (via LinkProperties du réseau WiFi spécifiquement)
            localIp = lp?.linkAddresses?.find { it.address is java.net.Inet4Address }?.address?.hostAddress 
                ?: getWifiIpFallback() 
                ?: "—"

            // 2. Gateway
            gatewayIp = lp?.routes?.find { it.isDefaultRoute && it.gateway != null }?.gateway?.hostAddress ?: "—"
            if (gatewayIp == "—") {
                val dhcp = wifiManager.dhcpInfo
                if (dhcp != null && dhcp.gateway != 0) {
                    gatewayIp = Formatter.formatIpAddress(dhcp.gateway)
                }
            }

            // 3. SSID / BSSID
            val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                capabilities?.transportInfo as? WifiInfo
            } else {
                @Suppress("DEPRECATION")
                wifiManager.connectionInfo
            }

            if (wifiInfo != null) {
                ssid = wifiInfo.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" } ?: "WiFi"
                bssid = wifiInfo.bssid ?: "—"
                isConnected = true
            }
            
            if (ssid == "WiFi" || ssid == "Inconnu") {
                @Suppress("DEPRECATION")
                val info = wifiManager.connectionInfo
                val backupSsid = info.ssid?.removeSurrounding("\"")
                if (backupSsid != null && backupSsid != "<unknown ssid>") ssid = backupSsid
                if (bssid == "—") bssid = info.bssid ?: "—"
            }

            // 4. Sécurité
            if (bssid != "—") {
                try {
                    security = wifiManager.scanResults.find { it.BSSID == bssid }?.let { 
                        parseCapabilities(it.capabilities) 
                    } ?: "WPA2"
                } catch (e: Exception) {}
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return NetworkInfo(
            ssid = ssid,
            bssid = bssid,
            localIp = localIp,
            gatewayIp = gatewayIp,
            gatewayMac = "—",
            dnsServers = emptyList(),
            security = security,
            isConnected = isConnected || localIp != "—"
        )
    }

    private fun getWifiIpFallback(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp || intf.name.contains("tun")) continue
                if (intf.name.contains("wlan") || intf.name.contains("eth")) {
                    for (addr in intf.inetAddresses) {
                        if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {}
        return null
    }

    private fun parseCapabilities(capabilities: String): String {
        return when {
            capabilities.contains("WPA3") -> "WPA3"
            capabilities.contains("WPA2") -> "WPA2"
            capabilities.contains("WPA") -> "WPA"
            capabilities.contains("WEP") -> "WEP"
            capabilities.contains("OPEN") || capabilities.isBlank() -> "OPEN"
            else -> "WPA2"
        }
    }
}
