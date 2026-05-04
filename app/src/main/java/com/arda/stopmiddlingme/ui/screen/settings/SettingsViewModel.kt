package com.arda.stopmiddlingme.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arda.stopmiddlingme.data.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val dnsMonitoring: StateFlow<Boolean> = settingsDataStore.dnsMonitoring
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val sslStripDetection: StateFlow<Boolean> = settingsDataStore.sslStripDetection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val realTimeNotifications: StateFlow<Boolean> = settingsDataStore.realTimeNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setDnsMonitoring(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDnsMonitoring(enabled) }
    }

    fun setSslStripDetection(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setSslStripDetection(enabled) }
    }

    fun setRealTimeNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setRealTimeNotifications(enabled) }
    }
}
