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
    /**
     * Optional note that an advance payment was received for this booking. The name
     * is historical and is kept so stored rows and backups stay readable. It is not
     * a policy: no rule requires it, no default sets it, and nothing (earnings,
     * conflicts, validation, status changes) reads it. Cash is [amountReceived] only.
     */
    val depositReceived: Boolean = false,
    val notes: String = "",
    /**
     * Money actually received for this booking, as the operator recorded it; null
     * until recorded. This, not [rate] or a COMPLETED status, is cash: a completed
     * booking's nominal rate is not money in hand.
     */
    val amountReceived: Money? = null,
) {
    init {
        require(durationMinutes > 0) { "durationMinutes must be positive" }
        require(amountReceived == null || amountReceived.cents >= 0) { "amountReceived must not be negative" }
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

    /**
     * Still an open inquiry or confirmation although its time has passed: nobody
     * recorded whether it happened. Such a booking is neither earned nor
     * projected money; it needs the operator to mark it completed, no-show or
     * cancelled.
     */
    fun needsReview(now: LocalDateTime): Boolean = status.isProjected && isPast(now)

    /** Copy with a new [status]; everything else is unchanged. */
    fun withStatus(status: BookingStatus): Booking = copy(status = status)
}
