package com.arda.stopmiddlingme.ui.screen.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arda.stopmiddlingme.data.source.ArpReader
import com.arda.stopmiddlingme.data.source.WifiScanner
import com.arda.stopmiddlingme.domain.model.LanDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InetAddress
import java.net.NetworkInterface
import javax.inject.Inject
import java.util.concurrent.TimeUnit

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val arpReader: ArpReader,
    private val wifiScanner: WifiScanner
) : ViewModel() {

    private val _devices = MutableStateFlow<List<LanDevice>>(emptyList())
    val devices: StateFlow<List<LanDevice>> = _devices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    fun scanNetwork() {
        if (_isScanning.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            
            try {
                val wifiNetwork = wifiScanner.getWifiNetwork()
                val netInfo = wifiScanner.getFullNetworkInfo()
                
                // Détection de l'IP réelle du téléphone (on exclut la plage VPN 10.255.x.x)
                val selfIp = listOfNotNull(netInfo.localIp, getWifiIpAddress())
                    .find { it != "—" && it != "0.0.0.0" && !it.startsWith("10.255") } 
                    ?: netInfo.localIp
                
                val gatewayIp = netInfo.gatewayIp.takeIf { it != "—" && it != "0.0.0.0" }
                val reachedIps = mutableSetOf<String>()
                
                if (selfIp != "—" && selfIp != "0.0.0.0") reachedIps.add(selfIp)
                if (gatewayIp != null) reachedIps.add(gatewayIp)

                if (selfIp.contains(".") && selfIp.count { it == '.' } == 3) {
                    val subnet = selfIp.substringBeforeLast(".")
                    
                    // Scan agressif mais filtré pour réveiller la table ARP de l'OS
                    (1..254).chunked(32).forEach { chunk ->
                        chunk.map { i ->
                            async {
                                val target = "$subnet.$i"
                                if (target == selfIp || target.startsWith("10.255")) return@async
                                
                                // 1. UDP Poke (Fire & Forget)
                                try {
                                    java.net.DatagramSocket().use { ds ->
                                        wifiNetwork?.bindSocket(ds)
                                        val packet = java.net.DatagramPacket(ByteArray(0), 0, InetAddress.getByName(target), 7)
                                        ds.send(packet)
                                    }
                                } catch (e: Exception) { }

                                // 2. TCP Probe rapide (port 80)
                                try {
                                    java.net.Socket().use { socket ->
                                        wifiNetwork?.bindSocket(socket)
                                        socket.connect(java.net.InetSocketAddress(target, 80), 150)
                                        synchronized(reachedIps) { reachedIps.add(target) }
                                    }
                                } catch (e: Exception) { }

                                // 3. Ping natif
                                if (pingIpNative(target)) {
                                    synchronized(reachedIps) { reachedIps.add(target) }
                                }
                            }
                        }.awaitAll()
                        delay(20) // Petit délai entre les chunks pour la stabilité
                    }
                }
                
                // On laisse un peu de temps à la table ARP pour se stabiliser après les pings
                delay(800)
                
                val wifiInterface = wifiScanner.getWifiInterfaceName()
                val arpEntries = arpReader.readArpTable()
                
                // On filtre les entrées ARP pour ne garder que celles liées au WiFi/Ethernet réel
                val arpMap = arpEntries
                    .filter { entry ->
                        wifiInterface == null || 
                        entry.device == wifiInterface || 
                        entry.device.contains("wlan") || 
                        entry.device.contains("eth")
                    }
                    .associateBy { it.ip }
                
                // RÈGLE : Sur Android 10+, la table ARP est restreinte.
                // On affiche les IPs qui ont répondu au scan (reachedIps) 
                // ET celles présentes dans l'ARP (si dispo).
                val finalIps = (arpMap.keys + reachedIps)
                    .filter { it.contains(".") && it != "0.0.0.0" }
                    .distinct()
                
                val lanDevices = finalIps
                    .map { ip ->
                        val arpEntry = arpMap[ip]
                        val isSelf = (ip == selfIp)
                        val isGateway = (ip == gatewayIp)
                        
                        LanDevice(
                            ip = ip,
                            mac = arpEntry?.mac ?: if (isSelf) "Moi" else "Inconnu",
                            manufacturer = null,
                            isGateway = isGateway,
                            isSelf = isSelf
                        )
                    }.sortedWith(compareByDescending<LanDevice> { it.isSelf }
                        .thenByDescending { it.isGateway }
                        .thenBy { ipToLong(it.ip) }
                    )
                
                _devices.value = lanDevices
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    private suspend fun pingIpNative(ipAddress: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Utilisation du ping système qui est plus fiable que isReachable()
            val process = Runtime.getRuntime().exec("ping -c 1 -W 1 $ipAddress")
            val completed = process.waitFor(800, TimeUnit.MILLISECONDS)
            completed && process.exitValue() == 0
        } catch (e: Exception) { false }
    }

    private fun getWifiIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback || intf.name.contains("tun") || intf.name.contains("vpn")) continue
                for (addr in intf.inetAddresses) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
                }
            }
        } catch (e: Exception) { }
        return null
    }

    private fun ipToLong(ip: String): Long {
        return try {
            val parts = ip.split(".")
            if (parts.size != 4) return 0L
            (parts[0].toLong() shl 24) + (parts[1].toLong() shl 16) + (parts[2].toLong() shl 8) + parts[3].toLong()
        } catch (e: Exception) { 0L }
    }
}
