package com.clocktools.timetable.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "zones")
data class Zone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val routeLabel: String,
    val lastOutboundTime: LocalTime,
    val lastReturnTime: LocalTime,
    val walkToStopMinutes: Int = 15,
    val lastVerified: LocalDate
)
