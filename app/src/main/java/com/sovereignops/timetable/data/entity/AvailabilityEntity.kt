package com.sovereignops.timetable.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Storage row for a recurring weekly availability window. [dayOfWeek] is the ISO
 * value 1..7 (Mon..Sun); [startMinuteOfDay]/[endMinuteOfDay] are minutes since
 * midnight.
 */
@Entity(tableName = "availability")
data class AvailabilityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: Int,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
)
