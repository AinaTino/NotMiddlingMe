package com.arda.stopmiddlingme.ui.screen.dashboard

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arda.stopmiddlingme.R
import com.arda.stopmiddlingme.domain.model.AlertLevel
import com.arda.stopmiddlingme.ui.component.ScoreGauge
import com.arda.stopmiddlingme.ui.theme.StopMiddlingMeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val currentSession by viewModel.currentSession.collectAsState()
    val activeSignals by viewModel.activeSignals.collectAsState()
    val isVpnRunning by viewModel.isVpnRunning.collectAsState()

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.startVpn()
        }
    }

    // Mock update SSID pour le test
    LaunchedEffect(Unit) {
        viewModel.refreshServiceStatus()
    }

    DashboardContent(
        currentSession = currentSession,
        activeSignals = activeSignals,
        isVpnRunning = isVpnRunning,
        onToggleVpn = {
            if (isVpnRunning) {
                viewModel.stopVpn()
            } else {
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent != null) {
                    vpnLauncher.launch(vpnIntent)
                } else {
                    viewModel.startVpn()
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    currentSession: com.arda.stopmiddlingme.data.db.entity.AlertSession?,
    activeSignals: List<com.arda.stopmiddlingme.data.db.entity.SignalInstance>,
    isVpnRunning: Boolean,
    onToggleVpn: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ScoreGauge(
                score = currentSession?.totalScore ?: 0,
                level = currentSession?.finalLevel ?: AlertLevel.SAFE
            )

            Spacer(modifier = Modifier.height(16.dp))

            // État de la protection
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isVpnRunning) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                modifier = Modifier.padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isVpnRunning) Icons.Default.Shield else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isVpnRunning) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isVpnRunning) stringResource(R.string.protection_active) else stringResource(R.string.protection_disabled),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isVpnRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onToggleVpn,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = if (isVpnRunning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary) 
                         else ButtonDefaults.buttonColors()
            ) {
                Text(if (isVpnRunning) stringResource(R.string.stop_monitoring) else stringResource(R.string.start_monitoring))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.real_time_signals), style = MaterialTheme.typography.titleMedium)
                if (activeSignals.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text(activeSignals.size.toString())
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            if (activeSignals.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_anomalies),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activeSignals) { signal ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = signal.type.description,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = signal.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    StopMiddlingMeTheme {
        DashboardContent(
            currentSession = null,
            activeSignals = emptyList(),
            isVpnRunning = false,
            onToggleVpn = {}
        )
    }
}
