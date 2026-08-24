package com.sovereignops.timetable.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Storage row for a booking. Deliberately primitive-only so Room reflects over a
 * flat table and the domain model stays free of persistence concerns; mapping
 * lives in EntityMappers.
 *
 * [startEpochSecond] encodes the wall-clock start against a fixed UTC reference
 * (see EntityMappers) — the app never converts time zones, so this is a stable,
 * sortable, reversible key, not a real instant.
 */
@Entity(tableName = "bookings")
data class BookingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochSecond: Long,
    val durationMinutes: Int,
    val clientAlias: String,
    val rateCents: Long,
    val status: String,
    val locationLabel: String,
    val screened: Boolean,
    val depositReceived: Boolean,
    val notes: String,
)
