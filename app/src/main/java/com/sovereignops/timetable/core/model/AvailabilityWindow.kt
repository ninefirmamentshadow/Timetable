package com.sovereignops.timetable.core.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A recurring weekly working window, e.g. Friday 18:00–23:00, held as minutes
 * since midnight. Half-open on the end: a window ending at 23:00 does not itself
 * permit a booking that runs to 23:01.
 *
 * A window may end at midnight: [endMinute] is 1440 (24:00), which the
 * [LocalTime] constructor accepts as [LocalTime.MIDNIGHT]. A window never wraps
 * past midnight; an overnight shift is two windows (Friday ..24:00, Saturday
 * 00:00..), and [AvailabilityCoverage] treats a booking that crosses midnight as
 * covered when both days' windows cover their part.
 */
data class AvailabilityWindow(
    val id: Long,
    val dayOfWeek: DayOfWeek,
    val startMinute: Int,
    val endMinute: Int,
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "window start must be within the day" }
        require(endMinute in 1..MINUTES_PER_DAY) { "window end must be within the day (24:00 allowed)" }
        require(endMinute > startMinute) { "window end must be after start; split windows that cross midnight" }
    }

    /** Build from clock times; an [end] of [LocalTime.MIDNIGHT] means 24:00 (end of day). */
    constructor(id: Long, dayOfWeek: DayOfWeek, start: LocalTime, end: LocalTime) :
        this(id, dayOfWeek, minuteOf(start), endMinuteOf(end))

    val start: LocalTime get() = LocalTime.of(startMinute / 60, startMinute % 60)

    /** The end as a clock time; midnight for a window that ends at 24:00. */
    val end: LocalTime get() = if (endsAtMidnight) LocalTime.MIDNIGHT else LocalTime.of(endMinute / 60, endMinute % 60)

    val endsAtMidnight: Boolean get() = endMinute == MINUTES_PER_DAY

    /** Does a booking of [durationMinutes] starting at [at] fit entirely inside this window? */
    fun accommodates(at: LocalDateTime, durationMinutes: Int): Boolean {
        if (at.dayOfWeek != dayOfWeek) return false
        val bookingStart = at.hour * 60 + at.minute
        // A booking that runs past midnight leaves this day's window by definition.
        return bookingStart >= startMinute && bookingStart + durationMinutes <= endMinute
    }

    /** Do the two windows share any time on the same weekday? Touching ends do not overlap. */
    fun overlaps(other: AvailabilityWindow): Boolean =
        dayOfWeek == other.dayOfWeek && startMinute < other.endMinute && other.startMinute < endMinute

    companion object {
        const val MINUTES_PER_DAY = 1440

        fun minuteOf(time: LocalTime): Int = time.hour * 60 + time.minute

        /** Minutes since midnight for a window end; midnight is 24:00 (1440). */
        fun endMinuteOf(end: LocalTime): Int = if (end == LocalTime.MIDNIGHT) MINUTES_PER_DAY else minuteOf(end)
    }
}
