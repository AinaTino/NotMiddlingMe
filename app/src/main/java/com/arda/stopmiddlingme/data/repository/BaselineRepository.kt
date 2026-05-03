package com.arda.stopmiddlingme.data.repository

import com.arda.stopmiddlingme.data.db.dao.BaselineDao
import com.arda.stopmiddlingme.data.db.entity.NetworkBaseline
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaselineRepository @Inject constructor(
    private val dao: BaselineDao
) {

    fun observe(ssid: String): Flow<NetworkBaseline?> = dao.observe(ssid)

    fun observeAll(): Flow<List<NetworkBaseline>> = dao.observeAll()

    suspend fun get(ssid: String): NetworkBaseline? = dao.get(ssid)

    suspend fun save(baseline: NetworkBaseline) {
        val existing = dao.get(baseline.ssid)
        if (existing == null) dao.insert(baseline) else dao.update(baseline)
    }

    suspend fun setTrusted(ssid: String, trusted: Boolean) {
        dao.setTrusted(ssid, trusted)
    }

    suspend fun delete(ssid: String) {
        dao.delete(ssid)
    }

    // Crée une baseline depuis les infos réseau actuelles si elle n'existe pas encore
    suspend fun createIfAbsent(
        ssid: String,
        bssid: String,
        gatewayIp: String,
        gatewayMac: String,
        dnsServers: List<String>
    ) {
        val existing = dao.get(ssid)
        if (existing == null) {
            dao.insert(
                NetworkBaseline(
                    ssid = ssid,
                    bssid = bssid,
                    gatewayIp = gatewayIp,
                    gatewayMac = gatewayMac,
                    dnsServers = dnsServers.joinToString(","),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    // Helper pour parser les DNS depuis le format stocké
    fun parseDnsServers(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
