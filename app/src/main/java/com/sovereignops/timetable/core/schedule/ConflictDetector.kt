package com.sovereignops.timetable.core.schedule

import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.core.model.Booking
import java.time.Duration
import java.time.LocalDateTime

/**
 * A reason a candidate booking is problematic. Ordered by severity so a UI can
 * show the worst first. OVERLAP is a hard clash; BUFFER is a travel-time
 * warning (this is an outcall, transit-dependent workflow — two bookings across
 * town with ten minutes between them is a real problem the calendar alone hides);
 * OUTSIDE_AVAILABILITY means the slot is outside every declared working window.
 */
enum class ConflictKind { OVERLAP, BUFFER, OUTSIDE_AVAILABILITY }

data class Conflict(
    val kind: ConflictKind,
    /** The existing booking involved, when the conflict is against one (null for availability). */
    val other: Booking?,
    val message: String,
)

/**
 * Pure scheduling checks over a candidate booking. No Android, no clock, no I/O:
 * every input is passed in so the result is deterministic and testable.
 *
 * @param travelBufferMinutes minimum gap required between the end of one booking
 *   and the start of the next, to cover travel. 0 disables the buffer check.
 */
class ConflictDetector(private val travelBufferMinutes: Int = 0) {

    /**
     * All conflicts for [candidate] against [existing], plus an availability
     * check when [availability] is non-empty. A booking is never checked against
     * itself (matched by id), and cancelled / no-show existing bookings do not
     * occupy the calendar so they never clash.
     */
    fun check(
        candidate: Booking,
        existing: List<Booking>,
        availability: List<AvailabilityWindow> = emptyList(),
    ): List<Conflict> {
        val conflicts = mutableListOf<Conflict>()

        for (other in existing) {
            if (other.id == candidate.id) continue
            if (!other.occupiesCalendar) continue

            if (overlaps(candidate, other)) {
                conflicts += Conflict(
                    ConflictKind.OVERLAP,
                    other,
                    "Overlaps ${other.clientAlias} (${other.start.toLocalTime()}–${other.end.toLocalTime()})",
                )
                continue // an overlap already implies a buffer breach; don't double-report
            }

            if (travelBufferMinutes > 0) {
                val gap = gapMinutes(candidate, other)
                if (gap < travelBufferMinutes) {
                    conflicts += Conflict(
                        ConflictKind.BUFFER,
                        other,
                        "Only ${gap}m from ${other.clientAlias}; ${travelBufferMinutes}m needed for travel",
                    )
                }
            }
        }

        if (availability.isNotEmpty() && candidate.occupiesCalendar) {
            val fits = availability.any { it.accommodates(candidate.start, candidate.durationMinutes) }
            if (!fits) {
                conflicts += Conflict(
                    ConflictKind.OUTSIDE_AVAILABILITY,
                    null,
                    "Outside every declared availability window",
                )
            }
        }

        return conflicts.sortedBy { it.kind.ordinal }
    }

    fun hasHardConflict(
        candidate: Booking,
        existing: List<Booking>,
    ): Boolean = check(candidate, existing).any { it.kind == ConflictKind.OVERLAP }

    private fun overlaps(a: Booking, b: Booking): Boolean =
        a.start.isBefore(b.end) && b.start.isBefore(a.end)

    /** Minutes of clear time between two non-overlapping bookings. */
    private fun gapMinutes(a: Booking, b: Booking): Long {
        val (first, second) = if (a.start.isBefore(b.start)) a to b else b to a
        return Duration.between(first.end, second.start).toMinutes()
    }
}
