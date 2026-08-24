package com.sovereignops.timetable.core.earnings

import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class EarningsTest {

    private fun booking(id: Long, day: Int, dollars: Long, status: BookingStatus) =
        Booking(
            id = id,
            start = LocalDateTime.of(2026, 8, day, 18, 0),
            durationMinutes = 60,
            clientAlias = "client$id",
            rate = Money.dollars(dollars),
            status = status,
        )

    @Test fun earnedAndProjectedAreSeparate() {
        val bookings = listOf(
            booking(1, 24, 125, BookingStatus.COMPLETED),
            booking(2, 25, 80, BookingStatus.COMPLETED),
            booking(3, 26, 125, BookingStatus.CONFIRMED),
            booking(4, 27, 50, BookingStatus.INQUIRY),
            booking(5, 23, 125, BookingStatus.NO_SHOW), // neither earned nor projected
            booking(6, 22, 125, BookingStatus.CANCELLED),
        )
        val s = Earnings.summarize(bookings)
        assertEquals(Money.dollars(205), s.earned)
        assertEquals(Money.dollars(175), s.projected)
        assertEquals(2, s.completedCount)
        assertEquals(2, s.projectedCount)
    }

    @Test fun rangeFiltersByStartDate() {
        val bookings = listOf(
            booking(1, 24, 125, BookingStatus.COMPLETED),
            booking(2, 25, 80, BookingStatus.COMPLETED),
            booking(3, 30, 125, BookingStatus.COMPLETED),
        )
        val s = Earnings.summarizeRange(bookings, LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 26))
        assertEquals(Money.dollars(205), s.earned)
        assertEquals(2, s.completedCount)
    }

    @Test fun earnedByDayGroups() {
        val bookings = listOf(
            booking(1, 24, 125, BookingStatus.COMPLETED),
            booking(2, 24, 80, BookingStatus.COMPLETED),
            booking(3, 25, 50, BookingStatus.COMPLETED),
            booking(4, 25, 125, BookingStatus.CONFIRMED), // not earned
        )
        val byDay = Earnings.earnedByDay(bookings)
        assertEquals(Money.dollars(205), byDay[LocalDate.of(2026, 8, 24)])
        assertEquals(Money.dollars(50), byDay[LocalDate.of(2026, 8, 25)])
    }

    @Test fun earnedByWeekUsesIsoWeeks() {
        val bookings = listOf(
            // 2026-08-24 is a Monday (ISO week 35), 2026-08-31 Monday (week 36).
            booking(1, 24, 125, BookingStatus.COMPLETED),
            booking(2, 26, 80, BookingStatus.COMPLETED),
            booking(3, 31, 50, BookingStatus.COMPLETED),
        )
        val byWeek = Earnings.earnedByWeek(bookings)
        assertEquals(Money.dollars(205), byWeek["2026-W35"])
        assertEquals(Money.dollars(50), byWeek["2026-W36"])
    }
}
