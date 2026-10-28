package com.sovereignops.timetable.core.model

import com.sovereignops.timetable.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BookingTest {

    private val now = LocalDateTime.of(2026, 8, 28, 12, 0)

    private fun booking(start: LocalDateTime, status: BookingStatus) =
        Booking(1, start, 60, "client", Money.dollars(125), status)

    @Test fun needsReviewOnlyForOpenBookingsInThePast() {
        assertTrue(booking(now.minusHours(3), BookingStatus.CONFIRMED).needsReview(now))
        assertTrue(booking(now.minusHours(3), BookingStatus.INQUIRY).needsReview(now))
        assertFalse(booking(now.minusHours(3), BookingStatus.COMPLETED).needsReview(now))
        assertFalse(booking(now.minusHours(3), BookingStatus.NO_SHOW).needsReview(now))
        assertFalse(booking(now.minusHours(3), BookingStatus.CANCELLED).needsReview(now))
        assertFalse(booking(now.plusHours(3), BookingStatus.CONFIRMED).needsReview(now))
    }

    @Test fun endingExactlyNowIsPast() {
        assertTrue(booking(now.minusMinutes(60), BookingStatus.CONFIRMED).needsReview(now))
    }

    @Test fun withStatusChangesOnlyTheStatus() {
        val original = booking(now.minusHours(3), BookingStatus.CONFIRMED)
        val done = original.withStatus(BookingStatus.COMPLETED)
        assertEquals(BookingStatus.COMPLETED, done.status)
        assertEquals(original.copy(status = BookingStatus.COMPLETED), done)
    }

    @Test fun resolutionsAreTheThreeOutcomes() {
        assertEquals(
            listOf(BookingStatus.COMPLETED, BookingStatus.NO_SHOW, BookingStatus.CANCELLED),
            BookingStatus.RESOLUTIONS,
        )
    }
}
