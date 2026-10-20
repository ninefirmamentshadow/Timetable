package com.sovereignops.timetable.core.model

import com.sovereignops.timetable.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class BookingDraftTest {

    private val today = LocalDate.of(2026, 10, 20)

    private fun ok(draft: BookingDraft, id: Long = 0): Booking =
        (draft.toBooking(id) as DraftResult.Ok).booking

    @Test fun blankDraftIsInvalidUntilAliasAndRateAreGiven() {
        val result = BookingDraft.blank(today, 60).toBooking(0) as DraftResult.Invalid
        assertEquals(setOf(DraftError.ALIAS_REQUIRED, DraftError.RATE_INVALID), result.errors)
    }

    @Test fun validDraftBuildsTheBooking() {
        val draft = BookingDraft.blank(today, 90).copy(
            alias = "  Alias A ", rateText = "$125", time = LocalTime.of(20, 30), locationLabel = " Place 1 ",
            notes = " n ", screened = true, status = BookingStatus.CONFIRMED,
        )
        assertEquals(
            Booking(
                7, LocalDateTime.of(2026, 10, 20, 20, 30), 90, "Alias A", Money.dollars(125),
                BookingStatus.CONFIRMED, "Place 1", true, false, "n",
            ),
            ok(draft, 7),
        )
    }

    @Test fun durationMustBePositiveWholeMinutes() {
        val base = BookingDraft.blank(today, 60).copy(alias = "A", rateText = "100")
        for (bad in listOf("", "0", "-5", "abc", "1.5")) {
            val r = base.copy(durationText = bad).toBooking(0) as DraftResult.Invalid
            assertEquals(setOf(DraftError.DURATION_INVALID), r.errors)
        }
    }

    @Test fun negativeRateIsRejected() {
        val r = BookingDraft.blank(today, 60).copy(alias = "A", rateText = "-10").toBooking(0)
        assertTrue(r is DraftResult.Invalid)
    }

    @Test fun roundTripFromBookingKeepsEveryField() {
        val booking = Booking(
            3, LocalDateTime.of(2026, 10, 19, 19, 0), 45, "Alias B", Money(15050), BookingStatus.NO_SHOW,
            "Place 2", true, true, "note",
        )
        assertEquals(booking, ok(BookingDraft.from(booking), 3))
    }
}
