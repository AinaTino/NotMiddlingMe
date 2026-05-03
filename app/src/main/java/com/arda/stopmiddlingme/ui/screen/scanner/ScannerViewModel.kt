package com.arda.stopmiddlingme.ui.screen.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arda.stopmiddlingme.data.source.ArpReader
import com.arda.stopmiddlingme.domain.model.ArpEntry
import com.arda.stopmiddlingme.domain.model.LanDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.NetworkInterface
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val arpReader: ArpReader
) : ViewModel() {

    private val _devices = MutableStateFlow<List<LanDevice>>(emptyList())
    val devices: StateFlow<List<LanDevice>> = _devices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    fun scanNetwork() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            
            // Simule un scan réseau pour l'UX et laisse le temps à l'OS 
            // de mettre à jour la table ARP si des paquets circulent
            kotlinx.coroutines.delay(1500)
            
            val arpEntries = arpReader.readArpTable()
            val selfIp = getLocalIpAddress()
            
            val lanDevices = arpEntries.map { entry ->
                LanDevice(
                    ip = entry.ip,
                    mac = entry.mac,
                    manufacturer = null, // Could be resolved via OUI lookup later
                    isGateway = false, // Could be identified by comparing with current gateway
                    isSelf = entry.ip == selfIp
                )
            }
            
            _devices.value = lanDevices
            _isScanning.value = false
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
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
