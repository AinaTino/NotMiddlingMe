package com.arda.stopmiddlingme.ui.screen.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.arda.stopmiddlingme.R

@Composable
fun SettingsScreen() {
    SettingsContent()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent() {
    var showLanguageDialog by remember { mutableStateOf(false) }
    val currentLocale = AppCompatDelegate.getApplicationLocales().get(0)?.language ?: "en"

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_settings)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.general_config), style = MaterialTheme.typography.titleMedium)
            
            var dnsEnabled by remember { mutableStateOf(true) }
            ToggleSetting(
                stringResource(R.string.dns_monitoring),
                stringResource(R.string.dns_monitoring_desc),
                dnsEnabled
            ) { dnsEnabled = it }
            
            var sslStripEnabled by remember { mutableStateOf(true) }
            ToggleSetting(
                stringResource(R.string.ssl_strip_detection),
                stringResource(R.string.ssl_strip_desc),
                sslStripEnabled
            ) { sslStripEnabled = it }
            
            var notificationEnabled by remember { mutableStateOf(true) }
            ToggleSetting(
                stringResource(R.string.real_time_notifications),
                stringResource(R.string.notifications_desc),
                notificationEnabled
            ) { notificationEnabled = it }
            
            HorizontalDivider()

            // Language Selection
            ListItem(
                headlineContent = { Text(stringResource(R.string.language)) },
                supportingContent = { 
                    Text(if (currentLocale == "fr") stringResource(R.string.language_fr) else stringResource(R.string.language_en)) 
                },
                leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
                modifier = Modifier.clickable { showLanguageDialog = true }
            )

            HorizontalDivider()
            
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
