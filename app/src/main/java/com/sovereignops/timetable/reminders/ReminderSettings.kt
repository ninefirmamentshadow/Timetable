package com.sovereignops.timetable.reminders

import android.content.Context
import com.sovereignops.timetable.core.reminders.ReminderPlanner

/** Reminder preferences, in a private SharedPreferences file. Nothing leaves the device. */
class ReminderSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("timetable_reminders", Context.MODE_PRIVATE)

    /** Off until the operator switches reminders on (which is also when the notification permission is asked). */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Minutes before the start, on top of the travel buffer. */
    var leadMinutes: Int
        get() = prefs.getInt(KEY_LEAD, ReminderPlanner.DEFAULT_LEAD_MINUTES)
        set(value) = prefs.edit().putInt(KEY_LEAD, value.coerceIn(0, ReminderPlanner.MAX_LEAD_MINUTES)).apply()

    /** Booking ids that currently have an alarm set, so stale ones can be cancelled. */
    var scheduledIds: Set<Long>
        get() = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        set(value) = prefs.edit().putStringSet(KEY_SCHEDULED, value.map { it.toString() }.toSet()).apply()

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_LEAD = "lead_minutes"
        const val KEY_SCHEDULED = "scheduled_ids"
    }
}
