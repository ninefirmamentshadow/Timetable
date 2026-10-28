package com.sovereignops.timetable.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.reminders.ReminderPlanner
import com.sovereignops.timetable.data.Repository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Keeps the system alarms in step with [ReminderPlanner]. Alarms are inexact
 * ([AlarmManager.setAndAllowWhileIdle]), so no exact-alarm permission is needed;
 * a reminder may arrive a few minutes after its nominal time. Call [reschedule]
 * whenever bookings, settings, the time or the time zone change: it cancels
 * alarms that are no longer wanted and (re)sets the wanted ones. Without a
 * [fixedZone] it reads the device's zone on every run, so a long-lived instance
 * follows a time-zone change.
 */
class ReminderScheduler(
    private val context: Context,
    private val fixedZone: ZoneId? = null,
) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    /** Re-set the alarms for [bookings]. Serialised with every other reschedule (see [mutex]). */
    suspend fun reschedule(bookings: List<Booking>, now: LocalDateTime, travelBufferMinutes: Int) {
        mutex.withLock { apply(bookings, now, travelBufferMinutes) }
    }

    /**
     * Read the bookings and settings fresh and re-set the alarms, all under one lock. Every caller
     * (a settings change, a bookings change, boot) goes through here, so two of them can never
     * interleave and cancel each other's alarms, and each run sees the latest saved settings.
     */
    suspend fun rescheduleFromStorage(now: LocalDateTime, travelBufferMinutes: Int) {
        mutex.withLock { apply(Repository(context).bookingsNow(), now, travelBufferMinutes) }
    }

    private fun apply(bookings: List<Booking>, now: LocalDateTime, travelBufferMinutes: Int) {
        val settings = ReminderSettings(context)
        val wanted = if (settings.enabled) {
            ReminderPlanner.plan(bookings, now, settings.leadMinutes, travelBufferMinutes, fixedZone ?: ZoneId.systemDefault())
        } else {
            emptyList()
        }
        val wantedIds = wanted.map { it.bookingId }.toSet()
        for (stale in settings.scheduledIds - wantedIds) alarms.cancel(pendingIntent(stale))
        for (reminder in wanted) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.fireAt.toEpochMilli(), pendingIntent(reminder.bookingId))
        }
        settings.scheduledIds = wantedIds
    }

    suspend fun cancelAll() {
        mutex.withLock {
            val settings = ReminderSettings(context)
            for (id in settings.scheduledIds) alarms.cancel(pendingIntent(id))
            settings.scheduledIds = emptySet()
        }
    }

    private fun pendingIntent(bookingId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMIND)
            .putExtra(ReminderReceiver.EXTRA_BOOKING_ID, bookingId)
        val requestCode = (bookingId xor (bookingId ushr 32)).toInt()
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        /** One lock for the whole process: scheduling reads and writes shared alarm state. */
        val mutex = Mutex()
    }
}
