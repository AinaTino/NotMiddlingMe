package com.arda.stopmiddlingme.data.source

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import com.arda.stopmiddlingme.domain.model.NetworkInfo
import com.arda.stopmiddlingme.domain.model.WifiAp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.InetAddress
import java.net.NetworkInterface
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

    fun getCurrentSsid(): String? {
        return try {
            val cm = connectivityManager
            val activeNetwork = cm.activeNetwork
            
            val wifiNetwork = cm.allNetworks.firstOrNull { 
                cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true 
            }
            
            val networkToQuery = wifiNetwork ?: activeNetwork
            val capabilities = cm.getNetworkCapabilities(networkToQuery)
            
            val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                capabilities?.transportInfo as? WifiInfo
            } else {
                @Suppress("DEPRECATION")
                wifiManager.connectionInfo
            }

            wifiInfo?.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }
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
            // 1. IP Locale via NetworkInterface (La plus fiable sous VPN)
            localIp = getLocalIpFallback() ?: "—"

            // 2. Gateway via DHCP (Très fiable sur WiFi)
            val dhcp = wifiManager.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0) {
                gatewayIp = Formatter.formatIpAddress(dhcp.gateway)
            }

            // 3. Infos WiFi via ConnectivityManager
            val cm = connectivityManager
            val activeNetwork = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(activeNetwork)
            
            val wifiNetwork = cm.allNetworks.firstOrNull { 
                cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true 
            }
            
            val networkToQuery = wifiNetwork ?: activeNetwork
            val lp = cm.getLinkProperties(networkToQuery)
            
            if (gatewayIp == "—") {
                gatewayIp = lp?.routes?.firstOrNull { it.isDefaultRoute && it.gateway != null }?.gateway?.hostAddress ?: "—"
            }
            
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

            // 4. Sécurité (nécessite permission localisation)
            try {
                if (bssid != "—") {
                    security = wifiManager.scanResults.find { it.BSSID == bssid }?.let { 
                        parseCapabilities(it.capabilities) 
                    } ?: "WPA2"
                }
            } catch (e: SecurityException) {}

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

    private fun getLocalIpFallback(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val interfaceList = interfaces.toList()
            
            // Priorité aux interfaces physiques
            for (intf in interfaceList) {
                if (intf.isLoopback || !intf.isUp) continue
                if (intf.name.startsWith("wlan") || intf.name.startsWith("eth")) {
                    for (addr in intf.inetAddresses) {
                        if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
                    }
                }
            }
            // Repli sur n'importe quel IPv4 non-VPN
            for (intf in interfaceList) {
                if (intf.isLoopback || !intf.isUp || intf.name.contains("tun")) continue
                for (addr in intf.inetAddresses) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
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
