package com.sovereignops.timetable.core.earnings

import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class EarningsTest {

    /** After every fixture booking below, so the legacy tests treat none as overdue. */
    private val farBefore = LocalDateTime.of(2026, 8, 1, 0, 0)

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
            booking(1, 24, 300, BookingStatus.COMPLETED),
            booking(2, 25, 200, BookingStatus.COMPLETED),
            booking(3, 26, 300, BookingStatus.CONFIRMED),
            booking(4, 27, 140, BookingStatus.INQUIRY),
            booking(5, 23, 300, BookingStatus.NO_SHOW), // neither earned nor projected
            booking(6, 22, 300, BookingStatus.CANCELLED),
        )
        val s = Earnings.summarize(bookings, farBefore)
        assertEquals(Money.dollars(500), s.earned)
        assertEquals(Money.dollars(440), s.projected)
        assertEquals(2, s.completedCount)
        assertEquals(2, s.projectedCount)
    }

    @Test fun rangeFiltersByStartDate() {
        val bookings = listOf(
            booking(1, 24, 300, BookingStatus.COMPLETED),
            booking(2, 25, 200, BookingStatus.COMPLETED),
            booking(3, 30, 300, BookingStatus.COMPLETED),
        )
        val s = Earnings.summarizeRange(bookings, LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 26), farBefore)
        assertEquals(Money.dollars(500), s.earned)
        assertEquals(2, s.completedCount)
    }

    @Test fun earnedByDayGroups() {
        val bookings = listOf(
            booking(1, 24, 300, BookingStatus.COMPLETED),
            booking(2, 24, 200, BookingStatus.COMPLETED),
            booking(3, 25, 140, BookingStatus.COMPLETED),
            booking(4, 25, 300, BookingStatus.CONFIRMED), // not earned
        )
        val byDay = Earnings.earnedByDay(bookings)
        assertEquals(Money.dollars(500), byDay[LocalDate.of(2026, 8, 24)])
        assertEquals(Money.dollars(140), byDay[LocalDate.of(2026, 8, 25)])
    }

    @Test fun earnedByWeekUsesIsoWeeks() {
        val bookings = listOf(
            // 2026-08-24 is a Monday (ISO week 35), 2026-08-31 Monday (week 36).
            booking(1, 24, 300, BookingStatus.COMPLETED),
            booking(2, 26, 200, BookingStatus.COMPLETED),
            booking(3, 31, 140, BookingStatus.COMPLETED),
        )
        val byWeek = Earnings.earnedByWeek(bookings)
        assertEquals(Money.dollars(500), byWeek["2026-W35"])
        assertEquals(Money.dollars(140), byWeek["2026-W36"])
    }

    // Regression: a confirmed booking from last week used to stay "projected"
    // forever, so projected money only ever grew.
    @Test fun overdueOpenBookingsAreNeedsReviewNotProjected() {
        val now = LocalDateTime.of(2026, 8, 28, 12, 0)
        val bookings = listOf(
            booking(1, 20, 300, BookingStatus.CONFIRMED), // a week ago, never resolved
            booking(2, 27, 140, BookingStatus.INQUIRY),   // yesterday, never resolved
            booking(3, 29, 300, BookingStatus.CONFIRMED), // tomorrow
            booking(4, 30, 200, BookingStatus.INQUIRY),   // in two days
            booking(5, 21, 300, BookingStatus.COMPLETED),
        )
        val s = Earnings.summarize(bookings, now)
        assertEquals(Money.dollars(500), s.projected)
        assertEquals(2, s.projectedCount)
        assertEquals(2, s.needsReviewCount)
        assertEquals(Money.dollars(300), s.earned)
    }

    @Test fun resolvingAnOverdueBookingMovesItOutOfReview() {
        val now = LocalDateTime.of(2026, 8, 28, 12, 0)
        val open = booking(1, 20, 300, BookingStatus.CONFIRMED)
        assertEquals(1, Earnings.summarize(listOf(open), now).needsReviewCount)

        val completed = Earnings.summarize(listOf(open.withStatus(BookingStatus.COMPLETED)), now)
        assertEquals(Money.dollars(300), completed.earned)
        assertEquals(0, completed.needsReviewCount)
        assertEquals(Money.ZERO, completed.projected)

        val noShow = Earnings.summarize(listOf(open.withStatus(BookingStatus.NO_SHOW)), now)
        assertEquals(Money.ZERO, noShow.earned)
        assertEquals(0, noShow.needsReviewCount)
    }

    @Test fun earnedAndProjectedAreNeverOneFigure() {
        val now = LocalDateTime.of(2026, 8, 28, 12, 0)
        val s = Earnings.summarize(
            listOf(
                booking(1, 24, 300, BookingStatus.COMPLETED),
                booking(2, 30, 200, BookingStatus.CONFIRMED),
            ),
            now,
        )
        assertEquals(Money.dollars(300), s.earned)
        assertEquals(Money.dollars(200), s.projected) // not 500
    }

    @Test fun bookingInProgressIsStillProjected() {
        val inProgress = Booking(
            1, LocalDateTime.of(2026, 8, 28, 11, 30), 60, "client1", Money.dollars(300), BookingStatus.CONFIRMED,
        ) // ends 12:30
        val s = Earnings.summarize(listOf(inProgress), LocalDateTime.of(2026, 8, 28, 12, 0))
        assertEquals(1, s.projectedCount)
        assertEquals(0, s.needsReviewCount)
    }
}
