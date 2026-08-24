package com.sovereignops.timetable.core.model

import com.sovereignops.timetable.core.Money
import java.time.Duration
import java.time.LocalDateTime

/**
 * One appointment. Deliberately alias-first: [clientAlias] is a handle the
 * operator chooses, never a legal name. [locationLabel] is a freeform label the
 * operator controls; the app stores whatever they type and never resolves,
 * geocodes, or transmits it. The app holds no network permission.
 */
data class Booking(
    val id: Long,
    val start: LocalDateTime,
    val durationMinutes: Int,
    val clientAlias: String,
    val rate: Money,
    val status: BookingStatus,
    val locationLabel: String = "",
    val screened: Boolean = false,
    val depositReceived: Boolean = false,
    val notes: String = "",
) {
    init {
        require(durationMinutes > 0) { "durationMinutes must be positive" }
    }

    val end: LocalDateTime
        get() = start.plusMinutes(durationMinutes.toLong())

    val duration: Duration
        get() = Duration.ofMinutes(durationMinutes.toLong())

    /** True when this booking still holds its slot on the calendar. */
    val occupiesCalendar: Boolean
        get() = status.occupiesCalendar

    fun isPast(now: LocalDateTime): Boolean = !end.isAfter(now)

    fun isUpcoming(now: LocalDateTime): Boolean = end.isAfter(now)
}
