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
            if (file.exists()) {
                file.forEachLine { line ->
                    // format : IP HWtype Flags HWaddress Mask Device
                    // ex    : 192.168.1.1 0x1 0x2 aa:bb:cc:dd:ee:ff * wlan0
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 6 && parts[0] != "IP") {
                        val mac = parts[3]
                        if (mac != "00:00:00:00:00:00") { // entrées invalides
                            entries.add(
                                ArpEntry(
                                    ip = parts[0],
                                    mac = mac.uppercase(),
                                    device = parts[5]
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: IOException) {
            // /proc/net/arp non lisible sur certaines versions d'Android ou sans permissions
        }
        return entries
    }
}
