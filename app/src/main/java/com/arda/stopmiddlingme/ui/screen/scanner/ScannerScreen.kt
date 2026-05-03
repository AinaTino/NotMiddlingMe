package com.arda.stopmiddlingme.ui.screen.scanner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.tooling.preview.Preview
import com.arda.stopmiddlingme.ui.theme.StopMiddlingMeTheme
import com.arda.stopmiddlingme.domain.model.LanDevice

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    ScannerContent(
        devices = devices,
        isScanning = isScanning,
        onScanClick = { viewModel.scanNetwork() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerContent(
    devices: List<LanDevice>,
    isScanning: Boolean,
    onScanClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scanner LAN") },
                actions = {
                    IconButton(onClick = onScanClick, enabled = !isScanning) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Scan",
                            modifier = if (isScanning) Modifier.rotate(rotation) else Modifier
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = isScanning,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "scanner_state"
            ) { targetScanning ->
                if (targetScanning) {
                    // État de chargement au centre
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Analyse du réseau...",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Identification des appareils connectés",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else if (devices.isEmpty()) {
                    // État vide
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CellTower,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Aucun appareil détecté", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Assurez-vous d'être connecté au WiFi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Button(
                                onClick = onScanClick,
                                modifier = Modifier.padding(top = 24.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text("Relancer le scan")
                            }
                        }
                    }
                } else {
                    // Liste des appareils
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                "Appareils trouvés (${devices.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(devices) { device ->
                            DeviceItem(device)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ScannerPreview() {
    StopMiddlingMeTheme {
        ScannerContent(
            devices = listOf(
                LanDevice("192.168.1.1", "00:11:22:33:44:55", null, isGateway = true, isSelf = false),
                LanDevice("192.168.1.15", "AA:BB:CC:DD:EE:FF", "Google LLC", isGateway = false, isSelf = true)
            ),
            isScanning = false,
            onScanClick = {}
        )
    }
}

@Composable
fun DeviceItem(device: LanDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (device.isSelf) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                 else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (device.isSelf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (device.isSelf) Icons.Default.Devices else Icons.Default.CellTower,
                        contentDescription = null,
                        tint = if (device.isSelf) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = device.ip, style = MaterialTheme.typography.titleMedium)
                    if (device.isSelf) {
                        Text(
                            " (Vous)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                Text(text = device.mac, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
