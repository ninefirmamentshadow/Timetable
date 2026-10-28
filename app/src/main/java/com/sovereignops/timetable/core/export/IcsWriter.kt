package com.sovereignops.timetable.core.export

import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A plain iCalendar (RFC 5545) file of bookings, written by hand: escaping,
 * line folding and CRLF line ends, no dependency, no clock (the caller passes
 * [stamp]). The file is NOT encrypted, so it carries the least that is useful in
 * a calendar app: the alias as the title, the time, the location label and the
 * status. Rates, notes and the screening/deposit flags are left out.
 *
 * Booking times are "floating" local times (no zone, no UTC offset): the app
 * never converts time zones, and a booking at 19:00 shows at 19:00 wherever the
 * file is opened. DTSTAMP is the moment of export and is UTC, as RFC 5545
 * requires.
 *
 * There is no METHOD line: METHOD:PUBLISH would need an ORGANIZER (RFC 5546),
 * and an organizer is an identity. Each UID carries [write]'s `exportId`, a
 * random id kept per install, because booking row ids restart at 1 on every
 * install and a calendar app must not replace one install's event with
 * another's on re-import.
 */
object IcsWriter {

    private val LOCAL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val EXPORT_ID = Regex("[0-9a-f]{16}")

    /**
     * The calendar file for [bookings], stamped [stamp]. Requires at least one
     * booking (a VCALENDAR without a component is not a valid file) and an
     * [exportId] of 16 lowercase hex digits.
     */
    fun write(bookings: List<Booking>, stamp: Instant, exportId: String): String {
        require(bookings.isNotEmpty()) { "a calendar file needs at least one booking" }
        require(EXPORT_ID.matches(exportId)) { "exportId must be 16 lowercase hex digits" }
        val lines = mutableListOf<String>()
        lines += "BEGIN:VCALENDAR"
        lines += "VERSION:2.0"
        lines += "PRODID:-//Timetable//Booking export//EN"
        lines += "CALSCALE:GREGORIAN"
        val dtstamp = UTC.format(stamp)
        for (b in bookings.sortedBy { it.start }) {
            lines += "BEGIN:VEVENT"
            lines += "UID:booking-${b.id}-$exportId@timetable.invalid"
            lines += "DTSTAMP:$dtstamp"
            lines += "DTSTART:${b.start.format(LOCAL)}"
            lines += "DTEND:${b.end.format(LOCAL)}"
            lines += "SUMMARY:${escapeText(b.clientAlias)}"
            if (b.locationLabel.isNotBlank()) lines += "LOCATION:${escapeText(b.locationLabel)}"
            lines += "STATUS:${icsStatus(b.status)}"
            lines += "DESCRIPTION:${escapeText(b.status.label)}"
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString(separator = "") { fold(it) + "\r\n" }
    }

    fun icsStatus(status: BookingStatus): String = when (status) {
        BookingStatus.INQUIRY -> "TENTATIVE"
        BookingStatus.CONFIRMED, BookingStatus.COMPLETED -> "CONFIRMED"
        BookingStatus.CANCELLED, BookingStatus.NO_SHOW -> "CANCELLED"
    }

    /**
     * TEXT escaping (RFC 5545 section 3.3.11): backslash, semicolon and comma get a
     * backslash; a line break becomes the two characters `\n`. A tab is kept
     * (it is TSAFE-CHAR); other control characters are dropped so they cannot
     * break the line structure.
     */
    fun escapeText(text: String): String = buildString(text.length + 8) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' -> append("\\\\")
                c == ';' -> append("\\;")
                c == ',' -> append("\\,")
                c == '\r' -> {
                    append("\\n")
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                }
                c == '\n' -> append("\\n")
                c == '\t' -> append(c)
                c.code < 0x20 || c.code == 0x7f -> Unit
                else -> append(c)
            }
            i++
        }
    }

    /**
     * Fold a logical line so that no physical line exceeds 75 octets (UTF-8),
     * never splitting a multi-byte character. Continuation lines start with one
     * space, which counts toward their 75 octets. Returns the lines joined with
     * CRLF (no trailing CRLF).
     */
    fun fold(line: String): String {
        val out = StringBuilder()
        var octets = 0
        var limit = MAX_OCTETS
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val size = utf8Length(cp)
            if (octets + size > limit) {
                out.append("\r\n ")
                octets = 1
                limit = MAX_OCTETS
            }
            out.appendCodePoint(cp)
            octets += size
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    private fun utf8Length(codePoint: Int): Int = when {
        codePoint < 0x80 -> 1
        codePoint < 0x800 -> 2
        codePoint < 0x10000 -> 3
        else -> 4
    }

    private const val MAX_OCTETS = 75
}
