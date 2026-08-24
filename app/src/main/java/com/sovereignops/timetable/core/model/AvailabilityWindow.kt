package com.sovereignops.timetable.core.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A recurring weekly working window, e.g. Friday 18:00–23:00. Half-open on the
 * end: a window ending at 23:00 does not itself permit a booking that runs to
 * 23:01. Windows do not cross midnight; split an overnight window into two.
 */
data class AvailabilityWindow(
    val id: Long,
    val dayOfWeek: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
) {
    init {
        require(end > start) { "window end must be after start; split windows that cross midnight" }
    }

    /** Does a booking of [durationMinutes] starting at [at] fit entirely inside this window? */
    fun accommodates(at: LocalDateTime, durationMinutes: Int): Boolean {
        if (at.dayOfWeek != dayOfWeek) return false
        val bookingStart = at.toLocalTime()
        val bookingEnd = at.plusMinutes(durationMinutes.toLong())
        // A booking that runs past midnight leaves this day's window by definition.
        if (bookingEnd.toLocalDate() != at.toLocalDate()) return false
        return bookingStart >= start && bookingEnd.toLocalTime() <= end
    }
}
