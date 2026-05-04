package com.arda.stopmiddlingme.ui.screen.dashboard

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arda.stopmiddlingme.data.db.entity.AlertSession
import com.arda.stopmiddlingme.data.db.entity.SignalInstance
import com.arda.stopmiddlingme.data.repository.BaselineRepository
import com.arda.stopmiddlingme.data.repository.SessionRepository
import com.arda.stopmiddlingme.data.source.WifiScanner
import com.arda.stopmiddlingme.service.MonitoringService
import com.arda.stopmiddlingme.service.StopMiddlingMeVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepo: SessionRepository,
    private val baselineRepo: BaselineRepository,
    private val wifiScanner: WifiScanner,      // ← ajoute cette injection
    // ...
) : ViewModel() {

    private val _currentSsid = MutableStateFlow<String?>(null)
    val currentSsid = _currentSsid.asStateFlow()

    init {
        // Charge le vrai SSID au démarrage
        updateSsid(wifiScanner.getCurrentSsid())
    }

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

    private val _isVpnRunning = MutableStateFlow(StopMiddlingMeVpnService.isRunning)
    val isVpnRunning = _isVpnRunning.asStateFlow()

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
