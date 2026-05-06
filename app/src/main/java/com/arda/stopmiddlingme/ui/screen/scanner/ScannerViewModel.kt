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
import java.net.ConnectException
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
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

    // Ports courants — le refus (ConnectException) indique que l'hôte existe
    private val probePorts = listOf(80, 443, 22, 53, 8080, 8443, 445, 139, 5000, 7)

    fun scanNetwork() {
        if (_isScanning.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true

            try {
                val wifiNetwork = wifiScanner.getWifiNetwork()
                val netInfo     = wifiScanner.getFullNetworkInfo()

                // IP réelle du téléphone (exclure plage VPN 10.255.x.x)
                val selfIp = listOfNotNull(netInfo.localIp, getWifiIpAddress())
                    .find { ip -> ip != "—" && ip != "0.0.0.0" && !ip.startsWith("10.255") }
                    ?: netInfo.localIp

                val gatewayIp  = netInfo.gatewayIp.takeIf { it != "—" && it != "0.0.0.0" }
                val reachedIps = mutableSetOf<String>()

                if (selfIp != "—" && selfIp != "0.0.0.0") reachedIps.add(selfIp)
                if (gatewayIp != null) reachedIps.add(gatewayIp)

                if (selfIp.contains(".") && selfIp.count { it == '.' } == 3) {
                    val subnet = selfIp.substringBeforeLast(".")

                    // ──────────────────────────────────────────────────────────
                    // Scan en chunks parallèles de 32 IPs
                    // Pour chaque IP cible, trois vecteurs de découverte :
                    //
                    // 1. UDP Fire & Forget (port 7 echo)
                    //    → force l'OS à résoudre l'ARP, même sans réponse UDP
                    //
                    // 2. TCP multi-ports avec ConnectException catching
                    //    → ConnectException = "Connection refused" = hôte UP
                    //    → SocketTimeoutException = hôte DOWN ou filtré
                    //    C'est la correction de Cause 3 : on ne détecte plus
                    //    uniquement les hôtes qui acceptent la connexion (port
                    //    ouvert), mais aussi ceux qui la refusent (port fermé
                    //    mais hôte joignable).
                    //
                    // 3. Ping ICMP natif
                    //    → le plus fiable, marche sur presque tous les appareils
                    // ──────────────────────────────────────────────────────────
                    (1..254).chunked(32).forEach { chunk ->
                        chunk.map { i ->
                            async {
                                val target = "$subnet.$i"
                                if (target == selfIp || target.startsWith("10.255")) return@async

                                // ── 1. UDP Poke ──────────────────────────────
                                try {
                                    java.net.DatagramSocket().use { ds ->
                                        wifiNetwork?.bindSocket(ds)
                                        ds.send(
                                            java.net.DatagramPacket(
                                                ByteArray(1), 1,
                                                InetAddress.getByName(target), 7
                                            )
                                        )
                                    }
                                } catch (_: Exception) {}

                                // ── 2. TCP multi-ports ───────────────────────
                                // ConnectException = port fermé MAIS hôte joignable → l'ajouter
                                // Toute autre exception = hôte injoignable → ignorer
                                var found = false
                                for (port in probePorts) {
                                    if (found) break
                                    try {
                                        java.net.Socket().use { socket ->
                                            wifiNetwork?.bindSocket(socket)
                                            socket.connect(
                                                java.net.InetSocketAddress(target, port),
                                                120
                                            )
                                            // Connexion acceptée = port ouvert = hôte UP
                                            synchronized(reachedIps) { reachedIps.add(target) }
                                            found = true
                                        }
                                    } catch (e: ConnectException) {
                                        // "Connection refused" = port fermé MAIS hôte UP
                                        // C'est la clé : on détecte l'hôte même sans port ouvert
                                        synchronized(reachedIps) { reachedIps.add(target) }
                                        found = true
                                    } catch (_: Exception) {
                                        // SocketTimeoutException, NetworkUnreachable, etc.
                                        // = hôte vraiment injoignable → continuer vers port suivant
                                    }
                                }

                                // ── 3. Ping ICMP ─────────────────────────────
                                if (!found && pingNative(target)) {
                                    synchronized(reachedIps) { reachedIps.add(target) }
                                }
                            }
                        }.awaitAll()
                        delay(15)
                    }
                }

                // Laisser la table ARP se stabiliser
                delay(800)

                val wifiInterface = wifiScanner.getWifiInterfaceName()
                val arpEntries    = arpReader.readArpTable()

                // Filtrer les entrées ARP sur l'interface WiFi uniquement
                val arpMap = arpEntries
                    .filter { entry ->
                        wifiInterface == null ||
                                entry.device == wifiInterface ||
                                entry.device.contains("wlan") ||
                                entry.device.contains("eth")
                    }
                    .associateBy { it.ip }

                // Union : IPs de l'ARP + IPs qui ont répondu au scan
                val finalIps = (arpMap.keys + reachedIps)
                    .filter { it.contains(".") && it != "0.0.0.0" }
                    .distinct()

                val lanDevices = finalIps.map { ip ->
                    val arpEntry  = arpMap[ip]
                    val isSelf    = (ip == selfIp)
                    val isGateway = (ip == gatewayIp)

                    LanDevice(
                        ip           = ip,
                        mac          = arpEntry?.mac ?: if (isSelf) "Moi" else "Inconnu",
                        manufacturer = null,
                        isGateway    = isGateway,
                        isSelf       = isSelf
                    )
                }.sortedWith(
                    compareByDescending<LanDevice> { it.isSelf }
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

    private suspend fun pingNative(ip: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val process   = Runtime.getRuntime().exec("ping -c 1 -W 1 $ip")
            val completed = process.waitFor(800, TimeUnit.MILLISECONDS)
            completed && process.exitValue() == 0
        } catch (_: Exception) { false }
    }

    private fun getWifiIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.filter { it.isUp && !it.isLoopback }
                ?.filter { !it.name.contains("tun") && !it.name.contains("vpn") }
                ?.flatMap { it.inetAddresses.toList() }
                ?.filterIsInstance<java.net.Inet4Address>()
                ?.filterNot { it.isLoopbackAddress }
                ?.firstOrNull()
                ?.hostAddress
        } catch (_: Exception) { null }
    }

    private fun ipToLong(ip: String): Long {
        return try {
            val parts = ip.split(".")
            if (parts.size != 4) return 0L
            parts.fold(0L) { acc, part -> (acc shl 8) + part.toLong() }
        } catch (_: Exception) { 0L }
    }
}