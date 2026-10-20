package com.sovereignops.timetable.ui

import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.data.AppDatabase
import com.sovereignops.timetable.data.toEntity
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Shared fixtures for Robolectric UI tests. Everything is synthetic: aliases are
 * letters, labels are placeholders, and the clock is fixed so goldens are stable.
 */
object TestSupport {

    /** Tuesday 2026-10-20 12:00. */
    val NOW: LocalDateTime = LocalDateTime.of(2026, 10, 20, 12, 0)

    fun fixClock() {
        AppClock.clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    }

    fun restoreClock() {
        AppClock.clock = Clock.systemDefaultZone()
    }

    /** An in-memory database whose queries run on the calling thread. */
    fun installInMemoryDatabase(): AppDatabase {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        AppDatabase.setInstanceForTesting(db)
        return db
    }

    fun uninstallDatabase(db: AppDatabase) {
        AppDatabase.setInstanceForTesting(null)
        db.close()
    }

    fun seed(db: AppDatabase) = runBlocking {
        val b = db.bookingDao()
        fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)
        listOf(
            Booking(0, at(14, 19), 60, "Alias A", Money.dollars(300), BookingStatus.COMPLETED,
                locationLabel = "Place 1", screened = true, depositReceived = true),
            Booking(0, at(17, 20), 30, "Alias B", Money.dollars(200), BookingStatus.NO_SHOW),
            Booking(0, at(19, 18), 60, "Alias C", Money.dollars(300), BookingStatus.CONFIRMED,
                locationLabel = "Place 2", screened = true),
            Booking(0, at(20, 19), 60, "Alias D", Money.dollars(300), BookingStatus.CONFIRMED,
                locationLabel = "Place 3", screened = true, depositReceived = true),
            Booking(0, at(22, 21), 30, "Alias E", Money.dollars(140), BookingStatus.INQUIRY),
        ).forEach { b.insert(it.toEntity()) }
        val a = db.availabilityDao()
        listOf(
            AvailabilityWindow(0, DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(23, 0)),
            AvailabilityWindow(0, DayOfWeek.SATURDAY, LocalTime.of(18, 0), LocalTime.of(23, 0)),
        ).forEach { a.insert(it.toEntity()) }
    }

    /** Idle the main looper until [condition] holds or [timeoutMs] passes. */
    fun awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(20)
        }
        shadowOf(Looper.getMainLooper()).idle()
        check(condition()) { "condition not met within ${timeoutMs}ms" }
    }
}
