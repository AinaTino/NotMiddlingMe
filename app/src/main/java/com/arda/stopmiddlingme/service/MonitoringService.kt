package com.arda.stopmiddlingme.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arda.stopmiddlingme.data.repository.BaselineRepository
import com.arda.stopmiddlingme.data.source.ArpReader
import com.arda.stopmiddlingme.data.source.WifiScanner
import com.arda.stopmiddlingme.domain.analyzer.ArpAnalyzer
import com.arda.stopmiddlingme.domain.analyzer.GatewayMonitor
import com.arda.stopmiddlingme.domain.analyzer.RogueApAnalyzer
import com.arda.stopmiddlingme.domain.model.ArpEntry
import com.arda.stopmiddlingme.domain.model.WifiAp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MonitoringService : Service() {

    @Inject lateinit var arpReader: ArpReader
    @Inject lateinit var wifiScanner: WifiScanner
    @Inject lateinit var arpAnalyzer: ArpAnalyzer
    @Inject lateinit var rogueApAnalyzer: RogueApAnalyzer
    @Inject lateinit var gatewayMonitor: GatewayMonitor
    @Inject lateinit var baselineRepo: BaselineRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: WifiManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    private var isConnectedToWifi = false
    private var currentSsid: String? = null

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, createPersistentNotification())
        startArpPolling()
        startWifiScanning()
        registerNetworkCallback()
        return START_STICKY
    }

    private fun startArpPolling() {
        scope.launch {
            while (isActive) {
                val ssid = currentSsid
                if (ssid != null) {
                    val table = arpReader.readArpTable()
                    
                    // S'assurer qu'une baseline existe (simplifié)
                    ensureBaseline(ssid, table)
                    
                    arpAnalyzer.analyze(table, ssid)
                }
                delay(10_000L)
            }
        }
    }

    private fun startWifiScanning() {
        scope.launch {
            while (isActive) {
                val ssid = currentSsid
                if (ssid != null) {
                    val networks = wifiScanner.getScanResults()
                    rogueApAnalyzer.analyze(networks, ssid)
                }
                delay(30_000L)
            }
        }
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isConnectedToWifi = true
                updateCurrentWifiInfo()
            }

            override fun onLost(network: Network) {
                isConnectedToWifi = false
                currentSsid = null
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                val ssid = currentSsid ?: return
                scope.launch {
                    // Si onAvailable n'a pas été appelé juste avant, c'est "non sollicité"
                    // Dans cette version simplifiée, on passe true si on veut tester le signal
                    gatewayMonitor.onNetworkChanged(linkProperties, ssid, isUnsolicited = false)
                }
            }
        }
        connectivityManager.registerNetworkCallback(request, networkCallback!!)
    }

    @SuppressLint("MissingPermission")
    private fun updateCurrentWifiInfo() {
        val info = wifiManager.connectionInfo
        currentSsid = info.ssid?.removeSurrounding("\"")
        if (currentSsid == "<unknown ssid>") currentSsid = null
    }

    @SuppressLint("MissingPermission")
    private fun scanWifiNetworks(): List<WifiAp> {
        return wifiManager.scanResults.map {
            WifiAp(
                ssid = it.SSID,
                bssid = it.BSSID,
                security = it.capabilities,
                signalLevel = it.level
            )
        }
    }

    private suspend fun ensureBaseline(ssid: String, arpTable: List<ArpEntry>) {
        val existing = baselineRepo.get(ssid)
        if (existing == null) {
            val gatewayIp = getGatewayIp() ?: return
            val gatewayMac = arpTable.find { it.ip == gatewayIp }?.mac ?: return
            
            baselineRepo.createIfAbsent(
                ssid = ssid,
                bssid = wifiManager.connectionInfo.bssid ?: "",
                gatewayIp = gatewayIp,
                gatewayMac = gatewayMac,
                dnsServers = connectivityManager.getLinkProperties(connectivityManager.activeNetwork)?.dnsServers?.map { it.hostAddress ?: "" } ?: emptyList()
            )
        }
    }

    private fun getGatewayIp(): String? {
        val lp = connectivityManager.getLinkProperties(connectivityManager.activeNetwork)
        return lp?.routes?.find { it.isDefaultRoute }?.gateway?.hostAddress
    }

    private fun createPersistentNotification(): Notification {
        val channelId = "monitoring_service"
        val channel = NotificationChannel(
            channelId,
            "Protection en arrière-plan",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("NotMiddlingMe")
            .setContentText("Surveillance du réseau en cours...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 1001
    }
}
