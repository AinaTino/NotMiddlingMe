package com.arda.stopmiddlingme.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_baseline")
data class NetworkBaseline(
    @PrimaryKey
    val ssid: String,
    val bssid: String,
    val gatewayIp: String,
    val gatewayMac: String,
    // stocké comme JSON string via TypeConverter
    val dnsServers: String,
    val createdAt: Long,
    val isTrusted: Boolean = false
)
