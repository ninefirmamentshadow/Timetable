package com.sovereignops.timetable.data

import android.content.Context

/**
 * Small local preferences: the travel buffer that drives the travel-gap warning
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

    /** FLAG_SECURE on every window (blocks screenshots, blanks the recents thumbnail). Default on. */
    var secureWindow: Boolean
        get() = prefs.getBoolean(KEY_SECURE, true)
        set(value) = prefs.edit().putBoolean(KEY_SECURE, value).apply()

    /**
     * A random id for this install, made on first use, that keeps calendar-export
     * UIDs unique across installs (row ids restart at 1). It identifies nothing:
     * 16 random hex digits, never sent anywhere, not in the backup.
     */
    val exportId: String
        @Synchronized get() = prefs.getString(KEY_EXPORT_ID, null)?.takeIf { EXPORT_ID.matches(it) } ?: run {
            val id = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString(KEY_EXPORT_ID, id).commit()
            id
        }

    companion object {
        private val EXPORT_ID = Regex("[0-9a-f]{16}")
        private const val KEY_EXPORT_ID = "calendar_export_id"
        private const val KEY_SECURE = "secure_window"
        private const val KEY_BUFFER = "travel_buffer_minutes"
        private const val KEY_DURATION = "default_duration_minutes"
        const val DEFAULT_BUFFER = 45
        const val DEFAULT_DURATION = 60
    }
}
