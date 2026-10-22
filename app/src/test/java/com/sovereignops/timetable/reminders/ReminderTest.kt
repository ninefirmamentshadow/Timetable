package com.sovereignops.timetable.reminders

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.data.AppDatabase
import com.sovereignops.timetable.data.Repository
import com.sovereignops.timetable.data.Settings
import com.sovereignops.timetable.ui.TestSupport
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReminderTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var alarmManager: AlarmManager
    private val zone = ZoneId.of("UTC")
    private val now = TestSupport.NOW // 2026-10-20 12:00

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        TestSupport.fixClock()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        db = TestSupport.installInMemoryDatabase()
        alarmManager = app.getSystemService(AlarmManager::class.java)
        ReminderSettings(app).apply { enabled = true; leadMinutes = 60; scheduledIds = emptySet() }
        Settings(app).travelBufferMinutes = 45
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() {
        TestSupport.uninstallDatabase(db)
        TestSupport.restoreClock()
        TimeZone.setDefault(null)
    }

    private fun booking(id: Long, start: LocalDateTime, status: BookingStatus = BookingStatus.CONFIRMED) =
        Booking(id, start, 60, "Alias $id", Money.dollars(300), status, locationLabel = "Place $id")

    private fun schedule(bookings: List<Booking>, now: LocalDateTime, buffer: Int) =
        runBlocking { ReminderScheduler(app, zone).reschedule(bookings, now, buffer) }

    private fun scheduleIn(zoneId: String, bookings: List<Booking>, now: LocalDateTime, buffer: Int) =
        runBlocking { ReminderScheduler(app, ZoneId.of(zoneId)).reschedule(bookings, now, buffer) }
    private fun alarms() = shadowOf(alarmManager).scheduledAlarms
    private fun idOf(alarm: org.robolectric.shadows.ShadowAlarmManager.ScheduledAlarm) =
        shadowOf(alarm.operation).savedIntent.getLongExtra(ReminderReceiver.EXTRA_BOOKING_ID, 0)

    private fun epoch(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    // --- scheduling ----------------------------------------------------------

    @Test fun setsAnInexactAllowWhileIdleAlarmLeadPlusBufferBeforeTheStart() {
        val start = now.plusDays(1).withHour(19).withMinute(0)
        schedule(listOf(booking(7, start)), now, 45)

        val alarm = alarms().single()
        assertEquals(1L * 7, idOf(alarm))
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertTrue("inexact, idle-allowed alarm (no exact-alarm permission)", alarm.allowWhileIdle)
        assertEquals(epoch(start.minusMinutes(105)), alarm.triggerAtMs)
        assertEquals(setOf(7L), ReminderSettings(app).scheduledIds)
    }

    @Test fun nothingIsScheduledWhileRemindersAreOff() {
        ReminderSettings(app).enabled = false
        schedule(listOf(booking(1, now.plusDays(1))), now, 45)
        assertTrue(alarms().isEmpty())
    }

    @Test fun switchingOffCancelsExistingAlarms() {
        schedule(listOf(booking(1, now.plusDays(1))), now, 45)
        assertEquals(1, alarms().size)
        ReminderSettings(app).enabled = false
        schedule(listOf(booking(1, now.plusDays(1))), now, 45)
        assertTrue(alarms().isEmpty())
        assertEquals(emptySet<Long>(), ReminderSettings(app).scheduledIds)
    }

    @Test fun aCancelledOrDeletedBookingsAlarmIsRemoved() {
        val a = booking(1, now.plusDays(1))
        val b = booking(2, now.plusDays(2))
        schedule(listOf(a, b), now, 45)
        assertEquals(setOf(1L, 2L), alarms().map(::idOf).toSet())

        schedule(listOf(a.copy(status = BookingStatus.CANCELLED), b), now, 45)
        assertEquals(setOf(2L), alarms().map(::idOf).toSet())

        schedule(emptyList(), now, 45) // b deleted
        assertTrue(alarms().isEmpty())
    }

    @Test fun movingABookingMovesItsAlarmInsteadOfAddingASecond() {
        val original = booking(1, now.plusDays(1).withHour(19))
        schedule(listOf(original), now, 45)
        val moved = original.copy(start = now.plusDays(2).withHour(20))
        schedule(listOf(moved), now, 45)
        val alarm = alarms().single()
        assertEquals(epoch(moved.start.minusMinutes(105)), alarm.triggerAtMs)
    }

    @Test fun changingTheTimeZoneMovesTheAlarmToTheSameWallClockTime() {
        val start = now.plusDays(1).withHour(19)
        scheduleIn("UTC", listOf(booking(1, start)), now, 45)
        val utc = alarms().single().triggerAtMs
        scheduleIn("America/New_York", listOf(booking(1, start)), now, 45)
        val ny = alarms().single().triggerAtMs
        assertEquals("19:00 local in New York is later in absolute time than 19:00 UTC", true, ny > utc)
        assertEquals(epoch(start.minusMinutes(105)) + 4 * 3600_000L, ny) // EDT on 2026-10-21 is UTC-4
    }

    // Regression: two reschedules in flight (one started before the reminders switch was turned
    // on) used to cancel the alarm the other had just set.
    @Test fun concurrentReschedulesNeverCancelEachOthersAlarms() {
        save(booking(0, now.plusDays(1).withHour(19)))
        ReminderSettings(app).enabled = false
        runBlocking {
            kotlinx.coroutines.coroutineScope {
                val early = (1..30).map {
                    launch(kotlinx.coroutines.Dispatchers.Default) { ReminderScheduler(app, zone).rescheduleFromStorage(now, 45) }
                }
                ReminderSettings(app).enabled = true // switched on while the earlier runs are in flight
                launch(kotlinx.coroutines.Dispatchers.Default) { ReminderScheduler(app, zone).rescheduleFromStorage(now, 45) }
                early.forEach { it.join() }
            }
        }
        assertEquals(1, alarms().size)
        assertEquals(1, ReminderSettings(app).scheduledIds.size)
    }

    // --- boot / time change --------------------------------------------------

    @Test fun bootAndClockChangesRescheduleFromStoredBookings() {
        runBlocking { Repository(app).saveBooking(booking(0, now.plusDays(1).withHour(19))) }
        for (action in listOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
        )) {
            shadowOf(alarmManager).scheduledAlarms.clear()
            ReminderSettings(app).scheduledIds = emptySet()
            runBlocking { BootReceiver.rescheduleFromStorage(app) } // what onReceive runs
            assertEquals("after $action", 1, alarms().size)
        }
    }

    @Test fun theBootReceiverHandlesExactlyTheDeclaredSystemBroadcasts() {
        assertEquals(
            setOf(
                "android.intent.action.BOOT_COMPLETED", "android.intent.action.MY_PACKAGE_REPLACED",
                "android.intent.action.TIME_SET", "android.intent.action.TIMEZONE_CHANGED",
            ),
            BootReceiver.HANDLED_ACTIONS,
        )
        // An unrelated action does nothing (and does not touch goAsync).
        BootReceiver().onReceive(app, Intent("com.example.UNRELATED"))
        assertTrue(alarms().isEmpty())
    }

    // --- notification --------------------------------------------------------

    private fun posted(): List<Notification> =
        shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    @Test fun lockScreenVersionShowsOnlyTheTimeNeverTheAlias() {
        val b = booking(5, now.plusDays(1).withHour(14).withMinute(0))
        val n = ReminderNotifications.build(app, b)
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
        val public = n.publicVersion
        assertNotNull(public)
        val publicTitle = public.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        assertEquals("Booking at 2:00 PM", publicTitle)
        val publicEverything = public.extras.keySet().mapNotNull { public.extras.get(it)?.toString() }.joinToString("|")
        assertFalse(publicEverything.contains("Alias 5"))
        assertFalse(publicEverything.contains("Place 5"))
        assertNull(public.extras.getCharSequence(Notification.EXTRA_TEXT))
        // Unlocked, the full text appears.
        assertEquals("Alias 5", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("2:00 PM–3:00 PM · Place 5", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(NotificationCompat.CATEGORY_REMINDER, n.category)
    }

    @Test fun theChannelHidesContentOnTheLockScreen() {
        ReminderNotifications.ensureChannel(app)
        val channel = app.getSystemService(NotificationManager::class.java).getNotificationChannel(ReminderNotifications.CHANNEL_ID)
        assertEquals(Notification.VISIBILITY_PRIVATE, channel.lockscreenVisibility)
    }

    private fun save(b: Booking) = runBlocking { Repository(app).saveBooking(b) }

    @Test fun anAlarmPostsTheReminderForAStillConfirmedBooking() {
        val id = save(booking(0, now.plusHours(2)))
        assertTrue(runBlocking { ReminderReceiver.deliver(app, id) })
        assertEquals(1, posted().size)
    }

    @Test fun noReminderForABookingThatWasCancelledEditedIntoThePastDeletedOrWhenOff() {
        val cancelled = save(booking(0, now.plusHours(2), BookingStatus.CANCELLED))
        val started = save(booking(0, now.minusMinutes(5)))
        val ok = save(booking(0, now.plusHours(3)))
        assertFalse(runBlocking { ReminderReceiver.deliver(app, cancelled) })
        assertFalse(runBlocking { ReminderReceiver.deliver(app, started) })
        assertFalse(runBlocking { ReminderReceiver.deliver(app, 9999) }) // deleted
        ReminderSettings(app).enabled = false
        assertFalse(runBlocking { ReminderReceiver.deliver(app, ok) })
        assertTrue(posted().isEmpty())
    }

    @Test fun blockedNotificationsPostNothing() {
        val id = save(booking(0, now.plusHours(2)))
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        assertFalse(runBlocking { ReminderReceiver.deliver(app, id) })
        assertTrue(posted().isEmpty())
    }

    // --- permission posture --------------------------------------------------

    @Test fun theAppRequestsExactlyTheApprovedPermissions() {
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        // AndroidX core merges one app-private signature permission, <applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION,
        // so non-exported dynamic receivers work on older Android. No other app can hold it; it grants nothing.
        val requested = info.requestedPermissions.orEmpty().toSet()
            .filterNot { it == "${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" }.toSet()
        assertFalse("never INTERNET", "android.permission.INTERNET" in requested)
        assertFalse(requested.any { it.contains("LOCATION") || it.contains("EXACT_ALARM") || it.contains("STORAGE") })
        // Owner-approved 2026-10-20 (AGENTS.md): reminders and the app lock. Anything else is a posture change.
        val approved = setOf(
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.USE_BIOMETRIC",
        )
        assertTrue("unapproved permissions: ${requested - approved}", approved.containsAll(requested))
        assertTrue("android.permission.POST_NOTIFICATIONS" in requested)
        assertTrue("android.permission.RECEIVE_BOOT_COMPLETED" in requested)
    }

    @Test fun contextUsedByTheSchedulerIsTheApplication() {
        assertTrue(app is Context)
    }
}
