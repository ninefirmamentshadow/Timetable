package com.sovereignops.timetable.ui

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The one place the UI layer reads the wall clock. The scheduling core never
 * does: it takes `now` as a parameter. Tests swap [clock] for a fixed one so
 * screens and screenshots are deterministic.
 */
object AppClock {
    @Volatile
    var clock: Clock = Clock.systemDefaultZone()

    fun now(): LocalDateTime = LocalDateTime.now(clock)

    fun today(): LocalDate = LocalDate.now(clock)
}
