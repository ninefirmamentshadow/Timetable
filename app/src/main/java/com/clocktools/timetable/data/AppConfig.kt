package com.clocktools.timetable.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalTime

/** Single-row table: app-wide settings. */
@Entity(tableName = "app_config")
data class AppConfig(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val curfewTime: LocalTime,
    val bufferMinutes: Int = 20,
    val contingencyMinutes: Int = 15,
    val reverifyIntervalDays: Int = 90
) {
    companion object {
        const val SINGLETON_ID = 0

        fun default() = AppConfig(curfewTime = LocalTime.of(22, 0))
    }
}
