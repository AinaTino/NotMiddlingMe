package com.arda.stopmiddlingme.data.source

import com.arda.stopmiddlingme.domain.model.ArpEntry
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArpReader @Inject constructor() {

    fun readArpTable(): List<ArpEntry> {
        val entries = mutableListOf<ArpEntry>()
        try {
            val file = File("/proc/net/arp")
            if (file.exists() && file.canRead()) {
                file.forEachLine { line ->
                    // format : IP HWtype Flags HWaddress Mask Device
                    // ex    : 192.168.1.1 0x1 0x2 aa:bb:cc:dd:ee:ff * wlan0
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 6 && parts[0] != "IP") {
                        val mac = parts[3].uppercase()
                        
                        // Sur Android 10+, l'OS peut renvoyer 00:00:00:00:00:00 ou masquer
                        // On accepte tout ce qui ressemble à une MAC, même masquée
                        if (mac != "00:00:00:00:00:00" && mac.contains(":")) {
                            entries.add(
                                ArpEntry(
                                    ip = parts[0],
                                    mac = mac,
                                    device = parts[5]
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Logged as debug to avoid noise, common on Android 10+
        }
        
        // Fallback: Si la table est vide (Android 10+), on pourrait tenter 
        // d'utiliser IPNeighbor si on était root, mais ici on reste passif.

        return entries
    }
}
