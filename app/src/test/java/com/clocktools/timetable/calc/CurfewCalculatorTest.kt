package com.clocktools.timetable.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class CurfewCalculatorTest {

    @Test
    fun `typical evening produces the expected safe start`() {
        // 21:00 last return - (60 + 20 + 15 + 15) minutes = 19:10
        val result = CurfewCalculator.lastSafeBookingStart(
            lastReturnTime = LocalTime.of(21, 0),
            appointmentMinutes = 60,
            bufferMinutes = 20,
            walkToStopMinutes = 15,
            contingencyMinutes = 15
        )

        assertTrue(result is CalculationResult.Safe)
        assertEquals(LocalTime.of(19, 10), (result as CalculationResult.Safe).lastSafeStart)
    }

    @Test
    fun `result minute is exactly preserved, not rounded`() {
        val result = CurfewCalculator.lastSafeBookingStart(
            lastReturnTime = LocalTime.of(21, 7),
            appointmentMinutes = 30,
            bufferMinutes = 20,
            walkToStopMinutes = 15,
            contingencyMinutes = 15
        )

        assertTrue(result is CalculationResult.Safe)
        assertEquals(LocalTime.of(19, 47), (result as CalculationResult.Safe).lastSafeStart)
    }

    @Test
    fun `an appointment longer than the available window yields NoSafeStart, never a negative time`() {
        val result = CurfewCalculator.lastSafeBookingStart(
            lastReturnTime = LocalTime.of(6, 0),
            appointmentMinutes = 400,
            bufferMinutes = 20,
            walkToStopMinutes = 15,
            contingencyMinutes = 15
        )

        assertTrue(result is CalculationResult.NoSafeStart)
    }

    @Test
    fun `deductions exactly consuming the window land on midnight, not NoSafeStart`() {
        // 07:00 (420 min) - (300 + 60 + 40 + 20) = 0 -> exactly 00:00, still a valid Safe result.
        val result = CurfewCalculator.lastSafeBookingStart(
            lastReturnTime = LocalTime.of(7, 0),
            appointmentMinutes = 300,
            bufferMinutes = 60,
            walkToStopMinutes = 40,
            contingencyMinutes = 20
        )

        assertTrue(result is CalculationResult.Safe)
        assertEquals(LocalTime.of(0, 0), (result as CalculationResult.Safe).lastSafeStart)
    }

    @Test
    fun `one minute past the window flips to NoSafeStart`() {
        // Same totals as above plus one extra minute of appointment length crosses the line.
        val result = CurfewCalculator.lastSafeBookingStart(
            lastReturnTime = LocalTime.of(7, 0),
            appointmentMinutes = 301,
            bufferMinutes = 60,
            walkToStopMinutes = 40,
            contingencyMinutes = 20
        )

        assertTrue(result is CalculationResult.NoSafeStart)
    }

    @Test
    fun `zero walk time is just another number, no special casing`() {
        val withWalk = CurfewCalculator.lastSafeBookingStart(
            lastReturnTime = LocalTime.of(21, 0),
            appointmentMinutes = 60,
            bufferMinutes = 20,
            walkToStopMinutes = 15,
            contingencyMinutes = 15
        ) as CalculationResult.Safe

        val withoutWalk = CurfewCalculator.lastSafeBookingStart(
            lastReturnTime = LocalTime.of(21, 0),
            appointmentMinutes = 60,
            bufferMinutes = 20,
            walkToStopMinutes = 0,
            contingencyMinutes = 15
        ) as CalculationResult.Safe

        // Removing exactly 15 minutes of walk time should move the safe start 15 minutes later.
        val diffMinutes = (withoutWalk.lastSafeStart.toSecondOfDay() - withWalk.lastSafeStart.toSecondOfDay()) / 60
        assertEquals(15, diffMinutes)
    }

    @Test
    fun `breakdown carries every input through unchanged for the auditable line-by-line display`() {
        val result = CurfewCalculator.lastSafeBookingStart(
            lastReturnTime = LocalTime.of(21, 0),
            appointmentMinutes = 60,
            bufferMinutes = 20,
            walkToStopMinutes = 15,
            contingencyMinutes = 15
        )

        val breakdown = result.breakdown
        assertEquals(LocalTime.of(21, 0), breakdown.deadline)
        assertEquals(60, breakdown.appointmentMinutes)
        assertEquals(20, breakdown.bufferMinutes)
        assertEquals(15, breakdown.walkToStopMinutes)
        assertEquals(15, breakdown.contingencyMinutes)
        assertEquals(110, breakdown.totalDeductionMinutes)
    }

    @Test
    fun `a NoSafeStart result still carries the breakdown that produced it`() {
        val result = CurfewCalculator.lastSafeBookingStart(
            lastReturnTime = LocalTime.of(6, 0),
            appointmentMinutes = 400,
            bufferMinutes = 20,
            walkToStopMinutes = 15,
            contingencyMinutes = 15
        )

        assertEquals(450, result.breakdown.totalDeductionMinutes)
    }

    @Test
    fun `far in excess of the window is still NoSafeStart, no crash from an out-of-range LocalTime`() {
        val result = CurfewCalculator.lastSafeBookingStart(
            lastReturnTime = LocalTime.of(0, 30),
            appointmentMinutes = 600,
            bufferMinutes = 60,
            walkToStopMinutes = 30,
            contingencyMinutes = 30
        )

        assertTrue(result is CalculationResult.NoSafeStart)
    }
}
