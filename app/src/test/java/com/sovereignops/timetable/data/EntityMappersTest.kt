package com.sovereignops.timetable.data

import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.data.entity.AvailabilityEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class EntityMappersTest {

    // Regression: an availability row ending at 24:00 (1440) crashed the mapper.
    @Test fun midnightEndedWindowRoundTripsThroughTheRow() {
        val row = AvailabilityEntity(id = 3, dayOfWeek = 5, startMinuteOfDay = 18 * 60, endMinuteOfDay = 1440)
        val window = row.toModel()
        assertEquals(AvailabilityWindow(3, DayOfWeek.FRIDAY, 18 * 60, 1440), window)
        assertEquals(row, window.toEntity())
    }
}
