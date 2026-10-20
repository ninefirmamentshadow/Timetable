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
        Booking(id, start, minutes, "client$id", Money.dollars(125), status)

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

    // Regression: past bookings were never shown anywhere.
    @Test fun sectionsPutOverdueOpenBookingsInReviewAndKeepHistoryReachable() {
        val overdue = booking(1, now.minusDays(2), status = BookingStatus.CONFIRMED)
        val doneYesterday = booking(2, now.minusDays(1), status = BookingStatus.COMPLETED)
        val doneLastWeek = booking(3, now.minusDays(7), status = BookingStatus.COMPLETED)
        val cancelledPast = booking(4, now.minusDays(3), status = BookingStatus.CANCELLED)
        val noShow = booking(5, now.minusDays(1).minusHours(2), status = BookingStatus.NO_SHOW)
        val soon = booking(6, now.plusHours(3))
        val cancelledFuture = booking(7, now.plusDays(1), status = BookingStatus.CANCELLED)

        val s = Agenda.sections(
            listOf(soon, doneLastWeek, overdue, cancelledFuture, doneYesterday, noShow, cancelledPast),
            now,
        )

        assertEquals(listOf(1L), s.needsReview.map { it.id })
        assertEquals(listOf(6L), s.upcoming.flatMap { it.bookings }.map { it.id })
        // Closed bookings of any date, most recent day first; within a day the later booking first.
        // A cancelled booking in the future is closed too, so it sits at the top.
        assertEquals(
            listOf(
                now.plusDays(1).toLocalDate(),
                now.minusDays(1).toLocalDate(),
                now.minusDays(3).toLocalDate(),
                now.minusDays(7).toLocalDate(),
            ),
            s.past.map { it.date },
        )
        assertEquals(listOf(7L), s.past[0].bookings.map { it.id })
        assertEquals(listOf(2L, 5L), s.past[1].bookings.map { it.id })
        // Nothing is dropped: every booking is in exactly one section.
        val shown = s.needsReview.size + s.upcoming.sumOf { it.bookings.size } + s.past.sumOf { it.bookings.size }
        assertEquals(7, shown)
    }

    @Test fun overdueInquiryIsNeedsReviewToo() {
        val inquiry = booking(1, now.minusHours(5), status = BookingStatus.INQUIRY)
        assertEquals(listOf(1L), Agenda.sections(listOf(inquiry), now).needsReview.map { it.id })
    }

    @Test fun completedBookingMarkedEarlyStaysUpcomingUntilItEnds() {
        val early = booking(1, now.plusHours(1), status = BookingStatus.COMPLETED)
        val s = Agenda.sections(listOf(early), now)
        assertEquals(listOf(1L), s.upcoming.flatMap { it.bookings }.map { it.id })
        assertEquals(0, s.past.size)
    }
}
