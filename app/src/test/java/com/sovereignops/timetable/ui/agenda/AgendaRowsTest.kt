package com.sovereignops.timetable.ui.agenda

import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.core.schedule.Agenda
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class AgendaRowsTest {

    private val now = LocalDateTime.of(2026, 10, 20, 12, 0)

    private fun booking(id: Long, start: LocalDateTime, status: BookingStatus) =
        Booking(id, start, 60, "alias$id", Money.dollars(300), status)

    @Test fun rowsAreSectionedAndSkipEmptySections() {
        val bookings = listOf(
            booking(1, now.minusDays(2), BookingStatus.CONFIRMED),
            booking(2, now.plusHours(4), BookingStatus.CONFIRMED),
            booking(3, now.minusDays(1), BookingStatus.COMPLETED),
        )
        val rows = AgendaRows.build(Agenda.sections(bookings, now)) { it.name }

        assertEquals(
            listOf(
                "SectionHeader:NEEDS_REVIEW", "Item:1",
                "SectionHeader:UPCOMING", "DayHeader:2026-10-20", "Item:2",
                "SectionHeader:PAST", "DayHeader:2026-10-19", "Item:3",
            ),
            rows.map(::describe),
        )
    }

    @Test fun noBookingsMeansNoRows() {
        assertEquals(emptyList<AgendaRow>(), AgendaRows.build(Agenda.sections(emptyList(), now)) { it.name })
    }

    @Test fun onlyUpcomingHasNoReviewOrPastHeaders() {
        val rows = AgendaRows.build(
            Agenda.sections(listOf(booking(1, now.plusHours(2), BookingStatus.INQUIRY)), now),
        ) { it.name }
        assertEquals(listOf("SectionHeader:UPCOMING", "DayHeader:2026-10-20", "Item:1"), rows.map(::describe))
    }

    private fun describe(row: AgendaRow) = when (row) {
        is AgendaRow.SectionHeader -> "SectionHeader:${row.section}"
        is AgendaRow.DayHeader -> "DayHeader:${row.date}"
        is AgendaRow.Item -> "Item:${row.booking.id}"
    }
}
