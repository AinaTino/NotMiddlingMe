package com.arda.stopmiddlingme.data.db

import androidx.room.TypeConverter
import com.arda.stopmiddlingme.domain.model.AlertLevel
import com.arda.stopmiddlingme.domain.model.SessionStatus
import com.arda.stopmiddlingme.domain.model.SignalType

class Converters {

    @TypeConverter
    fun fromSessionStatus(value: SessionStatus): String = value.name

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus = SessionStatus.valueOf(value)

    @TypeConverter
    fun fromAlertLevel(value: AlertLevel): String = value.name

    @TypeConverter
    fun toAlertLevel(value: String): AlertLevel = AlertLevel.valueOf(value)

    @TypeConverter
    fun fromSignalType(value: SignalType): String = value.name

    @TypeConverter
    fun toSignalType(value: String): SignalType = SignalType.valueOf(value)
}
