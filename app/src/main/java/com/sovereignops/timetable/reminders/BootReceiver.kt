package com.sovereignops.timetable.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sovereignops.timetable.data.Settings
import com.sovereignops.timetable.ui.AppClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Alarms do not survive a reboot, and a changed clock or time zone moves every
 * reminder: on boot, app update, time change or time-zone change, set them again
 * from the stored bookings.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                rescheduleFromStorage(context.applicationContext)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )

        suspend fun rescheduleFromStorage(context: Context) {
            ReminderScheduler(context).rescheduleFromStorage(AppClock.now(), Settings(context).travelBufferMinutes)
        }
    }
}
