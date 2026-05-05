package com.arda.stopmiddlingme.data.source

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.arda.stopmiddlingme.domain.model.ArpEntry
import com.arda.stopmiddlingme.domain.model.LanDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

/**
 * Scanner LAN basé sur /proc/net/arp.
 * Pas de root requis — lecture simple du fichier système.
 *
 * Limitutions :
 * - Ne voit que les appareils avec lesquels le téléphone a communiqué (ARP learned)
 * - Les appareils silencieux ne seront pas visibles
 * - Pour un vrai scan : implémenter ARP PING broadcast
 */
@Singleton
class LanScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val arpReader: ArpReader,
    private val wifiScanner: WifiScanner
) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * Scanne le LAN en lisant /proc/net/arp et en résolvant les OUI fabricants
     */
    fun scanLan(): List<LanDevice> {
        val devices = mutableListOf<LanDevice>()

        try {
            val arpTable = arpReader.readArpTable()
            val networkInfo = wifiScanner.getFullNetworkInfo()
            val localIp = networkInfo.localIp
            val gatewayIp = networkInfo.gatewayIp
            val gatewayMac = networkInfo.gatewayMac

            arpTable.forEach { entry ->
                val manufacturer = resolveOui(entry.mac)

                val device = LanDevice(
                    ip = entry.ip,
                    mac = entry.mac,
                    manufacturer = manufacturer,
                    isGateway = entry.ip == gatewayIp,
                    isSelf = entry.ip == localIp
                )

                devices.add(device)
            }

            // Tri : gateway first, puis self, puis autres
            devices.sortBy {
                when {
                    it.isGateway -> 0
                    it.isSelf -> 1
                    else -> 2
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return devices
    }

    /**
     * Résout le fabricant d'une MAC address via OUI lookup
     * Format MAC: AA:BB:CC:DD:EE:FF
     * OUI = AA:BB:CC (3 premiers octets)
     */
    private fun resolveOui(mac: String): String? {
        return try {
            val oui = mac.take(8).toUpperCase()

            // Heuristiques simples sans base de données externe
            when (oui) {
                in "00:00:00".."00:FF:FF" -> "Réservé"
                in "08:00:00".."08:00:FF" -> "Apple"
                in "A4:C3:F0".."A4:C3:FF" -> "Apple"
                in "B4:7C:9C".."B4:7C:FF" -> "Samsung"
                in "FC:F5:C1".."FC:F5:FF" -> "OnePlus"
                in "1C:1D:86".."1C:1D:FF" -> "OnePlus"
                in "00:1E:EC".."00:1E:FF" -> "Cisco"
                in "08:54:1F".."08:54:FF" -> "Dell"
                in "88:63:DF".."88:63:FF" -> "TP-Link"
                in "9C:EF:D5".."9C:EF:FF" -> "TP-Link"
                in "AC:9E:17".."AC:9E:FF" -> "Roku"
                in "00:12:47".."00:12:FF" -> "Nintendo"
                else -> {
                    // Broadcast/multicast?
                    if (mac.endsWith(":FF:FF") || mac.endsWith(":00:00")) {
                        "Broadcast"
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Obtient seulement les appareils visibles (excluant gateway et self par défaut)
     */
    fun scanLanVisibleOnly(): List<LanDevice> {
        return scanLan().filter { !it.isGateway && !it.isSelf }
    }

    /**
     * Obtient seulement les appareils suspects (fabricant inconnu)
     */
    fun scanLanSuspicious(): List<LanDevice> {
        return scanLan().filter { it.manufacturer == null && !it.isGateway && !it.isSelf }
    }
}

