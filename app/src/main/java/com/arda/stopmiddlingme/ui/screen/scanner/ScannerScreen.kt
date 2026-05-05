package com.arda.stopmiddlingme.ui.screen.scanner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.tooling.preview.Preview
import com.arda.stopmiddlingme.R
import com.arda.stopmiddlingme.ui.theme.StopMiddlingMeTheme
import com.arda.stopmiddlingme.domain.model.LanDevice

import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val context = LocalContext.current
    
    val hasLocationPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    ScannerContent(
        devices = devices,
        isScanning = isScanning,
        hasPermission = hasLocationPermission.value,
        onScanClick = { viewModel.scanNetwork() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerContent(
    devices: List<LanDevice>,
    isScanning: Boolean,
    hasPermission: Boolean,
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
                title = { Text(stringResource(R.string.scanner_title)) },
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
                targetState = when {
                    !hasPermission -> "no_permission"
                    isScanning -> "scanning"
                    devices.isEmpty() -> "empty"
                    else -> "list"
                },
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "scanner_state"
            ) { state ->
                when (state) {
                    "scanning" -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    stringResource(R.string.scanning_network),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    stringResource(R.string.identifying_devices),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                    "no_permission" -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocationOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Localisation requise",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Android nécessite la permission de localisation pour scanner les réseaux WiFi et identifier les appareils.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                    "empty" -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.CellTower,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(stringResource(R.string.no_devices), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.ensure_wifi),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Button(
                                    onClick = onScanClick,
                                    modifier = Modifier.padding(top = 24.dp),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text(stringResource(R.string.rescan))
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Text(
                                    text = stringResource(R.string.devices_found, devices.size),
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
            hasPermission = true,
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
                color = when {
                    device.isSelf -> MaterialTheme.colorScheme.primary
                    device.isGateway -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            device.isSelf -> Icons.Default.Devices
                            device.isGateway -> Icons.Default.Router
                            else -> Icons.Default.CellTower
                        },
                        contentDescription = null,
                        tint = if (device.isSelf || device.isGateway) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = device.ip, style = MaterialTheme.typography.titleMedium)
                    if (device.isSelf) {
                        Text(
                            text = " (${stringResource(R.string.you)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    } else if (device.isGateway) {
                        Text(
                            text = " (Gateway)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                Text(text = device.mac, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
