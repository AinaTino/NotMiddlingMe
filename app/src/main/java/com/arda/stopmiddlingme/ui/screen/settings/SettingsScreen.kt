package com.arda.stopmiddlingme.ui.screen.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.arda.stopmiddlingme.R
import com.arda.stopmiddlingme.data.db.entity.NetworkBaseline

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val dnsEnabled by viewModel.dnsMonitoring.collectAsState()
    val sslStripEnabled by viewModel.sslStripDetection.collectAsState()
    val notificationEnabled by viewModel.realTimeNotifications.collectAsState()
    val dnsServer by viewModel.dnsServer.collectAsState()
    val baselines by viewModel.baselines.collectAsState()

    SettingsContent(
        dnsEnabled = dnsEnabled,
        onDnsToggle = viewModel::setDnsMonitoring,
        sslStripEnabled = sslStripEnabled,
        onSslStripToggle = viewModel::setSslStripDetection,
        notificationEnabled = notificationEnabled,
        onNotificationToggle = viewModel::setRealTimeNotifications,
        dnsServer = dnsServer,
        onDnsServerChange = viewModel::setDnsServer,
        baselines = baselines,
        onToggleTrust = viewModel::toggleNetworkTrust,
        onDeleteNetwork = viewModel::deleteNetwork
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    dnsEnabled: Boolean,
    onDnsToggle: (Boolean) -> Unit,
    sslStripEnabled: Boolean,
    onSslStripToggle: (Boolean) -> Unit,
    notificationEnabled: Boolean,
    onNotificationToggle: (Boolean) -> Unit,
    dnsServer: String,
    onDnsServerChange: (String) -> Unit,
    baselines: List<NetworkBaseline>,
    onToggleTrust: (String, Boolean) -> Unit,
    onDeleteNetwork: (String) -> Unit
) {
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }
    val locales = AppCompatDelegate.getApplicationLocales()
    val currentLocale = if (locales.isEmpty) "system" else locales.get(0)?.language ?: "en"

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_settings)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.general_config), style = MaterialTheme.typography.titleMedium)
            
            ToggleSetting(
                stringResource(R.string.dns_monitoring),
                stringResource(R.string.dns_monitoring_desc),
                dnsEnabled,
                onDnsToggle
            )
            
            ToggleSetting(
                stringResource(R.string.ssl_strip_detection),
                stringResource(R.string.ssl_strip_desc),
                sslStripEnabled,
                onSslStripToggle
            )
            
            ToggleSetting(
                stringResource(R.string.real_time_notifications),
                stringResource(R.string.notifications_desc),
                notificationEnabled,
                onNotificationToggle
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.dns_server_title)) },
                supportingContent = { Text(dnsServer) },
                leadingContent = { Icon(Icons.Default.Dns, contentDescription = null) },
                modifier = Modifier.clickable { showDnsDialog = true }
            )
            
            HorizontalDivider()

            Text("Réseaux connus", style = MaterialTheme.typography.titleMedium)
            
            if (baselines.isEmpty()) {
                Text(
                    "Aucun réseau enregistré. Connectez-vous à un WiFi pour commencer le monitoring.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                baselines.forEach { baseline ->
                    NetworkBaselineItem(
                        baseline = baseline,
                        onToggleTrust = { onToggleTrust(baseline.ssid, baseline.isTrusted) },
                        onDelete = { onDeleteNetwork(baseline.ssid) }
                    )
                }
            }

            HorizontalDivider()

            // Language Selection
            ListItem(
                headlineContent = { Text(stringResource(R.string.language)) },
                supportingContent = { 
                    val languageDisplay = when(currentLocale) {
                        "fr" -> stringResource(R.string.language_fr)
                        "en" -> stringResource(R.string.language_en)
                        else -> stringResource(R.string.system_default)
                    }
                    Text(languageDisplay)
                },
                leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
                modifier = Modifier.clickable { showLanguageDialog = true }
            )

            HorizontalDivider()

            if (showDnsDialog) {
                DnsDialog(
                    currentDns = dnsServer,
                    onDismiss = { showDnsDialog = false },
                    onConfirm = {
                        onDnsServerChange(it)
                        showDnsDialog = false
                    }
                )
            }
            
            Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium)
            Text("StopMiddlingMe v1.0.0", style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_desc), style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    LanguageOption(stringResource(R.string.system_default), "system", currentLocale) {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                        showLanguageDialog = false
                    }
                    LanguageOption("English", "en", currentLocale) {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                        showLanguageDialog = false
                    }
                    LanguageOption("Français", "fr", currentLocale) {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr"))
                        showLanguageDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun LanguageOption(label: String, tag: String, currentTag: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = tag == currentTag, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
fun DnsDialog(
    currentDns: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentDns) }
    val isError = !android.util.Patterns.IP_ADDRESS.matcher(text).matches()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dns_server_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dns_server_desc))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.dns_server_hint)) },
                    isError = isError,
                    supportingText = {
                        if (isError) Text(stringResource(R.string.invalid_dns))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = !isError
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
fun NetworkBaselineItem(
    baseline: NetworkBaseline,
    onToggleTrust: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (baseline.isTrusted) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(baseline.ssid, fontWeight = FontWeight.Bold)
                Text("Gateway: ${baseline.gatewayMac}", style = MaterialTheme.typography.labelSmall)
            }
            Checkbox(checked = baseline.isTrusted, onCheckedChange = { onToggleTrust() })
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    com.arda.stopmiddlingme.ui.theme.StopMiddlingMeTheme {
        SettingsContent(
            dnsEnabled = true,
            onDnsToggle = {},
            sslStripEnabled = true,
            onSslStripToggle = {},
            notificationEnabled = true,
            onNotificationToggle = {},
            dnsServer = "1.1.1.1",
            onDnsServerChange = {},
            baselines = listOf(
                NetworkBaseline("Home_WiFi", "00:11:22:33:44:55", "192.168.1.1", "AA:BB:CC:DD:EE:FF", "1.1.1.1", "WPA2", System.currentTimeMillis(), true),
                NetworkBaseline("Work_WiFi", "66:77:88:99:00:11", "10.0.0.1", "FF:EE:DD:CC:BB:AA", "8.8.8.8", "WPA3", System.currentTimeMillis(), false)
            ),
            onToggleTrust = { _, _ -> },
            onDeleteNetwork = {}
        )
    }
}

@Composable
fun ToggleSetting(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
