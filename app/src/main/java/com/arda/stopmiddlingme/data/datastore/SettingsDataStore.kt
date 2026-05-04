package com.arda.stopmiddlingme.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val DNS_MONITORING = booleanPreferencesKey("dns_monitoring")
    private val SSL_STRIP_DETECTION = booleanPreferencesKey("ssl_strip_detection")
    private val REAL_TIME_NOTIFICATIONS = booleanPreferencesKey("real_time_notifications")
    private val DNS_SERVER = stringPreferencesKey("dns_server")

    val dnsMonitoring: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[DNS_MONITORING] ?: true }

    val sslStripDetection: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[SSL_STRIP_DETECTION] ?: true }

    val realTimeNotifications: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[REAL_TIME_NOTIFICATIONS] ?: true }

    val dnsServer: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[DNS_SERVER] ?: "1.1.1.1" }

    suspend fun setDnsMonitoring(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DNS_MONITORING] = enabled
        }
    }

    suspend fun setSslStripDetection(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SSL_STRIP_DETECTION] = enabled
        }
    }

    suspend fun setRealTimeNotifications(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[REAL_TIME_NOTIFICATIONS] = enabled
        }
    }

    suspend fun setDnsServer(server: String) {
        context.dataStore.edit { preferences ->
            preferences[DNS_SERVER] = server
        }
    }
}
