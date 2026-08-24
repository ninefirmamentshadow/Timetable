package com.sovereignops.timetable.core.schedule

import com.sovereignops.timetable.core.model.Booking
import java.time.LocalDate
import java.time.LocalDateTime

/** A day's worth of bookings, for a grouped agenda list. */
data class AgendaDay(
    val date: LocalDate,
    val bookings: List<Booking>,
)

/**
 * Pure agenda shaping over a booking list. No clock is read internally; the
 * caller passes [now] so the same inputs always produce the same agenda.
 */
object Agenda {

    /**
     * Upcoming calendar-occupying bookings (not cancelled / no-show), ending
     * after [now], earliest first.
     */
    fun upcoming(bookings: List<Booking>, now: LocalDateTime): List<Booking> =
        bookings.asSequence()
            .filter { it.occupiesCalendar }
            .filter { it.isUpcoming(now) }
            .sortedBy { it.start }
            .toList()

    /** Past calendar-occupying bookings, most recent first. */
    fun past(bookings: List<Booking>, now: LocalDateTime): List<Booking> =
        bookings.asSequence()
            .filter { it.occupiesCalendar }
            .filter { it.isPast(now) }
            .sortedByDescending { it.start }
            .toList()

    /** The very next upcoming booking, or null. */
    fun next(bookings: List<Booking>, now: LocalDateTime): Booking? =
        upcoming(bookings, now).firstOrNull()

    /** Upcoming bookings grouped by calendar day, days and bookings both ascending. */
    fun upcomingByDay(bookings: List<Booking>, now: LocalDateTime): List<AgendaDay> =
        upcoming(bookings, now)
            .groupBy { it.start.toLocalDate() }
            .toSortedMap()
            .map { (date, list) -> AgendaDay(date, list.sortedBy { it.start }) }

    /**
     * Free gaps between consecutive upcoming bookings on [date] that are at least
     * [minGapMinutes] long — the openings a new booking could actually fill.
     * Bounds are the first booking's start and the last booking's end; this does
     * not invent availability outside the booked span (that is availability's job).
     */
    fun gapsOn(
        bookings: List<Booking>,
        date: LocalDate,
        minGapMinutes: Int,
    ): List<TimeGap> {
        val day = bookings.asSequence()
            .filter { it.occupiesCalendar }
            .filter { it.start.toLocalDate() == date }
            .sortedBy { it.start }
            .toList()
        if (day.size < 2) return emptyList()

        val gaps = mutableListOf<TimeGap>()
        for (i in 0 until day.size - 1) {
            val gapStart = day[i].end
            val gapEnd = day[i + 1].start
            val minutes = java.time.Duration.between(gapStart, gapEnd).toMinutes()
            if (minutes >= minGapMinutes) gaps += TimeGap(gapStart, gapEnd, minutes)
        }
        return gaps
    }
}

data class TimeGap(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val minutes: Long,
)
