package com.arda.stopmiddlingme.ui.screen.dashboard

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arda.stopmiddlingme.R
import com.arda.stopmiddlingme.data.db.entity.AlertSession
import com.arda.stopmiddlingme.data.db.entity.SignalInstance
import com.arda.stopmiddlingme.domain.model.AlertLevel
import com.arda.stopmiddlingme.domain.model.NetworkInfo
import com.arda.stopmiddlingme.ui.component.ScoreGauge
import com.arda.stopmiddlingme.ui.theme.ColorSafe
import com.arda.stopmiddlingme.ui.theme.StopMiddlingMeTheme
import com.arda.stopmiddlingme.util.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val currentSession by viewModel.currentSession.collectAsState()
    val activeSignals by viewModel.activeSignals.collectAsState()
    val isVpnRunning by viewModel.isVpnRunning.collectAsState()
    val networkInfo by viewModel.networkInfo.collectAsState()

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.startVpn()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshServiceStatus()
    }

    DashboardContent(
        currentSession = currentSession,
        activeSignals = activeSignals,
        isVpnRunning = isVpnRunning,
        networkInfo = networkInfo,
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
        },
        onResolveAlert = { sessionId ->
            viewModel.resolveAlert(sessionId)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    currentSession: AlertSession?,
    activeSignals: List<SignalInstance>,
    isVpnRunning: Boolean,
    networkInfo: NetworkInfo?,
    onToggleVpn: () -> Unit,
    onResolveAlert: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "STOP ",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "MIDDLING",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = " ME",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 1.sp
                        )
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        Image(
                            painter = painterResource(id = R.mipmap.logo_foreground),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(1.1f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ScoreGauge(
                    score = currentSession?.totalScore ?: 0,
                    level = currentSession?.finalLevel ?: AlertLevel.SAFE,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            // État de la protection
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isVpnRunning) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = 8.dp)
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
            }

            item {
                Button(
                    onClick = onToggleVpn,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = if (isVpnRunning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary) 
                                 else ButtonDefaults.buttonColors()
                ) {
                    Text(if (isVpnRunning) stringResource(R.string.stop_monitoring) else stringResource(R.string.start_monitoring))
                }
            }

            // Carte infos réseau
            item {
                networkInfo?.let { info ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            NetworkInfoRow("📶 Réseau",  info.ssid)
                            NetworkInfoRow("🔑 BSSID",  info.bssid)
                            NetworkInfoRow("🌐 Gateway", info.gatewayIp)
                            info.dnsServers.firstOrNull()?.let {
                                NetworkInfoRow("🔎 DNS", it)
                            }
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.real_time_signals), style = MaterialTheme.typography.titleMedium)
                    if (activeSignals.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                            Text(activeSignals.size.toString())
                        }
                    }
                }
            }

            if (activeSignals.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.no_anomalies),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
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
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = DateTimeUtils.formatTime(signal.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // Bouton Résoudre
            item {
                if (currentSession != null && currentSession.finalLevel != AlertLevel.SAFE) {
                    OutlinedButton(
                        onClick = { onResolveAlert(currentSession.id) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorSafe)
                    ) {
                        Text("✅ Marquer comme résolu")
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// Composable helper
@Composable
fun NetworkInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
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
            networkInfo = NetworkInfo(
                ssid = "Mon Wifi",
                bssid = "00:11:22:33:44:55",
                localIp = "192.168.1.1",
                gatewayIp = "192.168.1.1",
                gatewayMac = "00:11:22:33:44:55",
                dnsServers = emptyList(),
                isConnected = true
            ),
            onToggleVpn = {},
            onResolveAlert = {}
        )
    }
}
