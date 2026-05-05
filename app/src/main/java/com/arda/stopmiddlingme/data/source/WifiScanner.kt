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
            val caps = if (wifiNetwork != null) connectivityManager.getNetworkCapabilities(wifiNetwork) else null
            
            // Backup via DhcpInfo : TRÈS FIABLE même sous VPN
            val dhcp = wifiManager.dhcpInfo
            if (dhcp != null) {
                if (dhcp.ipAddress != 0) {
                    localIp = intToIp(dhcp.ipAddress)
                }
                if (dhcp.gateway != 0) {
                    gatewayIp = intToIp(dhcp.gateway)
                }
            }

            // Si DhcpInfo a échoué, on tente LinkProperties
            if (localIp == "—" || localIp == "0.0.0.0") {
                val lp = connectivityManager.getLinkProperties(wifiNetwork ?: connectivityManager.activeNetwork)
                localIp = lp?.linkAddresses?.find { it.address is java.net.Inet4Address }?.address?.hostAddress ?: "—"
                if (gatewayIp == "—") {
                    gatewayIp = lp?.routes?.find { it.isDefaultRoute && it.gateway != null }?.gateway?.hostAddress ?: "—"
                }
            }

            // SSID / BSSID
            val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                caps?.transportInfo as? WifiInfo
            } else {
                @Suppress("DEPRECATION")
                wifiManager.connectionInfo
            }

            if (wifiInfo != null) {
                ssid = wifiInfo.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" } ?: "Inconnu"
                bssid = wifiInfo.bssid ?: "—"
                isConnected = true
            }
            
            // Fallback SSID si masqué (ex: GPS désactivé)
            if ((ssid == "Inconnu" || ssid == "WiFi") && bssid != "—" && bssid != "02:00:00:00:00:00") {
                ssid = "Réseau_${bssid.replace(":", "").takeLast(4).uppercase()}"
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
            isConnected = isConnected || (localIp != "—" && localIp != "0.0.0.0")
        )
    }

    private fun intToIp(i: Int): String {
        return Formatter.formatIpAddress(i)
    }

    fun getWifiInterfaceName(): String? {
        return try {
            val network = getWifiNetwork()
            val lp = if (network != null) connectivityManager.getLinkProperties(network) else null
            lp?.interfaceName ?: NetworkInterface.getNetworkInterfaces()?.asSequence()?.find { 
                it.isUp && !it.isLoopback && (it.name.contains("wlan") || it.name.contains("eth"))
            }?.name
        } catch (e: Exception) { null }
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
