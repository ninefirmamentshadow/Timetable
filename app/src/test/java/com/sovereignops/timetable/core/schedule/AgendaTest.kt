package com.sovereignops.timetable.core.schedule

import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AgendaTest {

    private fun booking(id: Long, start: LocalDateTime, minutes: Int = 60, status: BookingStatus = BookingStatus.CONFIRMED) =
        Booking(id, start, minutes, "client$id", Money.dollars(300), status)

    private val now = LocalDateTime.of(2026, 8, 28, 12, 0)

    @Test fun upcomingExcludesPastAndSortsAscending() {
        val past = booking(1, now.minusHours(3))
        val soon = booking(2, now.plusHours(2))
        val later = booking(3, now.plusHours(5))
        val upcoming = Agenda.upcoming(listOf(later, past, soon), now)
        assertEquals(listOf(2L, 3L), upcoming.map { it.id })
    }

    @Test fun inProgressBookingCountsAsUpcoming() {
        val inProgress = booking(1, now.minusMinutes(15), 60) // ends 12:45, after now
        assertEquals(listOf(1L), Agenda.upcoming(listOf(inProgress), now).map { it.id })
    }

    @Test fun cancelledExcluded() {
        val cancelled = booking(1, now.plusHours(2), status = BookingStatus.CANCELLED)
        assertEquals(emptyList<Long>(), Agenda.upcoming(listOf(cancelled), now).map { it.id })
    }

    @Test fun nextIsEarliestUpcoming() {
        val soon = booking(2, now.plusHours(2))
        val later = booking(3, now.plusHours(5))
        assertEquals(2L, Agenda.next(listOf(later, soon), now)?.id)
    }

    @Test fun nextIsNullWhenNothingUpcoming() {
        val past = booking(1, now.minusHours(3))
        assertNull(Agenda.next(listOf(past), now))
    }

    @Test fun groupsUpcomingByDay() {
        val today1 = booking(1, now.plusHours(1))
        val today2 = booking(2, now.plusHours(3))
        val tomorrow = booking(3, now.plusDays(1))
        val days = Agenda.upcomingByDay(listOf(tomorrow, today2, today1), now)
        assertEquals(2, days.size)
        assertEquals(listOf(1L, 2L), days[0].bookings.map { it.id })
        assertEquals(listOf(3L), days[1].bookings.map { it.id })
    }

    @Test fun gapsFindsFillableOpenings() {
        val date = LocalDate.of(2026, 8, 28)
        val a = booking(1, date.atTime(14, 0), 60) // 14:00–15:00
        val b = booking(2, date.atTime(18, 0), 60) // 18:00–19:00 → 3h gap
        val c = booking(3, date.atTime(19, 30), 60) // 30m gap after b
        val gaps = Agenda.gapsOn(listOf(a, b, c), date, minGapMinutes = 60)
        assertEquals(1, gaps.size)
        assertEquals(180L, gaps[0].minutes)
        assertEquals(date.atTime(15, 0), gaps[0].start)
    }

    @Test fun gapsEmptyWithFewerThanTwoBookings() {
        val date = LocalDate.of(2026, 8, 28)
        val a = booking(1, date.atTime(14, 0), 60)
        assertEquals(emptyList<TimeGap>(), Agenda.gapsOn(listOf(a), date, 30))
    }
}
