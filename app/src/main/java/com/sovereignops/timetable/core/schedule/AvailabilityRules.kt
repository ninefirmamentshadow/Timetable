package com.sovereignops.timetable.core.schedule

import com.sovereignops.timetable.core.model.AvailabilityWindow

/** Pure checks on the set of availability windows. */
object AvailabilityRules {

    /**
     * The existing window [candidate] would overlap, or null. A window being
     * edited is not compared with itself (matched by id); windows that only touch
     * (one ends when the next starts) do not overlap.
     */
    fun overlapping(candidate: AvailabilityWindow, existing: List<AvailabilityWindow>): AvailabilityWindow? =
        existing.firstOrNull { it.id != candidate.id && it.overlaps(candidate) }
}
