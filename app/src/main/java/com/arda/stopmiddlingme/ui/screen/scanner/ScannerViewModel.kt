package com.arda.stopmiddlingme.ui.screen.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arda.stopmiddlingme.data.source.ArpReader
import com.arda.stopmiddlingme.data.source.WifiScanner
import com.arda.stopmiddlingme.domain.model.LanDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.NetworkInterface
import javax.inject.Inject

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
            
            val netInfo = wifiScanner.getFullNetworkInfo()
            
            // CRITIQUE : On récupère l'IP du WiFi, pas celle du VPN
            val selfIp = netInfo.localIp.takeIf { it != "—" && !it.startsWith("10.0.0") } 
                ?: getWifiIpAddress() 
                ?: "—"
            
            val gatewayIp = netInfo.gatewayIp.takeIf { it != "—" }
            
            val reachedIps = mutableSetOf<String>()
            if (selfIp != "—") reachedIps.add(selfIp)
            if (gatewayIp != null && gatewayIp != "—") reachedIps.add(gatewayIp)

            if (selfIp.contains(".")) {
                val subnet = selfIp.substringBeforeLast(".")
                // Scan par paquets pour ne pas saturer le WiFi
                (1..254).chunked(50).forEach { chunk ->
                    chunk.map { i ->
                        async {
                            val target = "$subnet.$i"
                            if (target == selfIp) return@async
                            try {
                                val address = InetAddress.getByName(target)
                                // 1. Essai Ping (500ms pour laisser le temps aux vieux appareils)
                                if (address.isReachable(500)) {
                                    synchronized(reachedIps) { reachedIps.add(target) }
                                } else {
                                    // 2. Scan de ports pour réveiller les PC/Phones
                                    val ports = listOf(443, 445, 80, 139)
                                    for (port in ports) {
                                        try {
                                            java.net.Socket().use { socket ->
                                                socket.connect(java.net.InetSocketAddress(target, port), 300)
                                                synchronized(reachedIps) { reachedIps.add(target) }
                                                return@async 
                                            }
                                        } catch (e: java.net.ConnectException) {
                                            // L'appareil a refusé : il est donc présent !
                                            synchronized(reachedIps) { reachedIps.add(target) }
                                            return@async
                                        } catch (e: Exception) { }
                                    }
                                }
                            } catch (e: Exception) { }
                        }
                    }.awaitAll()
                }
            }
            
            val arpEntries = arpReader.readArpTable()
            val arpMap = arpEntries.associateBy { it.ip }
            
            // On ne garde que les IPs qui sont soit "Moi", soit la "Gateway", 
            // soit qui ont une entrée MAC valide dans la table ARP.
            // Cela élimine les "fantômes" qui répondent au scan mais n'ont pas de présence physique.
            val lanDevices = (reachedIps + arpMap.keys)
                .filter { it.contains(".") }
                .mapNotNull { ip ->
                    val arpEntry = arpMap[ip]
                    val isSelf = ip == selfIp
                    val isGateway = ip == gatewayIp
                    
                    // Si ce n'est ni moi, ni le routeur, et qu'on n'a pas de MAC : on ignore (fantôme)
                    if (!isSelf && !isGateway && arpEntry == null) return@mapNotNull null

                    LanDevice(
                        ip = ip,
                        mac = arpEntry?.mac ?: if (isSelf) "Moi" else "Inconnu", 
                        manufacturer = null, 
                        isGateway = isGateway,
                        isSelf = isSelf
                    )
                }.sortedWith(compareByDescending<LanDevice> { it.isSelf }
                    .thenByDescending { it.isGateway }
                    .thenBy { 
                        try { ipToLong(it.ip) } catch(e: Exception) { 0L }
                    }
                )
            
            _devices.value = lanDevices
            _isScanning.value = false
        }
    }

    private fun getWifiIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces.asSequence()) {
                if (!intf.isUp || intf.isLoopback || intf.name.contains("tun")) continue
                if (intf.name.contains("wlan") || intf.name.contains("eth")) {
                    for (addr in intf.inetAddresses.asSequence()) {
                        if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                            return addr.hostAddress
                        }
                    }
                }
            }
        } catch (ex: Exception) { }
        return null
    }

    private fun ipToLong(ip: String): Long {
        val parts = ip.split(".")
        return (parts[0].toLong() shl 24) + (parts[1].toLong() shl 16) + (parts[2].toLong() shl 8) + parts[3].toLong()
    }
}
