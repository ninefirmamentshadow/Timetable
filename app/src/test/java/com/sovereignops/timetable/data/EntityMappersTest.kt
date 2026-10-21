package com.sovereignops.timetable.data

import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.data.entity.AvailabilityEntity
import com.sovereignops.timetable.data.entity.BookingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class EntityMappersTest {

    // Regression: an availability row ending at 24:00 (1440) crashed the mapper.
    @Test fun midnightEndedWindowRoundTripsThroughTheRow() {
        val row = AvailabilityEntity(id = 3, dayOfWeek = 5, startMinuteOfDay = 18 * 60, endMinuteOfDay = 1440)
        val window = row.toModel()
        assertEquals(AvailabilityWindow(3, DayOfWeek.FRIDAY, 18 * 60, 1440), window)
        assertEquals(row, window.toEntity())
    }

    private fun row(
        status: String = "CONFIRMED",
        duration: Int = 60,
        rateCents: Long = 30000,
        start: Long = 1_792_000_000,
    ) = BookingEntity(1, start, duration, "Alias A", rateCents, status, "", false, false, "")

    @Test fun aGoodBookingRowIsRead() {
        assertTrue(row().toModelResult().isSuccess)
    }

    // Regression: one bad row threw inside the list's flow and crashed the screen.
    @Test fun badBookingRowsAreFailuresNotExceptions() {
        assertTrue(row(status = "BOGUS").toModelResult().isFailure)
        assertTrue(row(status = "").toModelResult().isFailure)
        assertTrue(row(duration = 0).toModelResult().isFailure)
        assertTrue(row(duration = -30).toModelResult().isFailure)
        assertTrue(row(rateCents = -1).toModelResult().isFailure)
        assertTrue(row(start = Long.MAX_VALUE).toModelResult().isFailure)
        assertTrue(row(start = Long.MIN_VALUE).toModelResult().isFailure)
    }

    @Test fun failureIsAnUnreadableRowError() {
        val error = row(status = "BOGUS").toModelResult().exceptionOrNull()
        assertTrue(error is UnreadableRow)
        assertTrue(row(start = Long.MAX_VALUE).toModelResult().exceptionOrNull() is UnreadableRow)
    }

    @Test fun badAvailabilityRowsAreFailures() {
        fun win(day: Int = 5, start: Int = 60, end: Int = 120) = AvailabilityEntity(1, day, start, end)
        assertTrue(win().toModelResult().isSuccess)
        assertTrue(win(day = 0).toModelResult().isFailure)
        assertTrue(win(day = 8).toModelResult().isFailure)
        assertTrue(win(start = -5).toModelResult().isFailure)
        assertTrue(win(end = 1441).toModelResult().isFailure)
        assertTrue(win(start = 120, end = 60).toModelResult().isFailure)
        assertTrue(win(start = 60, end = 60).toModelResult().isFailure)
    }

    @Test fun loadedSkipsAndCountsBadRows() {
        val loaded = Loaded.from(listOf(row(), row(status = "BOGUS"), row(duration = 0), row())) { it.toModelResult() }
        assertEquals(2, loaded.items.size)
        assertEquals(2, loaded.unreadable)
    }
}
