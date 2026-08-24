package com.sovereignops.timetable.core.model

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
}
