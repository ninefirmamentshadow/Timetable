package com.sovereignops.timetable.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sovereignops.timetable.R
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.reminders.ReminderPlanner
import com.sovereignops.timetable.ui.Formats
import com.sovereignops.timetable.ui.MainActivity

/**
 * Builds and posts the reminder notification. On the lock screen only the
 * public version shows ("Booking at 7:00 PM": no alias, no place); the full text
 * appears once the phone is unlocked.
 */
object ReminderNotifications {

    const val CHANNEL_ID = "booking_reminders"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, context.getString(R.string.reminders_channel_name), NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.reminders_channel_description)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(context: Context, booking: Booking): android.app.Notification {
        ensureChannel(context)
        val time = Formats.timeOf(context, booking.start.toLocalTime())
        val range = "$time–${Formats.timeOf(context, booking.end.toLocalTime())}"
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_agenda)
            .setContentTitle(ReminderPlanner.publicLine(booking.start, time))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val details = buildList {
            add(range)
            if (booking.locationLabel.isNotBlank()) add(booking.locationLabel)
        }.joinToString(" · ")
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_agenda)
            .setContentTitle(booking.clientAlias)
            .setContentText(details)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /** Posts the reminder if notifications are allowed; returns whether it was posted. */
    fun post(context: Context, booking: Booking): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        manager.notify(notificationId(booking.id), build(context, booking))
        return true
    }

    fun notificationId(bookingId: Long): Int = (bookingId xor (bookingId ushr 32)).toInt()
}
