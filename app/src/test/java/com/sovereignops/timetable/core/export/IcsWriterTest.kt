package com.sovereignops.timetable.core.export

import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class IcsWriterTest {

    private val stamp = LocalDateTime.of(2026, 10, 21, 9, 30, 15)

    private fun booking(
        id: Long = 1,
        alias: String = "Alias A",
        start: LocalDateTime = LocalDateTime.of(2026, 10, 21, 19, 0),
        minutes: Int = 60,
        status: BookingStatus = BookingStatus.CONFIRMED,
        location: String = "",
        notes: String = "secret note",
    ) = Booking(id, start, minutes, alias, Money.dollars(125), status, location, true, true, notes)

    private fun physicalLines(ics: String) = ics.split("\r\n").dropLast(1) // trailing CRLF leaves one empty

    @Test fun structureAndCrLfLineEnds() {
        val ics = IcsWriter.write(listOf(booking()), stamp)
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertFalse("no bare LF", ics.replace("\r\n", "").contains('\n'))
        val lines = physicalLines(ics)
        assertEquals("BEGIN:VCALENDAR", lines.first())
        assertEquals("VERSION:2.0", lines[1])
        assertTrue(lines.contains("BEGIN:VEVENT") && lines.contains("END:VEVENT"))
        assertEquals("END:VCALENDAR", lines.last())
    }

    @Test fun eventFieldsAreFloatingLocalTimes() {
        val lines = physicalLines(IcsWriter.write(listOf(booking(id = 7, location = "Place 1")), stamp))
        assertTrue(lines.contains("UID:booking-7@timetable.invalid"))
        assertTrue(lines.contains("DTSTAMP:20261021T093015"))
        assertTrue(lines.contains("DTSTART:20261021T190000"))
        assertTrue(lines.contains("DTEND:20261021T200000"))
        assertTrue(lines.contains("SUMMARY:Alias A"))
        assertTrue(lines.contains("LOCATION:Place 1"))
        assertTrue(lines.contains("STATUS:CONFIRMED"))
    }

    @Test fun bookingCrossingMidnightEndsTheNextDay() {
        val b = booking(start = LocalDateTime.of(2026, 10, 21, 23, 30), minutes = 90)
        assertTrue(physicalLines(IcsWriter.write(listOf(b), stamp)).contains("DTEND:20261022T010000"))
    }

    @Test fun privateFieldsAreLeftOut() {
        val ics = IcsWriter.write(listOf(booking(notes = "do not export this")), stamp)
        assertFalse(ics.contains("do not export this"))
        assertFalse(ics.contains("125"))
        assertFalse(ics.contains("LOCATION")) // blank label: no property at all
    }

    @Test fun statusMapping() {
        assertEquals("TENTATIVE", IcsWriter.icsStatus(BookingStatus.INQUIRY))
        assertEquals("CONFIRMED", IcsWriter.icsStatus(BookingStatus.CONFIRMED))
        assertEquals("CONFIRMED", IcsWriter.icsStatus(BookingStatus.COMPLETED))
        assertEquals("CANCELLED", IcsWriter.icsStatus(BookingStatus.CANCELLED))
        assertEquals("CANCELLED", IcsWriter.icsStatus(BookingStatus.NO_SHOW))
    }

    @Test fun eventsAreSortedByStart() {
        val later = booking(id = 1, start = LocalDateTime.of(2026, 10, 22, 19, 0))
        val earlier = booking(id = 2, start = LocalDateTime.of(2026, 10, 21, 19, 0))
        val uids = physicalLines(IcsWriter.write(listOf(later, earlier), stamp)).filter { it.startsWith("UID:") }
        assertEquals(listOf("UID:booking-2@timetable.invalid", "UID:booking-1@timetable.invalid"), uids)
    }

    @Test fun emptyListIsAnEmptyCalendar() {
        val lines = physicalLines(IcsWriter.write(emptyList(), stamp))
        assertFalse(lines.contains("BEGIN:VEVENT"))
        assertEquals("END:VCALENDAR", lines.last())
    }

    // --- escaping -----------------------------------------------------------

    @Test fun textEscaping() {
        assertEquals("a\\,b\\;c\\\\d", IcsWriter.escapeText("a,b;c\\d"))
        assertEquals("line1\\nline2", IcsWriter.escapeText("line1\nline2"))
        assertEquals("line1\\nline2", IcsWriter.escapeText("line1\r\nline2"))
        assertEquals("a\\nb", IcsWriter.escapeText("a\rb"))
        assertEquals("ab", IcsWriter.escapeText("a\u0000\u0007b")) // control characters dropped
        assertEquals("plain ünï ✓", IcsWriter.escapeText("plain ünï ✓"))
    }

    @Test fun awkwardAliasCannotInjectProperties() {
        val ics = IcsWriter.write(listOf(booking(alias = "x\r\nATTENDEE:mailto:evil@example.invalid")), stamp)
        assertFalse(physicalLines(ics).any { it.startsWith("ATTENDEE") })
        assertTrue(physicalLines(ics).any { it.startsWith("SUMMARY:x\\nATTENDEE") })
    }

    // --- folding ------------------------------------------------------------

    @Test fun shortLinesAreNotFolded() {
        assertEquals("SUMMARY:short", IcsWriter.fold("SUMMARY:short"))
    }

    @Test fun lineOfExactly75OctetsIsNotFolded() {
        val line = "S:" + "a".repeat(73)
        assertEquals(75, line.length)
        assertEquals(line, IcsWriter.fold(line))
    }

    @Test fun longLinesFoldAtSeventyFiveOctetsWithALeadingSpace() {
        val line = "DESCRIPTION:" + "x".repeat(200)
        val folded = IcsWriter.fold(line).split("\r\n")
        assertEquals(75, folded[0].length)
        assertTrue(folded.drop(1).all { it.startsWith(" ") })
        assertTrue(folded.all { it.toByteArray(Charsets.UTF_8).size <= 75 })
        // Unfolding (drop CRLF + one space) restores the line exactly.
        assertEquals(line, IcsWriter.fold(line).replace("\r\n ", ""))
    }

    @Test fun foldingNeverSplitsAMultiByteCharacter() {
        // 2-byte (ü), 3-byte (✓) and 4-byte (emoji) characters at awkward offsets.
        for (prefix in 60..80) {
            val line = "S:" + "a".repeat(prefix) + "ü✓😀".repeat(10)
            val folded = IcsWriter.fold(line).split("\r\n")
            assertTrue("prefix $prefix", folded.all { it.toByteArray(Charsets.UTF_8).size <= 75 })
            assertEquals("prefix $prefix", line, IcsWriter.fold(line).replace("\r\n ", ""))
            // Every physical line is valid text: encoding to UTF-8 and back is lossless
            // (a lone surrogate from a split pair would come back as "?").
            assertTrue(folded.all { String(it.toByteArray(Charsets.UTF_8), Charsets.UTF_8) == it })
        }
    }

    @Test fun foldedLongAliasInAFullFileKeepsEveryLineUnder75Octets() {
        val ics = IcsWriter.write(listOf(booking(alias = "Äliäs, with; odd\\chars ✓ ".repeat(8), location = "Pläce ".repeat(20))), stamp)
        assertTrue(ics.split("\r\n").all { it.toByteArray(Charsets.UTF_8).size <= 75 })
    }
}
