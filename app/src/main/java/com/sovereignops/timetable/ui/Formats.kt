package com.sovereignops.timetable.ui

import android.content.Context
import android.text.format.DateFormat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Date and time formatting for the UI, in one place. Times follow the system
 * 12/24-hour setting; dates follow the device locale. Money is formatted only by
 * `Money.format()`.
 */
object Formats {

    fun timePattern(is24Hour: Boolean): String = if (is24Hour) "HH:mm" else "h:mm a"

    fun time(context: Context): DateTimeFormatter =
        DateTimeFormatter.ofPattern(timePattern(DateFormat.is24HourFormat(context)), Locale.getDefault())

    fun timeOf(context: Context, time: LocalTime): String = time.format(time(context))

    /** A window end of 24:00 is stored as 1440 minutes and shown as midnight. */
    fun minuteOfDay(context: Context, minuteOfDay: Int): String =
        if (minuteOfDay >= 1440) {
            context.getString(com.sovereignops.timetable.R.string.midnight)
        } else {
            timeOf(context, LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
        }

    fun dayFull(): DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())

    fun dateShort(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())

    fun nextBooking(context: Context, start: LocalDateTime): String =
        start.format(DateTimeFormatter.ofPattern("EEE d MMM, ", Locale.getDefault())) +
            start.toLocalTime().format(time(context))
}
