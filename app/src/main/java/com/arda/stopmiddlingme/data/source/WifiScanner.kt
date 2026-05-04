package com.arda.stopmiddlingme.data.source

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import com.arda.stopmiddlingme.domain.model.WifiAp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

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
    fun getCurrentSsid(): String? {
        val info = wifiManager.connectionInfo
        return if (info != null && info.networkId != -1) {
            info.ssid?.removeSurrounding("\"")?.let {
                if (it == "<unknown ssid>") null else it
            }
        } else {
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun getCurrentBssid(): String? {
        val info = wifiManager.connectionInfo
        return if (info != null && info.networkId != -1) {
            info.bssid
        } else {
            null
        }
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
