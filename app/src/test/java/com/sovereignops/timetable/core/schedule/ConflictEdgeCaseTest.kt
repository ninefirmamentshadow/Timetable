package com.sovereignops.timetable.core.schedule

import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The edge cases the provider-suite audit (2026-10-27) asked for, beyond
 * ConflictDetectorTest: touching intervals in both orders, zero and tiny buffers,
 * overnight bookings, wall-clock (DST) semantics, reordering, changed
 * availability, cancelled / no-show on either side, and invalid input.
 */
class ConflictEdgeCaseTest {

    private fun booking(id: Long, start: LocalDateTime, minutes: Int, status: BookingStatus = BookingStatus.CONFIRMED) =
        Booking(id, start, minutes, "Alias $id", Money.dollars(50), status)

    private fun kinds(c: List<Conflict>) = c.map { it.kind }

    private val fri = LocalDateTime.of(2026, 10, 23, 0, 0) // a Friday
    private fun at(h: Int, m: Int = 0, day: LocalDateTime = fri) = day.withHour(h).withMinute(m)

    // ------------------------------------------------------------- touching

    @Test fun touchingBookingsDoNotOverlapInEitherOrder() {
        val a = booking(1, at(18), 60)
        val b = booking(2, at(19), 60)
        assertEquals(emptyList<ConflictKind>(), kinds(ConflictDetector(0).check(a, listOf(b))))
        assertEquals(emptyList<ConflictKind>(), kinds(ConflictDetector(0).check(b, listOf(a))))
    }

    @Test fun oneMinuteOfSharedTimeIsAnOverlapInEitherOrder() {
        val a = booking(1, at(18), 61)
        val b = booking(2, at(19), 60)
        assertEquals(listOf(ConflictKind.OVERLAP), kinds(ConflictDetector(0).check(a, listOf(b))))
        assertEquals(listOf(ConflictKind.OVERLAP), kinds(ConflictDetector(0).check(b, listOf(a))))
    }

    @Test fun aBookingInsideAnotherIsAnOverlap() {
        val outer = booking(1, at(18), 180)
        val inner = booking(2, at(19), 30)
        assertEquals(listOf(ConflictKind.OVERLAP), kinds(ConflictDetector(45).check(inner, listOf(outer))))
        assertEquals(listOf(ConflictKind.OVERLAP), kinds(ConflictDetector(45).check(outer, listOf(inner))))
    }

    // -------------------------------------------------------------- buffers

    @Test fun zeroBufferNeverWarnsForTouchingBookings() {
        val a = booking(1, at(18), 60)
        assertTrue(ConflictDetector(0).check(booking(2, at(19), 30), listOf(a)).isEmpty())
    }

    @Test fun aGapExactlyEqualToTheBufferIsEnough() {
        val a = booking(1, at(18), 60)
        assertTrue(ConflictDetector(45).check(booking(2, at(19, 45), 30), listOf(a)).isEmpty())
        assertEquals(listOf(ConflictKind.BUFFER), kinds(ConflictDetector(45).check(booking(2, at(19, 44), 30), listOf(a))))
    }

    @Test fun theBufferAppliesOnBothSidesOfTheCandidate() {
        val before = booking(1, at(17), 60) // ends 18:00
        val after = booking(3, at(20), 60) // starts 20:00
        val candidate = booking(2, at(18, 10), 100) // 18:10–19:50
        val c = ConflictDetector(30).check(candidate, listOf(before, after))
        assertEquals(listOf(ConflictKind.BUFFER, ConflictKind.BUFFER), kinds(c))
        assertEquals(setOf(1L, 3L), c.map { it.other!!.id }.toSet())
    }

    @Test fun aNegativeBufferBehavesLikeNone() {
        val a = booking(1, at(18), 60)
        assertTrue(ConflictDetector(-10).check(booking(2, at(19), 30), listOf(a)).isEmpty())
    }

    @Test fun reorderingTheExistingListChangesNothing() {
        val list = listOf(booking(1, at(17), 50), booking(3, at(20), 60), booking(4, at(18, 30), 30))
        val candidate = booking(2, at(18, 10), 100)
        val d = ConflictDetector(30)
        assertEquals(d.check(candidate, list), d.check(candidate, list.reversed()))
        assertEquals(d.check(candidate, list), d.check(candidate, list.shuffled(java.util.Random(7))))
    }

    @Test fun overlapsAreReportedBeforeBuffersAndAvailability() {
        val windows = listOf(AvailabilityWindow(1, DayOfWeek.FRIDAY, LocalTime.of(20, 0), LocalTime.of(23, 0)))
        val c = ConflictDetector(30).check(
            booking(9, at(18, 30), 60), listOf(booking(1, at(19), 30), booking(2, at(17), 70)), windows, // 2 ends 18:10: 20m gap
        )
        assertEquals(listOf(ConflictKind.OVERLAP, ConflictKind.BUFFER, ConflictKind.OUTSIDE_AVAILABILITY), kinds(c))
    }

    // ------------------------------------------------------------ overnight

