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
import android.content.pm.ServiceInfo
import android.os.Build

@AndroidEntryPoint
class MonitoringService : Service() {

    @Inject lateinit var arpReader: ArpReader
    @Inject lateinit var wifiScanner: WifiScanner
    @Inject lateinit var arpAnalyzer: ArpAnalyzer
    @Inject lateinit var rogueApAnalyzer: RogueApAnalyzer
    @Inject lateinit var gatewayMonitor: GatewayMonitor
    @Inject lateinit var baselineRepo: BaselineRepository

    @Volatile
    private var justConnected = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: WifiManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    private var isConnectedToWifi = false
    private var currentSsid: String? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createPersistentNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        
        // Initialisation immédiate des infos WiFi
        updateCurrentWifiInfo()
        
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
                    val gatewayIp = getGatewayIp()
                    
                    // S'assurer qu'une baseline existe
                    ensureBaseline(ssid, table)
                    
                    // Analyse ARP traditionnelle (limitée sur Android 10+)
                    arpAnalyzer.analyze(table, ssid)

                    // Analyse de latence vers la gateway (efficace sur Android 10+)
                    if (gatewayIp != null) {
                        gatewayMonitor.checkLatency(gatewayIp, ssid)
                    }
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
                justConnected = true
                updateCurrentWifiInfo()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                val ssid = currentSsid ?: return
                val unsolicited = !justConnected
                justConnected = false
                scope.launch {
                    gatewayMonitor.onNetworkChanged(linkProperties, ssid, isUnsolicited = unsolicited)
                }
            }

            override fun onLost(network: Network) {
                isConnectedToWifi = false
                currentSsid = null
            }

        }
        connectivityManager.registerNetworkCallback(request, networkCallback!!)
    }

    private fun updateCurrentWifiInfo() {
        currentSsid = wifiScanner.getCurrentSsid()
    }

    private suspend fun ensureBaseline(ssid: String, arpTable: List<ArpEntry>) {
        val existing = baselineRepo.get(ssid)
        if (existing == null) {
            val gatewayIp = getGatewayIp() ?: return
            
            // Envoi d'un paquet UDP vers le port 7 du routeur pour peupler la table ARP
            try {
                val address = java.net.InetAddress.getByName(gatewayIp)
                java.net.Socket().use { it.connect(java.net.InetSocketAddress(address, 7), 500) }
            } catch (e: Exception) {}

            // Sur Android 10+, on n'aura souvent PAS la MAC ici. On met un placeholder si besoin.
            val gatewayMac = arpReader.readArpTable().find { it.ip == gatewayIp }?.mac ?: "02:00:00:00:00:00"
            val fullInfo = wifiScanner.getFullNetworkInfo()
            
            // Fallback SSID plus robuste
            val finalSsid = if (ssid == "Inconnu" || ssid == "WiFi" || ssid == "<unknown ssid>") {
                val suffix = if (gatewayMac != "02:00:00:00:00:00") {
                    gatewayMac.takeLast(5).replace(":", "")
                } else {
                    fullInfo.bssid.takeLast(4).replace(":", "")
                }
                "WiFi_$suffix"
            } else ssid

            baselineRepo.createIfAbsent(
                ssid = finalSsid,
                bssid = fullInfo.bssid,
                gatewayIp = gatewayIp,
                gatewayMac = gatewayMac,
                dnsServers = connectivityManager.getLinkProperties(wifiScanner.getWifiNetwork())?.dnsServers?.map { it.hostAddress ?: "" } ?: emptyList(),
                security = fullInfo.security
            )
        }
    }

    private fun getGatewayIp(): String? {
        val wifiNetwork = wifiScanner.getWifiNetwork() ?: connectivityManager.activeNetwork
        val lp = connectivityManager.getLinkProperties(wifiNetwork)
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
            .setContentTitle("StopMiddlingMe")
            .setContentText("Surveillance du réseau en cours...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 1001

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
