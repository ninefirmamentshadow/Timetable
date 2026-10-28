package com.sovereignops.timetable.ui

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The one place the UI layer reads the wall clock. The scheduling core never
 * does: it takes `now` as a parameter. Tests set [clock] to a fixed one so
 * screens and screenshots are deterministic; null (the default) means the
 * system clock in the device's current zone, read on every call so a
 * time-zone change is followed.
 */
object AppClock {
    @Volatile
    var clock: Clock? = null

    private fun current(): Clock = clock ?: Clock.systemDefaultZone()

    fun now(): LocalDateTime = LocalDateTime.now(current())

    fun today(): LocalDate = LocalDate.now(current())

    fun instant(): Instant = current().instant()
}
