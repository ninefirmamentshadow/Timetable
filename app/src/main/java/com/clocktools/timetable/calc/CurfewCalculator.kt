package com.clocktools.timetable.calc

import java.time.LocalTime

/**
 * The whole app is this calculation. Everything else is a way of getting numbers into it and
 * the result back out.
 *
 * lastSafeBookingStart = min(zone.lastReturnTime, effectiveCurfewDeadline)
 *                        - appointmentMinutes - bufferMinutes - walkToStopMinutes - contingencyMinutes
 *
 * effectiveCurfewDeadline would be curfewTime minus the return-leg travel duration, but the
 * return leg isn't modeled anywhere in this app. Instead, the binding constraint is treated as
 * simply zone.lastReturnTime: the user is expected to only ever enter the last return bus that
 * they've already confirmed (against the printed/online schedule) arrives before curfew. So the
 * "min(...)" in the formula above collapses to zone.lastReturnTime, and curfewTime itself never
 * enters this specific calculation - it only bounds what a valid lastReturnTime entry can be
 * (see ZoneTimeValidation).
 */
object CurfewCalculator {

    fun lastSafeBookingStart(
        lastReturnTime: LocalTime,
        appointmentMinutes: Int,
        bufferMinutes: Int,
        walkToStopMinutes: Int,
        contingencyMinutes: Int
    ): CalculationResult {
        val breakdown = Breakdown(
            deadline = lastReturnTime,
            appointmentMinutes = appointmentMinutes,
            bufferMinutes = bufferMinutes,
            walkToStopMinutes = walkToStopMinutes,
            contingencyMinutes = contingencyMinutes
        )

        // Work in plain minute-of-day integers rather than LocalTime.minusMinutes(), which wraps
        // cyclically past midnight - that would turn "window closed" into a bogus late-night time
        // instead of a negative number we can detect.
        val deadlineMinuteOfDay = lastReturnTime.hour * 60 + lastReturnTime.minute
        val startMinuteOfDay = deadlineMinuteOfDay - breakdown.totalDeductionMinutes

        return if (startMinuteOfDay < 0) {
            CalculationResult.NoSafeStart(breakdown)
        } else {
            CalculationResult.Safe(
                lastSafeStart = LocalTime.of(startMinuteOfDay / 60, startMinuteOfDay % 60),
                breakdown = breakdown
            )
        }
    }
}
