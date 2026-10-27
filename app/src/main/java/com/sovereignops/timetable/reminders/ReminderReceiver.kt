package com.sovereignops.timetable.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.data.Repository
import com.sovereignops.timetable.ui.AppClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Fires when a reminder alarm goes off: re-checks the booking and posts the notification. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val bookingId = intent.getLongExtra(EXTRA_BOOKING_ID, 0L)
        if (bookingId == 0L) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                deliver(context.applicationContext, bookingId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A background broadcast must never crash the app (for example a database whose
                // key is unavailable); the alarms are set again the next time the app runs.
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.sovereignops.timetable.action.REMIND"
        const val EXTRA_BOOKING_ID = "booking_id"

        /**
         * Posts the reminder only if it is still wanted: reminders on, and the booking
         * still confirmed and not yet started (it may have been edited, cancelled or
         * deleted since the alarm was set). Returns whether it was posted.
         */
        suspend fun deliver(context: Context, bookingId: Long): Boolean {
            if (!ReminderSettings(context).enabled) return false
            val booking = Repository(context).bookingById(bookingId) ?: return false
            if (booking.status != BookingStatus.CONFIRMED || !booking.start.isAfter(AppClock.now())) return false
            return ReminderNotifications.post(context, booking)
        }
    }
}
