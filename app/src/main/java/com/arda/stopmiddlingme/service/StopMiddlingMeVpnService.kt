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

            vpnInterface = builder.establish() ?: return

            val inputStream  = FileInputStream(vpnInterface!!.fileDescriptor)
            val outputStream = FileOutputStream(vpnInterface!!.fileDescriptor)
            val buffer = ByteBuffer.allocate(65535)

            while (scope.isActive) {
                buffer.clear()
                val length = inputStream.read(buffer.array())
                if (length <= 0) { yield(); continue }

                val data = buffer.array().copyOf(length)
                val response = forwardAndAnalyze(data, length)
                if (response != null) {
                    outputStream.write(response)
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

    private suspend fun forwardAndAnalyze(data: ByteArray, length: Int): ByteArray? {
        try {
            val version = (data[0].toInt() shr 4) and 0x0F
            if (version != 4) return null

            val protocol = data[9].toInt()
            val ihl = (data[0].toInt() and 0x0F) * 4

            val destIp = InetAddress.getByAddress(data.copyOfRange(16, 20))
            val srcIp = data.copyOfRange(12, 16)
            val srcPort = ((data[ihl].toInt() and 0xFF) shl 8) or (data[ihl + 1].toInt() and 0xFF)
            val destPort = ((data[ihl + 2].toInt() and 0xFF) shl 8) or (data[ihl + 3].toInt() and 0xFF)

            if (protocol == 17) { // UDP
                val dnsPayload = data.copyOfRange(ihl + 8, length)
                val socket = java.net.DatagramSocket()
                protect(socket)
                socket.soTimeout = 3000
                socket.send(java.net.DatagramPacket(dnsPayload, dnsPayload.size, destIp, destPort))

                val responseBuffer = ByteArray(65535)
                val responsePacket = java.net.DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)
                socket.close()

                val responseData = responsePacket.data.copyOf(responsePacket.length)

                if (destPort == 53) {
                    analyzeDnsResponse(responseData, getCurrentSsid())
                }

                return buildUdpResponsePacket(
                    srcIp = destIp.address,
                    dstIp = srcIp,
                    srcPort = destPort,
                    dstPort = srcPort,
                    payload = responseData
                )
            }

            if (protocol == 6) { // TCP port 80
                if (destPort == 80) {
                    val tcpHeaderLen = ((data[ihl + 12].toInt() and 0xFF) shr 4) * 4
                    val tcpPayloadOffset = ihl + tcpHeaderLen
                    if (tcpPayloadOffset < length) {
                        val payload = String(data.copyOfRange(tcpPayloadOffset, length))
                        if (payload.contains("Host: ")) {
                            val host = payload.substringAfter("Host: ").substringBefore("\r\n").trim()
                            sslStripAnalyzer.analyze(host, getCurrentSsid())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // timeout, etc.
        }
        return null
    }

    private fun analyzeDnsResponse(dnsData: ByteArray, ssid: String) {
        try {
            val msg = Message(dnsData)
            val question = msg.getQuestion() ?: return
            val domain = question.name.toString().removeSuffix(".")
            msg.getSectionArray(Section.ANSWER).forEach { record ->
                if (record is ARecord) {
                    val ip = record.address?.hostAddress ?: return@forEach
                    dnsAnalyzer.analyze(ip, domain, ssid, serviceScope)
                }
            }
        } catch (e: Exception) { /* parsing fail silencieux */ }
    }

    private fun buildUdpResponsePacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val totalLength = 28 + payload.size
        val packet = ByteArray(totalLength)

        // IP header
        packet[0]  = 0x45.toByte()           // version=4, ihl=5
        packet[1]  = 0x00
        packet[2]  = (totalLength shr 8).toByte()
        packet[3]  = (totalLength and 0xFF).toByte()
        packet[8]  = 64                       // TTL
        packet[9]  = 17                       // protocole UDP
        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)

        // UDP header
        packet[20] = (srcPort shr 8).toByte()
        packet[21] = (srcPort and 0xFF).toByte()
        packet[22] = (dstPort shr 8).toByte()
        packet[23] = (dstPort and 0xFF).toByte()
        val udpLen = 8 + payload.size
        packet[24] = (udpLen shr 8).toByte()
        packet[25] = (udpLen and 0xFF).toByte()

        // Payload DNS
        System.arraycopy(payload, 0, packet, 28, payload.size)

        // IP checksum
        var checksum = 0
        for (i in 0 until 20 step 2) {
            val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            checksum += word
        }
        while (checksum shr 16 != 0) checksum = (checksum and 0xFFFF) + (checksum shr 16)
        checksum = checksum.inv() and 0xFFFF
        packet[10] = (checksum shr 8).toByte()
        packet[11] = (checksum and 0xFF).toByte()

        return packet
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
