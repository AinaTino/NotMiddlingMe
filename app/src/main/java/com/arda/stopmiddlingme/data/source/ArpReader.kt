package com.arda.stopmiddlingme.data.source

import com.arda.stopmiddlingme.domain.model.ArpEntry
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArpReader @Inject constructor() {

    fun readArpTable(): List<ArpEntry> {
        val entries = mutableListOf<ArpEntry>()

        // 1. Priorité à la commande native 'ip neigh'
        try {
            val process = Runtime.getRuntime().exec("ip neigh show")
            val reader = process.inputStream.bufferedReader()
            reader.forEachLine { line ->
                // Exemple de retour: "192.168.88.1 dev wlan0 lladdr aa:bb:cc:dd:ee:ff REACHABLE"
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 5 && parts.contains("lladdr")) {
                    val ip = parts[0]
                    val macIndex = parts.indexOf("lladdr") + 1
                    val devIndex = parts.indexOf("dev") + 1

                    if (macIndex < parts.size) {
                        val mac = parts[macIndex].uppercase()
                        val device = if (devIndex < parts.size) parts[devIndex] else "wlan0"

                        if (mac != "00:00:00:00:00:00" && mac.contains(":")) {
                            entries.add(ArpEntry(ip = ip, mac = mac, device = device))
                        }
                    }
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fallback sur l'ancienne méthode si la première échoue (ou retourne vide)
        if (entries.isEmpty()) {
            try {
                val file = File("/proc/net/arp")
                if (file.exists() && file.canRead()) {
                    file.forEachLine { line ->
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.size >= 6 && parts[0] != "IP") {
                            val mac = parts[3].uppercase()
                            if (mac != "00:00:00:00:00:00" && mac.contains(":")) {
                                entries.add(ArpEntry(ip = parts[0], mac = mac, device = parts[5]))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // distinctBy évite d'avoir la même IP en double si elle a plusieurs statuts
        return entries.distinctBy { it.ip }
    }
}