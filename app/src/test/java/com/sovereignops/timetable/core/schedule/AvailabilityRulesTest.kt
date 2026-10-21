package com.sovereignops.timetable.core.schedule

import com.sovereignops.timetable.core.model.AvailabilityWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class AvailabilityRulesTest {

    private fun win(id: Long, day: DayOfWeek, from: Int, to: Int) =
        AvailabilityWindow(id, day, LocalTime.of(from, 0), if (to == 24) LocalTime.MIDNIGHT else LocalTime.of(to, 0))

    private val existing = listOf(win(1, DayOfWeek.FRIDAY, 18, 22), win(2, DayOfWeek.SATURDAY, 18, 24))

    @Test fun overlapWithAnExistingWindowIsReported() {
        val hit = AvailabilityRules.overlapping(win(0, DayOfWeek.FRIDAY, 21, 23), existing)
        assertEquals(1L, hit?.id)
    }

    @Test fun touchingAndOtherDaysAreFine() {
        assertNull(AvailabilityRules.overlapping(win(0, DayOfWeek.FRIDAY, 22, 24), existing))
        assertNull(AvailabilityRules.overlapping(win(0, DayOfWeek.SUNDAY, 18, 22), existing))
    }

    @Test fun editingAWindowDoesNotOverlapItself() {
        assertNull(AvailabilityRules.overlapping(win(1, DayOfWeek.FRIDAY, 19, 23), existing))
    }

    @Test fun editedWindowStillCannotOverlapAnotherOne() {
        val both = existing + win(3, DayOfWeek.FRIDAY, 23, 24)
        assertEquals(3L, AvailabilityRules.overlapping(win(1, DayOfWeek.FRIDAY, 19, 24), both)?.id)
    }
}
