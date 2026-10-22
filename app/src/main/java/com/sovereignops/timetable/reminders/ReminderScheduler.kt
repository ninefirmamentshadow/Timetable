package com.sovereignops.timetable.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.reminders.ReminderPlanner
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Keeps the system alarms in step with [ReminderPlanner]. Alarms are inexact
 * ([AlarmManager.setAndAllowWhileIdle]), so no exact-alarm permission is needed;
 * a reminder may arrive a few minutes after its nominal time. Call [reschedule]
 * whenever bookings, settings, the time or the time zone change: it cancels
 * alarms that are no longer wanted and (re)sets the wanted ones.
 */
class ReminderScheduler(
    private val context: Context,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    fun reschedule(bookings: List<Booking>, now: LocalDateTime, travelBufferMinutes: Int) {
        val settings = ReminderSettings(context)
        val wanted = if (settings.enabled) {
            ReminderPlanner.plan(bookings, now, settings.leadMinutes, travelBufferMinutes)
        } else {
            emptyList()
        }
        val wantedIds = wanted.map { it.bookingId }.toSet()
        for (stale in settings.scheduledIds - wantedIds) alarms.cancel(pendingIntent(stale))
        for (reminder in wanted) {
            val at = reminder.fireAt.atZone(zone).toInstant().toEpochMilli()
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(reminder.bookingId))
        }
        settings.scheduledIds = wantedIds
    }

    fun cancelAll() {
        val settings = ReminderSettings(context)
        for (id in settings.scheduledIds) alarms.cancel(pendingIntent(id))
        settings.scheduledIds = emptySet()
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
}
