package com.sovereignops.timetable.core.schedule

import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class ConflictDetectorTest {

    private fun booking(
        id: Long,
        start: LocalDateTime,
        minutes: Int,
        status: BookingStatus = BookingStatus.CONFIRMED,
        alias: String = "client$id",
    ) = Booking(
        id = id,
        start = start,
        durationMinutes = minutes,
        clientAlias = alias,
        rate = Money.dollars(125),
        status = status,
    )

    // A Friday.
    private val friday = LocalDateTime.of(2026, 8, 28, 0, 0)

    @Test fun overlapIsDetected() {
        val a = booking(1, friday.withHour(18), 60)
        val candidate = booking(2, friday.withHour(18).withMinute(30), 60)
        val conflicts = ConflictDetector().check(candidate, listOf(a))
        assertEquals(1, conflicts.size)
        assertEquals(ConflictKind.OVERLAP, conflicts[0].kind)
    }

    @Test fun adjacentBookingsDoNotOverlap() {
        val a = booking(1, friday.withHour(18), 60) // 18:00–19:00
        val candidate = booking(2, friday.withHour(19), 60) // 19:00–20:00
        val conflicts = ConflictDetector().check(candidate, listOf(a))
        assertTrue(conflicts.isEmpty())
    }

    @Test fun bufferBreachIsWarnedNotHard() {
        val a = booking(1, friday.withHour(18), 60) // ends 19:00
        val candidate = booking(2, friday.withHour(19).withMinute(15), 60) // 15m gap
        val conflicts = ConflictDetector(travelBufferMinutes = 45).check(candidate, listOf(a))
        assertEquals(1, conflicts.size)
        assertEquals(ConflictKind.BUFFER, conflicts[0].kind)
    }

    @Test fun bufferSatisfiedIsClean() {
        val a = booking(1, friday.withHour(18), 60) // ends 19:00
        val candidate = booking(2, friday.withHour(20), 60) // 60m gap
        val conflicts = ConflictDetector(travelBufferMinutes = 45).check(candidate, listOf(a))
        assertTrue(conflicts.isEmpty())
    }

    @Test fun overlapDoesNotAlsoReportBuffer() {
        val a = booking(1, friday.withHour(18), 60)
        val candidate = booking(2, friday.withHour(18).withMinute(30), 60)
        val conflicts = ConflictDetector(travelBufferMinutes = 45).check(candidate, listOf(a))
        assertEquals(1, conflicts.size)
        assertEquals(ConflictKind.OVERLAP, conflicts[0].kind)
    }

    @Test fun cancelledExistingDoesNotClash() {
        val a = booking(1, friday.withHour(18), 60, status = BookingStatus.CANCELLED)
        val candidate = booking(2, friday.withHour(18).withMinute(30), 60)
        assertTrue(ConflictDetector().check(candidate, listOf(a)).isEmpty())
    }

    @Test fun bookingNeverClashesWithItself() {
        val a = booking(1, friday.withHour(18), 60)
        val conflicts = ConflictDetector(travelBufferMinutes = 45).check(a, listOf(a))
        assertTrue(conflicts.isEmpty())
    }

    @Test fun outsideAvailabilityFlaggedWhenWindowsGiven() {
        val windows = listOf(
            AvailabilityWindow(1, DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(23, 0)),
        )
        val candidate = booking(2, friday.withHour(12), 60) // noon, outside window
        val conflicts = ConflictDetector().check(candidate, emptyList(), windows)
        assertEquals(1, conflicts.size)
        assertEquals(ConflictKind.OUTSIDE_AVAILABILITY, conflicts[0].kind)
    }

    @Test fun insideAvailabilityIsClean() {
        val windows = listOf(
            AvailabilityWindow(1, DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(23, 0)),
        )
        val candidate = booking(2, friday.withHour(19), 60)
        assertTrue(ConflictDetector().check(candidate, emptyList(), windows).isEmpty())
    }

    @Test fun availabilityIgnoredWhenNoWindowsDeclared() {
        val candidate = booking(2, friday.withHour(3), 60) // 3am, but no windows configured
        assertTrue(ConflictDetector().check(candidate, emptyList()).isEmpty())
    }

    @Test fun hardConflictHelperSeesOnlyOverlap() {
        val a = booking(1, friday.withHour(18), 60)
        val overlapCandidate = booking(2, friday.withHour(18).withMinute(30), 60)
        val nearCandidate = booking(3, friday.withHour(19).withMinute(10), 60)
        assertTrue(ConflictDetector(45).hasHardConflict(overlapCandidate, listOf(a)))
        assertFalse(ConflictDetector(45).hasHardConflict(nearCandidate, listOf(a)))
    }

    // Regression: availability at the midnight boundary.
    private val saturday = friday.plusDays(1)
    private fun win(day: DayOfWeek, from: LocalTime, to: LocalTime) = AvailabilityWindow(0, day, from, to)

    @Test fun bookingEndingExactlyAtMidnightFitsAWindowThatEndsAtMidnight() {
        val windows = listOf(win(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.MIDNIGHT))
        val candidate = booking(1, friday.withHour(23), 60) // 23:00–24:00
        assertTrue(ConflictDetector().check(candidate, emptyList(), windows).isEmpty())
    }

    @Test fun bookingCrossingMidnightIsCoveredByAdjacentDaysWindows() {
        val windows = listOf(
            win(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.MIDNIGHT),
            win(DayOfWeek.SATURDAY, LocalTime.MIDNIGHT, LocalTime.of(3, 0)),
        )
        val candidate = booking(1, friday.withHour(23), 120) // 23:00 Fri – 01:00 Sat
        assertTrue(ConflictDetector().check(candidate, emptyList(), windows).isEmpty())
    }

    @Test fun bookingCrossingMidnightWithoutTheNextDaysWindowIsOutsideAvailability() {
        val windows = listOf(win(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.MIDNIGHT))
        val candidate = booking(1, friday.withHour(23), 120)
        val conflicts = ConflictDetector().check(candidate, emptyList(), windows)
        assertEquals(listOf(ConflictKind.OUTSIDE_AVAILABILITY), conflicts.map { it.kind })
    }

    @Test fun touchingWindowsOnOneDayCountAsOneStretch() {
        val windows = listOf(
            win(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(20, 0)),
            win(DayOfWeek.FRIDAY, LocalTime.of(20, 0), LocalTime.of(23, 0)),
        )
        assertTrue(ConflictDetector().check(booking(1, friday.withHour(19), 120), emptyList(), windows).isEmpty())
    }

    @Test fun aGapBetweenWindowsIsNotCovered() {
        val windows = listOf(
            win(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(19, 0)),
            win(DayOfWeek.FRIDAY, LocalTime.of(20, 0), LocalTime.of(23, 0)),
        )
        val conflicts = ConflictDetector().check(booking(1, friday.withHour(18).withMinute(30), 120), emptyList(), windows)
        assertEquals(listOf(ConflictKind.OUTSIDE_AVAILABILITY), conflicts.map { it.kind })
    }

    @Test fun bookingOnTheNextDayAfterMidnightUsesThatDaysWindow() {
        val windows = listOf(win(DayOfWeek.SATURDAY, LocalTime.MIDNIGHT, LocalTime.of(3, 0)))
        assertTrue(ConflictDetector().check(booking(1, saturday.withHour(1), 60), emptyList(), windows).isEmpty())
    }
}
