package com.clocktools.timetable.calc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ZoneTimeValidationTest {

    @Test
    fun `midnight is rejected`() {
        assertFalse(ZoneTimeValidation.isValidZoneTime(LocalTime.of(0, 0)))
    }

    @Test
    fun `deep night is rejected`() {
        assertFalse(ZoneTimeValidation.isValidZoneTime(LocalTime.of(3, 59)))
    }

    @Test
    fun `early morning boundary is accepted`() {
        assertTrue(ZoneTimeValidation.isValidZoneTime(LocalTime.of(4, 0)))
    }

    @Test
    fun `ordinary evening time is accepted`() {
        assertTrue(ZoneTimeValidation.isValidZoneTime(LocalTime.of(21, 30)))
    }

    @Test
    fun `last minute before midnight is accepted`() {
        assertTrue(ZoneTimeValidation.isValidZoneTime(LocalTime.of(23, 59)))
    }
}
