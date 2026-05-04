package com.arda.stopmiddlingme.ui.screen.dashboard

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arda.stopmiddlingme.data.db.entity.AlertSession
import com.arda.stopmiddlingme.data.db.entity.SignalInstance
import com.arda.stopmiddlingme.data.repository.BaselineRepository
import com.arda.stopmiddlingme.data.repository.SessionRepository
import com.arda.stopmiddlingme.data.source.WifiScanner
import com.arda.stopmiddlingme.domain.model.NetworkInfo
import com.arda.stopmiddlingme.service.MonitoringService
import com.arda.stopmiddlingme.service.StopMiddlingMeVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepo: SessionRepository,
    private val baselineRepo: BaselineRepository,
    private val wifiScanner: WifiScanner
) : ViewModel() {

    // 1. TOUTES les propriétés MutableStateFlow en premier
    private val _currentSsid = MutableStateFlow<String?>(null)
    private val _isVpnRunning = MutableStateFlow(StopMiddlingMeVpnService.isRunning)

    // 2. Les versions publiques (ReadOnly)
    val currentSsid = _currentSsid.asStateFlow()
    val isVpnRunning = _isVpnRunning.asStateFlow()

    // 3. Les StateFlow dérivés (Lazy)
    val networkInfo: StateFlow<NetworkInfo?> = flow {
        while(true) {
            emit(NetworkInfo(
                ssid       = wifiScanner.getCurrentSsid() ?: "—",
                bssid      = wifiScanner.getCurrentBssid() ?: "—",
                gatewayIp  = "—",
                gatewayMac = "—",
                dnsServers = emptyList(),
                isConnected = wifiScanner.getCurrentSsid() != null
            ))
            delay(10000) // Rafraîchir toutes les 10s
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentSession: StateFlow<AlertSession?> = _currentSsid
        .flatMapLatest { ssid ->
            if (ssid != null) sessionRepo.observeOpenSession(ssid)
            else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeSignals: StateFlow<List<SignalInstance>> = currentSession
        .flatMapLatest { session ->
            if (session != null) sessionRepo.observeSignals(session.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 4. Bloc init en dernier
    init {
        // On initialise la valeur de départ EN ARRIÈRE-PLAN
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ssid = wifiScanner.getCurrentSsid()
            _currentSsid.value = ssid
        }

        // Polling de l'état du VPN
        viewModelScope.launch {
            while (true) {
                _isVpnRunning.value = StopMiddlingMeVpnService.isRunning
                delay(5000)
            }
        }
    }

    fun updateSsid(ssid: String?) {
        _currentSsid.value = ssid
    }

    fun startVpn() {
        val intent = Intent(context, StopMiddlingMeVpnService::class.java)
        context.startForegroundService(intent)
        _isVpnRunning.value = true
    }

    fun stopVpn() {
        val intent = Intent(context, StopMiddlingMeVpnService::class.java).apply {
            action = StopMiddlingMeVpnService.ACTION_STOP
        }
        context.startService(intent)
        _isVpnRunning.value = false
    }

    fun refreshServiceStatus() {
        _isVpnRunning.value = StopMiddlingMeVpnService.isRunning
    }

    fun resolveAlert(sessionId: String) {
        viewModelScope.launch {
            sessionRepo.resolveSession(sessionId)
        }
    }

    fun isMonitoringRunning(): Boolean = MonitoringService.isRunning
}
