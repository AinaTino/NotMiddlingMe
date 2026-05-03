package com.arda.stopmiddlingme.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.arda.stopmiddlingme.R
import com.arda.stopmiddlingme.domain.model.AlertLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alertes de sécurité",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications pour les attaques MITM détectées"
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun notify(sessionId: String, level: AlertLevel, description: String) {
        val title = when (level) {
            AlertLevel.CRITIQUE -> "⚠️ ATTAQUE DÉTECTÉE"
            AlertLevel.WARNING -> "⚠️ Menace probable"
            AlertLevel.SUSPECT -> "🔍 Activité suspecte"
            else -> return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // À remplacer par une icône du projet
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(sessionId.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "security_alerts"
    }
}
