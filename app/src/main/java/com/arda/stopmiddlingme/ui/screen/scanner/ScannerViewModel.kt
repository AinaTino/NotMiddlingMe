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
import kotlinx.coroutines.withContext
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
            val selfIp = netInfo.localIp.takeIf { it != "—" } ?: getLocalIpAddress()
            val gatewayIp = netInfo.gatewayIp.takeIf { it != "—" }
            
            val reachedIps = mutableSetOf<String>()
            // S'assurer que soi-même et la passerelle sont là même si le scan échoue
            selfIp?.takeIf { it.contains(".") }?.let { reachedIps.add(it) }
            gatewayIp?.takeIf { it != null && it.contains(".") }?.let { reachedIps.add(it) }

            if (selfIp != null && selfIp.contains(".")) {
                val subnet = selfIp.substringBeforeLast(".")
                // Scan parallèle optimisé par paquets
                (1..254).chunked(40).forEach { chunk ->
                    chunk.map { i ->
                        async {
                            val target = "$subnet.$i"
                            if (target == selfIp) return@async
                            try {
                                val address = InetAddress.getByName(target)
                                // 1. Essai ICMP (Ping)
                                if (address.isReachable(400)) {
                                    synchronized(reachedIps) { reachedIps.add(target) }
                                } else {
                                    // 2. Fallback TCP sur port 80 pour forcer l'ARP
                                    java.net.Socket().use { socket ->
                                        socket.connect(java.net.InetSocketAddress(target, 80), 150)
                                        synchronized(reachedIps) { reachedIps.add(target) }
                                    }
                                }
                            } catch (e: Exception) {
                                // Échec silencieux pour les autres ports/adresses
                            }
                        }
                    }.awaitAll()
                }
            }
            
            val arpEntries = arpReader.readArpTable()
            val arpMap = arpEntries.associateBy { it.ip }
            
            // Fusion des sources
            val allIps = (reachedIps + arpMap.keys).toSet()
            
            val lanDevices = allIps.filter { it.contains(".") }.map { ip ->
                val arpEntry = arpMap[ip]
                LanDevice(
                    ip = ip,
                    mac = arpEntry?.mac ?: "Inconnu", 
                    manufacturer = null, 
                    isGateway = ip == gatewayIp,
                    isSelf = ip == selfIp
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

    private fun ipToLong(ip: String): Long {
        val parts = ip.split(".")
        return (parts[0].toLong() shl 24) + (parts[1].toLong() shl 16) + (parts[2].toLong() shl 8) + parts[3].toLong()
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces.asSequence()) {
                if (!intf.isUp || intf.isLoopback || intf.name.contains("p2p")) continue
                for (addr in intf.inetAddresses.asSequence()) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }
}
