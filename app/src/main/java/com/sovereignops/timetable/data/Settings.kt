package com.sovereignops.timetable.data

import android.content.Context

/**
 * Small local preferences: the travel buffer that drives the outcall gap warning
 * and the default booking length. Stored in a private SharedPreferences file;
 * nothing here leaves the device.
 */
class Settings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("timetable_settings", Context.MODE_PRIVATE)

    var travelBufferMinutes: Int
        get() = prefs.getInt(KEY_BUFFER, DEFAULT_BUFFER)
        set(value) = prefs.edit().putInt(KEY_BUFFER, value.coerceIn(0, 480)).apply()

    var defaultDurationMinutes: Int
        get() = prefs.getInt(KEY_DURATION, DEFAULT_DURATION)
        set(value) = prefs.edit().putInt(KEY_DURATION, value.coerceIn(15, 1440)).apply()

    companion object {
        private const val KEY_BUFFER = "travel_buffer_minutes"
        private const val KEY_DURATION = "default_duration_minutes"
        const val DEFAULT_BUFFER = 45
        const val DEFAULT_DURATION = 60
    }
}
