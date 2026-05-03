package com.arda.stopmiddlingme.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    SettingsContent()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("General Configuration", style = MaterialTheme.typography.titleMedium)
            
            var dnsEnabled by remember { mutableStateOf(true) }
            ToggleSetting("Active DNS Monitoring", "Periodically check for DNS hijacking", dnsEnabled) { dnsEnabled = it }
            
            var sslStripEnabled by remember { mutableStateOf(true) }
            ToggleSetting("SSL Strip Detection", "Monitor port 80 for HSTS downgrade attempts", sslStripEnabled) { sslStripEnabled = it }
            
            var notificationEnabled by remember { mutableStateOf(true) }
            ToggleSetting("Real-time Notifications", "Show alerts in system drawer", notificationEnabled) { notificationEnabled = it }
            
            HorizontalDivider()
            
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text("StopMiddlingMe v1.0.0", style = MaterialTheme.typography.bodyMedium)
            Text("MITM Detection Tool for Android", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    com.arda.stopmiddlingme.ui.theme.StopMiddlingMeTheme {
        SettingsContent()
    }
}

@Composable
fun ToggleSetting(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
