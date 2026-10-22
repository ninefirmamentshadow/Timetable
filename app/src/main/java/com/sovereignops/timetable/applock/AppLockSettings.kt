package com.sovereignops.timetable.applock

import android.content.Context

/** App-lock preferences. Opt-in: off until the operator switches it on. */
class AppLockSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Seconds in the background before the app locks. */
    var timeoutSeconds: Int
        get() = prefs.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT_SECONDS)
        set(value) = prefs.edit().putInt(KEY_TIMEOUT, value.coerceIn(0, MAX_TIMEOUT_SECONDS)).apply()

    val timeoutMillis: Long get() = timeoutSeconds * 1000L

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 30
        const val MAX_TIMEOUT_SECONDS = 600
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TIMEOUT = "timeout_seconds"
    }
}