    @Test fun anOvernightBookingOverlapsTheNextMorning() {
        val late = booking(1, at(23), 120) // 23:00–01:00 Saturday
        val early = booking(2, at(0, 30, fri.plusDays(1)), 60)
        assertEquals(listOf(ConflictKind.OVERLAP), kinds(ConflictDetector(0).check(early, listOf(late))))
    }

    @Test fun theBufferIsMeasuredAcrossMidnight() {
        val late = booking(1, at(23), 60) // ends 00:00 Saturday
        val early = booking(2, at(0, 20, fri.plusDays(1)), 60)
        val c = ConflictDetector(30).check(early, listOf(late))
        assertEquals(listOf(ConflictKind.BUFFER), kinds(c))
        assertTrue(c.single().message.startsWith("Only 20m"))
    }

    // ---------------------------------------------- daylight saving (wall clock)

    // Bookings are wall-clock times; the app never converts time zones. On the night
    // the clocks go back (in many zones, 2026-11-01), an hour-long booking from 01:30
    // is 01:30–02:30 on the calendar whatever the zone does.
    @Test fun conflictsUseWallClockTimesAcrossADaylightSavingChange() {
        val night = LocalDateTime.of(2026, 11, 1, 1, 30)
        val a = booking(1, night, 60) // 01:30–02:30 wall clock
        assertTrue(ConflictDetector(0).check(booking(2, night.plusMinutes(60), 30), listOf(a)).isEmpty())
        assertEquals(listOf(ConflictKind.OVERLAP), kinds(ConflictDetector(0).check(booking(2, night.plusMinutes(59), 30), listOf(a))))
    }

    // ------------------------------------------------------------- statuses

    @Test fun noShowAndCancelledExistingBookingsNeverClashOrWarn() {
        for (status in listOf(BookingStatus.NO_SHOW, BookingStatus.CANCELLED)) {
            val gone = booking(1, at(18), 60, status)
            assertTrue(ConflictDetector(45).check(booking(2, at(18), 60), listOf(gone)).isEmpty())
            assertTrue(ConflictDetector(45).check(booking(2, at(19), 30), listOf(gone)).isEmpty())
        }
    }

    @Test fun inquiriesAndCompletedBookingsStillHoldTheirSlot() {
        for (status in listOf(BookingStatus.INQUIRY, BookingStatus.COMPLETED)) {
            val held = booking(1, at(18), 60, status)
            assertEquals(listOf(ConflictKind.OVERLAP), kinds(ConflictDetector(0).check(booking(2, at(18, 30), 60), listOf(held))))
        }
    }

    // Regression: editing a cancelled or no-show booking (say, to add a note) warned
    // that it overlapped whatever now holds that time, though it holds no slot itself.
    @Test fun aCancelledOrNoShowCandidateRaisesNoConflicts() {
        val windows = listOf(AvailabilityWindow(1, DayOfWeek.FRIDAY, LocalTime.of(20, 0), LocalTime.of(23, 0)))
        val holder = booking(1, at(18), 60)
        for (status in listOf(BookingStatus.CANCELLED, BookingStatus.NO_SHOW)) {
            assertTrue(status.name, ConflictDetector(45).check(booking(2, at(18), 60, status), listOf(holder), windows).isEmpty())
        }
    }

    // ------------------------------------------------------- availability

    @Test fun changingTheWindowsChangesTheAnswerForTheSameBooking() {
        val b = booking(1, at(21), 60)
        val before = listOf(AvailabilityWindow(1, DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(23, 0)))
        val after = listOf(AvailabilityWindow(1, DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(21, 30)))
        assertTrue(ConflictDetector(0).check(b, emptyList(), before).isEmpty())
        assertEquals(listOf(ConflictKind.OUTSIDE_AVAILABILITY), kinds(ConflictDetector(0).check(b, emptyList(), after)))
    }

    @Test fun aWindowOnAnotherWeekdayDoesNotCover() {
        val saturday = listOf(AvailabilityWindow(1, DayOfWeek.SATURDAY, LocalTime.of(0, 0), LocalTime.MIDNIGHT))
        assertEquals(listOf(ConflictKind.OUTSIDE_AVAILABILITY), kinds(ConflictDetector(0).check(booking(1, at(12), 60), emptyList(), saturday)))
    }

    // -------------------------------------------------------- invalid input

    @Test fun aBookingCannotHaveAZeroOrNegativeDuration() {
        assertThrows(IllegalArgumentException::class.java) { booking(1, at(18), 0) }
        assertThrows(IllegalArgumentException::class.java) { booking(1, at(18), -30) }
    }

    @Test fun windowsMustBeInsideOneDayAndForwards() {
        assertThrows(IllegalArgumentException::class.java) { AvailabilityWindow(1, DayOfWeek.FRIDAY, 1200, 1100) }
        assertThrows(IllegalArgumentException::class.java) { AvailabilityWindow(1, DayOfWeek.FRIDAY, -1, 60) }
        assertThrows(IllegalArgumentException::class.java) { AvailabilityWindow(1, DayOfWeek.FRIDAY, 60, 1441) }
        assertThrows(IllegalArgumentException::class.java) { AvailabilityWindow(1, DayOfWeek.FRIDAY, 600, 600) }
    }
}
