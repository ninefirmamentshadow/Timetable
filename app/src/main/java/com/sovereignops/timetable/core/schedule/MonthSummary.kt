package com.sovereignops.timetable.core.schedule

import com.sovereignops.timetable.core.model.Booking
import java.time.LocalDate

/**
 * Which days of a month have bookings. Only bookings that still hold their slot
 * count (a cancelled or no-show booking frees its day), and a booking belongs to
 * the day it starts on. Pure; no clock.
 */
object MonthSummary {

    /** Calendar-occupying bookings grouped by the day they start, each day's list in time order. */
    fun byDay(bookings: List<Booking>): Map<LocalDate, List<Booking>> =
        bookings.asSequence()
            .filter { it.occupiesCalendar }
            .groupBy { it.start.toLocalDate() }
            .mapValues { (_, list) -> list.sortedBy { it.start } }

    /** The bookings that start on [date], in time order. */
    fun on(bookings: List<Booking>, date: LocalDate): List<Booking> = byDay(bookings)[date].orEmpty()
}
