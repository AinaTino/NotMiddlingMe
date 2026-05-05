package com.arda.stopmiddlingme.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.arda.stopmiddlingme.MainActivity
import com.arda.stopmiddlingme.data.datastore.SettingsDataStore
import com.arda.stopmiddlingme.domain.analyzer.SslStripAnalyzer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@AndroidEntryPoint
class StopMiddlingMeVpnService : VpnService() {

    // @Inject lateinit var dnsAnalyzer: DnsAnalyzer  // Désactivé : analyse DNS via VPN TUN causait des timeouts
    @Inject lateinit var sslStripAnalyzer: SslStripAnalyzer
    @Inject lateinit var settingsDataStore: SettingsDataStore
    
    private lateinit var wifiManager: WifiManager

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpnInternal()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, createNotification())
        
        job?.cancel()
        job = serviceScope.launch {
            setupVpn(this)
        }
        
        return START_STICKY
    }

    private fun stopVpnInternal() {
        job?.cancel()
        closeVpn()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun setupVpn(scope: CoroutineScope) {
        try {
            vpnInterface = Builder()
                .setSession("StopMiddlingMe")
                .addAddress("10.255.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(settingsDataStore.dnsServer.first())
                // IMPORTANT : Exclure l'application elle-même du VPN
                // Cela permet au scanner de fonctionner sur le WiFi réel sans interférence
                .addDisallowedApplication(packageName)
                .setBlocking(false)
                .establish() ?: return

            val inputStream  = FileInputStream(vpnInterface!!.fileDescriptor)
            val outputStream = FileOutputStream(vpnInterface!!.fileDescriptor)
            val buffer       = ByteArray(65535)
            val outputMutex  = Mutex()

            while (scope.isActive) {
                val length = inputStream.read(buffer)
                if (length <= 0) { yield(); continue }

                val data    = buffer.copyOf(length)
                val version = (data[0].toInt() shr 4) and 0x0F

                // IPv6 — laisser passer intact
                if (version != 4) {
                    outputMutex.withLock { outputStream.write(data, 0, length) }
                    continue
                }

                val protocol = data[9].toInt()
                val ihl      = (data[0].toInt() and 0x0F) * 4
                if (ihl + 4 > length) continue

                val dstPort = ((data[ihl + 2].toInt() and 0xFF) shl 8) or
                               (data[ihl + 3].toInt() and 0xFF)

                when {
                    // DNS UDP — LAISSER PASSER le paquet original vers le serveur DNS
                    // On analyse la réponse via PacketCapture passive (LinkProperties)
                    // Capturer et modifier le DNS cause des timeouts
                    protocol == 17 && dstPort == 53 -> {
                        // Laisser passer intact — le serveur DNS répondra hors du tunnel
                        outputMutex.withLock { outputStream.write(data, 0, length) }
                        // Optionnel : logger pour debug
                        // analyzeOutboundDnsQuery(data, length, ihl)
                    }

                    // HTTP TCP port 80 — analyser SSL Strip, laisser passer intact
                    protocol == 6 && dstPort == 80 -> {
                        inspectHttp(data, length, ihl)
                        outputMutex.withLock { outputStream.write(data, 0, length) }
                    }

                    // Tout le reste (HTTPS, TCP quelconque) — laisser passer sans toucher
                    else -> {
                        outputMutex.withLock { outputStream.write(data, 0, length) }
                    }
                }

                yield()
            }
        } catch (e: CancellationException) {
            // arrêt normal
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            closeVpn()
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // NOTE: DNS analysis anciennement via VPNService TUN a été DÉSACTIVÉ car
    // il causait des timeouts sur les requêtes des apps (la requête était mangée).
    // La détection DNS spoofing se fait maintenant via :
    // 1. Analyse passive des changements LinkProperties (DNS serveurs)
    // 2. Analyse passivedu trafic outbound (SNI, Host headers)
    // ──────────────────────────────────────────────────────────────────────────────

    private fun inspectHttp(data: ByteArray, length: Int, ihl: Int) {
        try {
            val tcpHeaderLen  = ((data[ihl + 12].toInt() and 0xFF) shr 4) * 4
            val payloadOffset = ihl + tcpHeaderLen
            if (payloadOffset >= length) return

            val payload = String(data.copyOfRange(payloadOffset, length), Charsets.ISO_8859_1)
            if (!payload.startsWith("GET") && !payload.startsWith("POST")) return

            val host = payload.lines()
                .firstOrNull { it.startsWith("Host:") }
                ?.substringAfter("Host:")
                ?.trim() ?: return

            sslStripAnalyzer.analyze(host, getCurrentSsid())
        } catch (e: Exception) { /* silent */ }
    }


    private fun getCurrentSsid(): String {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        return if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            val info = wifiManager.connectionInfo
            info.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" } ?: "—"
        } else {
            "—" // Réseau mobile ou autre : on retourne "—" pour ignorer l'analyse locale
        }
    }

    private fun createNotification(): android.app.Notification {
        val channelId = "vpn_service"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, "Protection VPN", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("StopMiddlingMe - Protection Active")
            .setContentText("Analyse du trafic en cours...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun closeVpn() {
        vpnInterface?.close()
        vpnInterface = null
    }

    override fun onDestroy() {
        isRunning = false
        job?.cancel()
        serviceScope.cancel()
        closeVpn()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 2002
        const val ACTION_STOP = "com.arda.stopmiddlingme.STOP_VPN"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
