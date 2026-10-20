package com.sovereignops.timetable.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class AvailabilityWindowTest {

    private val friday = DayOfWeek.FRIDAY
    private fun window() = AvailabilityWindow(1, friday, LocalTime.of(18, 0), LocalTime.of(23, 0))
    private val aFriday = LocalDateTime.of(2026, 8, 28, 0, 0) // verified Friday

    @Test fun fitsFullyInsideWindow() {
        assertTrue(window().accommodates(aFriday.withHour(19), 60))
    }

    @Test fun startBeforeWindowFails() {
        assertFalse(window().accommodates(aFriday.withHour(17).withMinute(30), 60))
    }

    @Test fun endAfterWindowFails() {
        assertFalse(window().accommodates(aFriday.withHour(22).withMinute(30), 60)) // would end 23:30
    }

    @Test fun endExactlyAtWindowEndFits() {
        assertTrue(window().accommodates(aFriday.withHour(22), 60)) // 22:00–23:00, end == window end
    }

    @Test fun wrongDayFails() {
        val saturday = aFriday.plusDays(1)
        assertFalse(window().accommodates(saturday.withHour(19), 60))
    }

    @Test fun bookingCrossingMidnightFails() {
        val lateWindow = AvailabilityWindow(2, friday, LocalTime.of(18, 0), LocalTime.of(23, 59))
        assertFalse(lateWindow.accommodates(aFriday.withHour(23).withMinute(30), 60)) // ends next day
    }

    @Test fun endBeforeStartRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AvailabilityWindow(3, friday, LocalTime.of(23, 0), LocalTime.of(18, 0))
        }
    }

    // Regression: a window could not end at midnight, so an evening window could
    // stop at 23:59 at best and a booking ending at midnight never fit.
    @Test fun windowMayEndAtMidnight() {
        val w = AvailabilityWindow(4, friday, LocalTime.of(18, 0), LocalTime.MIDNIGHT)
        assertEquals(1440, w.endMinute)
        assertTrue(w.endsAtMidnight)
        assertEquals(LocalTime.MIDNIGHT, w.end)
        assertTrue(w.accommodates(aFriday.withHour(23), 60)) // 23:00–24:00
        assertTrue(w.accommodates(aFriday.withHour(22), 120))
    }

    @Test fun midnightWindowStillRejectsABookingThatRunsPastMidnight() {
        val w = AvailabilityWindow(4, friday, LocalTime.of(18, 0), LocalTime.MIDNIGHT)
        assertFalse(w.accommodates(aFriday.withHour(23).withMinute(30), 60)) // ends 00:30 Saturday
    }

    @Test fun fullDayWindowIsValid() {
        val w = AvailabilityWindow(5, friday, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT)
        assertEquals(0, w.startMinute)
        assertEquals(1440, w.endMinute)
        assertTrue(w.accommodates(aFriday.withHour(0), 1440))
    }

    @Test fun minutesOutsideTheDayAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { AvailabilityWindow(6, friday, -1, 60) }
        assertThrows(IllegalArgumentException::class.java) { AvailabilityWindow(6, friday, 1440, 1441) }
        assertThrows(IllegalArgumentException::class.java) { AvailabilityWindow(6, friday, 60, 1441) }
        assertThrows(IllegalArgumentException::class.java) { AvailabilityWindow(6, friday, 60, 60) }
    }

    @Test fun overlapIsSameDayAndSharedTimeOnly() {
        val a = AvailabilityWindow(1, friday, LocalTime.of(18, 0), LocalTime.of(20, 0))
        assertTrue(a.overlaps(AvailabilityWindow(2, friday, LocalTime.of(19, 0), LocalTime.of(22, 0))))
        assertTrue(a.overlaps(AvailabilityWindow(3, friday, LocalTime.of(17, 0), LocalTime.of(23, 0)))) // contains
        assertFalse(a.overlaps(AvailabilityWindow(4, friday, LocalTime.of(20, 0), LocalTime.of(22, 0)))) // touching
        assertFalse(a.overlaps(AvailabilityWindow(5, DayOfWeek.SATURDAY, LocalTime.of(18, 0), LocalTime.of(20, 0))))
    }
}
