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
import com.arda.stopmiddlingme.domain.analyzer.CertAnalyzer
import com.arda.stopmiddlingme.domain.analyzer.DnsAnalyzer
import com.arda.stopmiddlingme.domain.analyzer.SslStripAnalyzer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.xbill.DNS.ARecord
import org.xbill.DNS.Message
import org.xbill.DNS.Section
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import javax.inject.Inject

@AndroidEntryPoint
class StopMiddlingMeVpnService : VpnService() {

    @Inject lateinit var dnsAnalyzer: DnsAnalyzer
    @Inject lateinit var sslStripAnalyzer: SslStripAnalyzer
    @Inject lateinit var certAnalyzer: CertAnalyzer
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
            val userDns = settingsDataStore.dnsServer.first()
            val builder = Builder()
                .setSession("StopMiddlingMe")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0) 
                .addDnsServer(userDns)
                .setBlocking(false) 

            vpnInterface = builder.establish()
            if (vpnInterface == null) return

            val inputStream = FileInputStream(vpnInterface?.fileDescriptor)
            val outputStream = FileOutputStream(vpnInterface?.fileDescriptor)
            val buffer = ByteBuffer.allocate(Short.MAX_VALUE.toInt())

            while (scope.isActive) {
                val length = try { inputStream.read(buffer.array()) } catch (e: Exception) { -1 }
                if (length > 0) {
                    inspectPacket(buffer, length)
                    // Forward the packet
                    outputStream.write(buffer.array(), 0, length)
                } else if (length == -1) {
                    break
                }
                buffer.clear()
                yield()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            closeVpn()
        }
    }

    private fun inspectPacket(buffer: ByteBuffer, length: Int) {
        val data = buffer.array()
        
        val version = (data[0].toInt() shr 4) and 0x0F
        if (version != 4) return 

        val protocol = data[9].toInt()
        val ihl = (data[0].toInt() and 0x0F) * 4
        val payloadOffset = ihl + 8 // Offset standard pour UDP/TCP header (simplifié)
        
        if (protocol == 17) { // UDP
            val dstPort = ((data[ihl + 2].toInt() and 0xFF) shl 8) or (data[ihl + 3].toInt() and 0xFF)
            
            if (dstPort == 53) {
                try {
                    val dnsData = data.copyOfRange(ihl + 8, length)
                    val dnsMessage = Message(dnsData)
                    val question = dnsMessage.getQuestion()
                    val domain = question?.name?.toString()?.removeSuffix(".") ?: return
                    
                    // Chercher les réponses (si c'est un paquet entrant)
                    val answers = dnsMessage.getSectionArray(Section.ANSWER)
                    for (record in answers) {
                        if (record is ARecord) {
                            val resolvedIp = record.address?.hostAddress ?: continue
                            dnsAnalyzer.analyze(resolvedIp, domain, getCurrentSsid(), serviceScope)
                        }
                    }
                } catch (e: Exception) {
                    // Erreur de parsing DNS
                }
            }
        } else if (protocol == 6) { // TCP
            val dstPort = ((data[ihl + 2].toInt() and 0xFF) shl 8) or (data[ihl + 3].toInt() and 0xFF)
            
            if (dstPort == 80) {
                // SSL Stripping : On cherche "Host: " dans le payload HTTP
                val payload = String(data.copyOfRange(payloadOffset, length))
                if (payload.contains("Host: ")) {
                    val host = payload.substringAfter("Host: ").substringBefore("\r\n").trim()
                    sslStripAnalyzer.analyze(host, getCurrentSsid())
                }
            }
        }
    }

    private fun getCurrentSsid(): String {
        val info = wifiManager.connectionInfo
        var ssid = info.ssid?.removeSurrounding("\"")
        if (ssid == null || ssid == "<unknown ssid>") ssid = "Unknown_WiFi"
        return ssid
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
