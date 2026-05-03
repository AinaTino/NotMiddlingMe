package com.arda.stopmiddlingme.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arda.stopmiddlingme.domain.model.AlertLevel
import com.arda.stopmiddlingme.domain.model.SessionStatus

@Entity(
    tableName = "alert_session",
    foreignKeys = [
        ForeignKey(
            entity = NetworkBaseline::class,
            parentColumns = ["ssid"],
            childColumns = ["networkSsid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("networkSsid")]
)
data class AlertSession(
    @PrimaryKey
    val id: String,
    val networkSsid: String,
    val status: SessionStatus,
    val totalScore: Int,
    val finalLevel: AlertLevel,
    val openedAt: Long,
    // Timer de fermeture automatique — resetté à chaque nouveau signal (fenêtre glissante)
    val autoCloseAt: Long,
    // null tant que la session est OPEN
    val closedAt: Long? = null
)
