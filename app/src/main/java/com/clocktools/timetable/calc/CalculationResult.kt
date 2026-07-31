package com.clocktools.timetable.calc

import java.time.LocalTime

/**
 * Every deduction that went into a calculation, kept around so the result screen can print
 * the line-by-line math instead of just a number.
 */
data class Breakdown(
    val deadline: LocalTime,
    val appointmentMinutes: Int,
    val bufferMinutes: Int,
    val walkToStopMinutes: Int,
    val contingencyMinutes: Int
) {
    val totalDeductionMinutes: Int
        get() = appointmentMinutes + bufferMinutes + walkToStopMinutes + contingencyMinutes
}

sealed interface CalculationResult {
    val breakdown: Breakdown

    data class Safe(val lastSafeStart: LocalTime, override val breakdown: Breakdown) : CalculationResult

    data class NoSafeStart(override val breakdown: Breakdown) : CalculationResult
}
