package com.sovereignops.timetable.core.schedule

import com.sovereignops.timetable.core.model.AvailabilityWindow
import java.time.LocalDateTime

/**
 * Is a booking inside the declared availability? Pure, no clock.
 *
 * The booking is cut at each midnight and every day's part must lie inside that
 * weekday's windows. Windows that touch (18:00–20:00 and 20:00–23:00) count as
 * one stretch, so a booking may span the join, and a booking that crosses
 * midnight is covered when one day's window runs to 24:00 and the next day's
 * starts at 00:00.
 */
object AvailabilityCoverage {

    fun covers(windows: List<AvailabilityWindow>, start: LocalDateTime, durationMinutes: Int): Boolean {
        var remaining = durationMinutes
        var date = start.toLocalDate()
        var from = start.hour * 60 + start.minute
        while (remaining > 0) {
            val to = minOf(AvailabilityWindow.MINUTES_PER_DAY, from + remaining)
            val stretches = mergedStretches(windows.filter { it.dayOfWeek == date.dayOfWeek })
            if (stretches.none { from >= it.first && to <= it.second }) return false
            remaining -= to - from
            date = date.plusDays(1)
            from = 0
        }
        return true
    }

    /** Sorted, non-overlapping [start, end) stretches with touching windows joined. */
    private fun mergedStretches(windows: List<AvailabilityWindow>): List<Pair<Int, Int>> {
        val merged = mutableListOf<Pair<Int, Int>>()
        for (w in windows.sortedBy { it.startMinute }) {
            val last = merged.lastOrNull()
            if (last != null && w.startMinute <= last.second) {
                merged[merged.lastIndex] = last.first to maxOf(last.second, w.endMinute)
            } else {
                merged += w.startMinute to w.endMinute
            }
        }
        return merged
    }
}
