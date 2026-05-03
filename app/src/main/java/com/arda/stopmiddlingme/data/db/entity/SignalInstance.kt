package com.arda.stopmiddlingme.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arda.stopmiddlingme.domain.model.SignalType

@Entity(
    tableName = "signal_instance",
    foreignKeys = [
        ForeignKey(
            entity = AlertSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class SignalInstance(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    // Le type porte le poids, standalone et decay — pas besoin de les dupliquer ici
    val type: SignalType,
    // Description lisible ex: "Gateway MAC: BB:BB:BB → CC:CC:CC"
    val detail: String,
    val timestamp: Long,
    // Calculé = timestamp + type.decaySeconds * 1000
    // Quand expiré, le signal ne contribue plus au score
    val expireAt: Long
)
