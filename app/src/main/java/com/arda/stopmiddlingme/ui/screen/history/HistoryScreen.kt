package com.arda.stopmiddlingme.ui.screen.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arda.stopmiddlingme.R
import com.arda.stopmiddlingme.data.db.entity.AlertSession
import com.arda.stopmiddlingme.domain.model.AlertLevel
import com.arda.stopmiddlingme.ui.theme.StopMiddlingMeTheme
import com.arda.stopmiddlingme.domain.model.SessionStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()

    HistoryContent(sessions = sessions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
    sessions: List<AlertSession>
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.history_title)) })
        }
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Text(stringResource(R.string.no_history), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sessions.sortedByDescending { it.openedAt }) { session ->
                    HistoryItem(session)
                }
            }
        }
    }
}

@Composable
fun HistoryItem(session: AlertSession) {
    val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(session.openedAt))

    val statusColor = when (session.finalLevel) {
        AlertLevel.SAFE -> Color(0xFF4CAF50)
        AlertLevel.SUSPECT -> Color(0xFFFFC107)
        AlertLevel.WARNING -> Color(0xFFFF9800)
        AlertLevel.CRITIQUE -> Color(0xFFF44336)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = statusColor.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (session.finalLevel == AlertLevel.SAFE) Icons.Default.Info else Icons.Default.Warning,
                        contentDescription = null,
                        tint = statusColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = session.networkSsid, style = MaterialTheme.typography.titleMedium)
                Text(text = dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.score_label, session.totalScore),
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor
                )
                Text(
                    text = session.finalLevel.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryPreview() {
    StopMiddlingMeTheme {
        HistoryContent(
            sessions = listOf(
                AlertSession(
                    id = "1",
                    networkSsid = "Home_WiFi",
                    status = SessionStatus.RESOLVED,
                    totalScore = 5,
                    finalLevel = AlertLevel.WARNING,
                    openedAt = System.currentTimeMillis() - 3600000,
                    autoCloseAt = System.currentTimeMillis() - 3500000,
                    closedAt = System.currentTimeMillis() - 3550000
                ),
                AlertSession(
                    id = "2",
                    networkSsid = "Starbucks_Free",
                    status = SessionStatus.RESOLVED,
                    totalScore = 0,
                    finalLevel = AlertLevel.SAFE,
                    openedAt = System.currentTimeMillis() - 7200000,
                    autoCloseAt = System.currentTimeMillis() - 7100000,
                    closedAt = System.currentTimeMillis() - 7150000
                )
            )
        )
    }
}
