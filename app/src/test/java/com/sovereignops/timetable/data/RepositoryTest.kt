package com.sovereignops.timetable.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.data.entity.AvailabilityEntity
import com.sovereignops.timetable.data.entity.BookingEntity
import com.sovereignops.timetable.ui.TestSupport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() {
        db = TestSupport.installInMemoryDatabase()
        repo = Repository(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() = TestSupport.uninstallDatabase(db)

    private fun sample() = Booking(
        id = 0,
        start = LocalDateTime.of(2026, 10, 19, 19, 0),
        durationMinutes = 90,
        clientAlias = "Alias A",
        rate = Money.dollars(125),
        status = BookingStatus.CONFIRMED,
        locationLabel = "Place 1",
        screened = true,
        depositReceived = true,
        notes = "note",
    )

    @Test fun setBookingStatusChangesOnlyTheStatusOfTheStoredRow() = runBlocking {
        val id = repo.saveBooking(sample())
        assertTrue(repo.setBookingStatus(id, BookingStatus.COMPLETED))
        assertEquals(sample().copy(id = id, status = BookingStatus.COMPLETED), repo.bookingById(id))
    }

    @Test fun setBookingStatusOnAMissingRowReportsFalseAndWritesNothing() = runBlocking {
        assertFalse(repo.setBookingStatus(999, BookingStatus.COMPLETED))
        assertNull(repo.bookingById(999))
    }

    private fun badRows() = listOf(
        BookingEntity(0, 1_792_000_000, 0, "Bad A", 100, "CONFIRMED", "", false, false, ""),
        BookingEntity(0, 1_792_000_000, 60, "Bad B", 100, "NOT_A_STATUS", "", false, false, ""),
    )

    private fun count(table: String) =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    // Regression: one unreadable row crashed the whole list.
    @Test fun unreadableRowsAreSkippedAndCountedNeverDeleted() = runBlocking {
        repo.saveBooking(sample())
        badRows().forEach { db.bookingDao().insert(it) }

        val loaded = repo.bookingsLoaded.first()
        assertEquals(1, loaded.items.size)
        assertEquals(2, loaded.unreadable)
        assertEquals("the unreadable rows are still stored", 3, count("bookings"))
        assertEquals(1, repo.bookings.first().size)
    }

    @Test fun anUnreadableBookingCannotBeOpenedOrChangedByStatus() = runBlocking {
        val id = db.bookingDao().insert(badRows()[1])
        assertNull(repo.bookingById(id))
        assertFalse(repo.setBookingStatus(id, BookingStatus.COMPLETED))
        assertEquals("NOT_A_STATUS", db.bookingDao().getById(id)!!.status)
    }

    @Test fun unreadableAvailabilityRowsAreSkippedAndCounted() = runBlocking {
        db.availabilityDao().insert(AvailabilityEntity(0, 9, 60, 120))
        db.availabilityDao().insert(AvailabilityEntity(0, 5, 18 * 60, 1440))
        val loaded = repo.availabilityLoaded.first()
        assertEquals(1, loaded.items.size)
        assertEquals(1, loaded.unreadable)
        assertEquals(2, count("availability"))
    }
}
