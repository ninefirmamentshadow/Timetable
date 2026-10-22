package com.sovereignops.timetable.core.reminders

import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ReminderPlannerTest {

    private val now = LocalDateTime.of(2026, 10, 20, 12, 0)

    private fun booking(id: Long, start: LocalDateTime, status: BookingStatus = BookingStatus.CONFIRMED) =
        Booking(id, start, 60, "alias$id", Money.dollars(100), status)

    private fun plan(vararg b: Booking, lead: Int = 60, buffer: Int = 45, nowAt: LocalDateTime = now) =
        ReminderPlanner.plan(b.toList(), nowAt, lead, buffer)

    @Test fun firesLeadPlusTravelBufferBeforeTheStart() {
        val p = plan(booking(1, now.plusDays(1).withHour(19).withMinute(0)))
        assertEquals(1, p.size)
        // 19:00 minus 60 lead minus 45 buffer = 17:15
        assertEquals(now.plusDays(1).withHour(17).withMinute(15), p[0].fireAt)
        assertEquals(1L, p[0].bookingId)
    }

    @Test fun onlyConfirmedBookingsAreReminded() {
        val start = now.plusDays(1)
        val p = plan(
            booking(1, start, BookingStatus.INQUIRY),
            booking(2, start, BookingStatus.CONFIRMED),
            booking(3, start, BookingStatus.COMPLETED),
            booking(4, start, BookingStatus.CANCELLED),
            booking(5, start, BookingStatus.NO_SHOW),
        )
        assertEquals(listOf(2L), p.map { it.bookingId })
    }

    @Test fun aReminderThatIsAlreadyDueIsNotScheduled() {
        // Starts in 90 minutes; reminder was due 15 minutes ago (60 + 45 = 105 before).
        assertEquals(emptyList<PlannedReminder>(), plan(booking(1, now.plusMinutes(90))))
        // Starts in 2 hours: reminder in 15 minutes, still ahead.
        assertEquals(1, plan(booking(2, now.plusMinutes(120))).size)
    }

    @Test fun aBookingThatHasStartedGetsNoReminder() {
        assertEquals(emptyList<PlannedReminder>(), plan(booking(1, now.minusMinutes(5))))
    }

    @Test fun reminderExactlyAtNowIsNotScheduled() {
        // fireAt == now: not strictly in the future.
        assertEquals(emptyList<PlannedReminder>(), plan(booking(1, now.plusMinutes(105))))
    }

    @Test fun onlyTheNextFourteenDaysArePlanned() {
        val p = plan(booking(1, now.plusDays(13)), booking(2, now.plusDays(15)))
        assertEquals(listOf(1L), p.map { it.bookingId })
    }

    @Test fun resultIsSortedAndCapped() {
        val bookings = (1L..60L).map { booking(it, now.plusHours(3 + it)) }.reversed()
        val p = ReminderPlanner.plan(bookings, now, 60, 0, maxCount = 40)
        assertEquals(40, p.size)
        assertEquals((1L..40L).toList(), p.map { it.bookingId })
    }

    @Test fun zeroLeadAndBufferFiresAtTheStart() {
        val start = now.plusHours(5)
        assertEquals(start, plan(booking(1, start), lead = 0, buffer = 0)[0].fireAt)
    }

    @Test fun leadIsClampedAndNegativeBufferIgnored() {
        val start = now.plusDays(3)
        assertEquals(start.minusMinutes(1440), plan(booking(1, start), lead = 99999, buffer = -30)[0].fireAt)
    }

    @Test fun publicLineCarriesOnlyTheTime() {
        assertEquals("Booking at 7:00 PM", ReminderPlanner.publicLine(now, "7:00 PM"))
    }
}
