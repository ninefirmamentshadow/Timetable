package com.sovereignops.timetable.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An unsaved editor form, kept so a crash, a killed process or a closed dialog
 * never loses what was typed. One row per editor session target: [bookingId] is
 * the booking being edited, or 0 for a new booking. Text fields are stored exactly
 * as typed (not validated); [dateEpochDay] and [minuteOfDay] hold the chosen date
 * and time. It lives in the same (encrypted) database as the bookings.
 */
@Entity(tableName = "booking_drafts")
data class BookingDraftEntity(
    @PrimaryKey val bookingId: Long,
    val alias: String,
    val dateEpochDay: Long,
    val minuteOfDay: Int,
    val durationText: String,
    val rateText: String,
    val status: String,
    val locationLabel: String,
    val notes: String,
    val screened: Boolean,
    val depositReceived: Boolean,
    val updatedAtEpochSecond: Long,
)
