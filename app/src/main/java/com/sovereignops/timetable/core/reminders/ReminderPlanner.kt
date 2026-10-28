package com.sovereignops.timetable.core.reminders

import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** One reminder to deliver at the instant [fireAt] for the booking [bookingId]. */
data class PlannedReminder(val bookingId: Long, val fireAt: Instant, val bookingStart: LocalDateTime)

/**
 * Decides which reminders should exist right now. Pure: no clock, no Android; the
 * caller passes [now] and the [zone] that bookings' wall-clock times are in. A
 * reminder fires `leadMinutes + travelBufferMinutes` of real time before a
 * booking starts (on a daylight-saving night too), so the lead covers getting
 * ready and the buffer covers the trip. Only CONFIRMED bookings that have not started are reminded; an
 * inquiry is not a commitment, and a completed, cancelled or no-show booking
 * needs nothing. A reminder whose time has already passed is not scheduled (no
 * catch-up burst after the phone was off), and only the next [maxCount]
 * reminders within [horizonDays] days are planned, which bounds the number of
 * alarms.
 */
object ReminderPlanner {

    const val DEFAULT_LEAD_MINUTES = 60
    const val MAX_LEAD_MINUTES = 24 * 60
    const val DEFAULT_HORIZON_DAYS = 14L
    const val DEFAULT_MAX_COUNT = 40

    fun plan(
        bookings: List<Booking>,
        now: LocalDateTime,
        leadMinutes: Int,
        travelBufferMinutes: Int,
        zone: ZoneId,
        horizonDays: Long = DEFAULT_HORIZON_DAYS,
        maxCount: Int = DEFAULT_MAX_COUNT,
    ): List<PlannedReminder> {
        val before = leadMinutes.coerceIn(0, MAX_LEAD_MINUTES).toLong() + travelBufferMinutes.coerceAtLeast(0).toLong()
        val nowAt = now.atZone(zone).toInstant()
        val horizon = now.plusDays(horizonDays).atZone(zone).toInstant()
        return bookings.asSequence()
            .filter { it.status == BookingStatus.CONFIRMED }
            .filter { it.start.isAfter(now) }
            .map { PlannedReminder(it.id, it.start.atZone(zone).toInstant().minusSeconds(before * 60), it.start) }
            .filter { it.fireAt.isAfter(nowAt) && !it.fireAt.isAfter(horizon) }
            .sortedBy { it.fireAt }
            .take(maxCount)
            .toList()
    }

    /** The reminder's public text: the start time only, no alias or place. */
    fun publicLine(start: LocalDateTime, timeText: String): String = "Booking at $timeText"
}
