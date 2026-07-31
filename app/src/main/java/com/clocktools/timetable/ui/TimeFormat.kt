package com.clocktools.timetable.ui

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormat {
    private val DISPLAY = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun display(time: LocalTime): String = time.format(DISPLAY)
}
