package com.clocktools.timetable.calc

import java.time.LocalTime

/**
 * Given a hard evening curfew, no zone's last outbound/return bus should ever legitimately fall
 * in the deep-night hours. Rejecting entries there on the Zones screen guards against a
 * fat-fingered post-midnight time silently breaking the same-day minute-of-day math in
 * CurfewCalculator (which never crosses a day boundary).
 */
object ZoneTimeValidation {

    private const val EARLIEST_VALID_HOUR = 4

    fun isValidZoneTime(time: LocalTime): Boolean = time.hour >= EARLIEST_VALID_HOUR
}
