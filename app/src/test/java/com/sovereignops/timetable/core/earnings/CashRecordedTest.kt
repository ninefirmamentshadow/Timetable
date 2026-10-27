package com.sovereignops.timetable.core.earnings

import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingDraft
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.core.model.DraftError
import com.sovereignops.timetable.core.model.DraftResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** A completed booking's nominal value is not cash; only a recorded amount received is. */
class CashRecordedTest {

    private val now = LocalDateTime.of(2026, 10, 27, 12, 0)

    private fun booking(id: Long, day: Int, rate: Long, status: BookingStatus, received: Long? = null) = Booking(
        id, LocalDateTime.of(2026, 10, day, 18, 0), 60, "Alias $id", Money.dollars(rate), status,
        amountReceived = received?.let { Money.dollars(it) },
    )

    @Test fun completedValueAndCashAreSeparateFigures() {
        val s = Earnings.summarize(
            listOf(
                booking(1, 20, 125, BookingStatus.COMPLETED, received = 125),
                booking(2, 21, 125, BookingStatus.COMPLETED), // completed, nothing recorded yet
                booking(3, 22, 100, BookingStatus.COMPLETED, received = 75), // paid less than booked
            ),
            now,
        )
        assertEquals(Money.dollars(350), s.earned) // booked value of completed bookings
        assertEquals(Money.dollars(200), s.cashRecorded) // what was actually received
        assertEquals(2, s.cashRecordedCount)
        assertEquals(1, s.completedWithoutPaymentCount)
    }

    @Test fun aPaymentOnANoShowOrCancelledBookingIsCashButNotCompletedValue() {
        val s = Earnings.summarize(
            listOf(
                booking(1, 20, 125, BookingStatus.NO_SHOW, received = 50),
                booking(2, 21, 125, BookingStatus.CANCELLED, received = 25),
            ),
            now,
        )
        assertEquals(Money.ZERO, s.earned)
        assertEquals(Money.dollars(75), s.cashRecorded)
        assertEquals(0, s.completedWithoutPaymentCount)
    }

    @Test fun aProjectedBookingIsNeverCashEvenWhenConfirmed() {
        val s = Earnings.summarize(listOf(booking(1, 30, 125, BookingStatus.CONFIRMED)), now)
        assertEquals(Money.dollars(125), s.projected)
        assertEquals(Money.ZERO, s.cashRecorded)
        assertEquals(0, s.cashRecordedCount)
    }

    @Test fun zeroReceivedIsRecordedAndIsNotTheSameAsUnrecorded() {
        val s = Earnings.summarize(listOf(booking(1, 20, 125, BookingStatus.COMPLETED, received = 0)), now)
        assertEquals(Money.ZERO, s.cashRecorded)
        assertEquals(1, s.cashRecordedCount)
        assertEquals(0, s.completedWithoutPaymentCount)
    }

    @Test fun aNegativeAmountReceivedIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { booking(1, 20, 125, BookingStatus.COMPLETED, received = -1) }
    }

    // ------------------------------------------------------------ the editor form

    private val blank = BookingDraft.blank(LocalDate.of(2026, 10, 27), 60).copy(alias = "A", rateText = "125")

    @Test fun aBlankReceivedFieldMeansNotRecorded() {
        for (text in listOf(null, "", "   ")) {
            val ok = blank.copy(receivedText = text).toBooking(0) as DraftResult.Ok
            assertNull(ok.booking.amountReceived)
        }
    }

    @Test fun aTypedAmountIsRecordedAndRoundTripsThroughTheForm() {
        val ok = blank.copy(receivedText = "$75.50").toBooking(0) as DraftResult.Ok
        assertEquals(Money(7550), ok.booking.amountReceived)
        assertEquals(ok.booking, (BookingDraft.from(ok.booking).toBooking(0) as DraftResult.Ok).booking)
    }

    @Test fun anUnreadableOrNegativeAmountIsAFormError() {
        for (text in listOf("abc", "-5")) {
            val invalid = blank.copy(receivedText = text).toBooking(0) as DraftResult.Invalid
            assertTrue(text, DraftError.RECEIVED_INVALID in invalid.errors)
        }
    }
}
