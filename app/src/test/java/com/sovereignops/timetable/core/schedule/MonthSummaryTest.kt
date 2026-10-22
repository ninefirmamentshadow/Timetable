package com.sovereignops.timetable.core.schedule

import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class MonthSummaryTest {

    private fun booking(id: Long, start: LocalDateTime, status: BookingStatus = BookingStatus.CONFIRMED, minutes: Int = 60) =
        Booking(id, start, minutes, "alias$id", Money.dollars(100), status)

    private val d20 = LocalDate.of(2026, 10, 20)

    @Test fun groupsByStartDayInTimeOrder() {
        val late = booking(1, d20.atTime(21, 0))
        val early = booking(2, d20.atTime(9, 0))
        val next = booking(3, d20.plusDays(1).atTime(12, 0))
        val byDay = MonthSummary.byDay(listOf(late, next, early))
        assertEquals(listOf(2L, 1L), byDay[d20]!!.map { it.id })
        assertEquals(listOf(3L), byDay[d20.plusDays(1)]!!.map { it.id })
        assertEquals(2, byDay.size)
    }

    @Test fun cancelledAndNoShowDoNotMarkADay() {
        val bookings = listOf(
            booking(1, d20.atTime(9, 0), BookingStatus.CANCELLED),
            booking(2, d20.atTime(10, 0), BookingStatus.NO_SHOW),
            booking(3, d20.plusDays(1).atTime(10, 0), BookingStatus.INQUIRY),
            booking(4, d20.plusDays(2).atTime(10, 0), BookingStatus.COMPLETED),
        )
        assertEquals(setOf(d20.plusDays(1), d20.plusDays(2)), MonthSummary.byDay(bookings).keys)
    }

    @Test fun aBookingCrossingMidnightBelongsToItsStartDay() {
        val b = booking(1, d20.atTime(23, 30), minutes = 120)
        assertEquals(listOf(1L), MonthSummary.on(listOf(b), d20).map { it.id })
        assertEquals(emptyList<Booking>(), MonthSummary.on(listOf(b), d20.plusDays(1)))
    }

    @Test fun emptyInputGivesEmptyResults() {
        assertEquals(emptyMap<LocalDate, List<Booking>>(), MonthSummary.byDay(emptyList()))
        assertEquals(emptyList<Booking>(), MonthSummary.on(emptyList(), d20))
    }
}
