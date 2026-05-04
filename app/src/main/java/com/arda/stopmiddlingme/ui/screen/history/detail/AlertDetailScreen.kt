package com.arda.stopmiddlingme.ui.screen.history.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arda.stopmiddlingme.data.db.entity.AlertSession
import com.arda.stopmiddlingme.data.db.entity.SignalInstance
import com.arda.stopmiddlingme.domain.model.AlertLevel
import com.arda.stopmiddlingme.util.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDetailScreen(
    onBack: () -> Unit,
    viewModel: AlertDetailViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsState()
    val signals by viewModel.signals.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Détails de l'alerte") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        session?.let { currentSession ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                SessionHeader(currentSession)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Signaux détectés",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                SignalList(signals)
            }
        }
    }
}

@Composable
fun SessionHeader(session: AlertSession) {
    val statusColor = when (session.finalLevel) {
        AlertLevel.SAFE -> Color(0xFF4CAF50)
        AlertLevel.SUSPECT -> Color(0xFFFFC107)
        AlertLevel.WARNING -> Color(0xFFFF9800)
        AlertLevel.CRITIQUE -> Color(0xFFF44336)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (session.finalLevel == AlertLevel.SAFE) Icons.Default.Shield else Icons.Default.Warning,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = session.networkSsid,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Score total: ${session.totalScore}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = statusColor.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))
            Text("Date: ${DateTimeUtils.formatDateTime(session.openedAt)}", style = MaterialTheme.typography.bodyMedium)
            Text("Niveau final: ${session.finalLevel.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = statusColor)
        }
    }
}

@Composable
fun SignalList(signals: List<SignalInstance>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(signals.sortedByDescending { it.timestamp }) { signal ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = signal.type.description,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = signal.detail,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = DateTimeUtils.formatTime(signal.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
