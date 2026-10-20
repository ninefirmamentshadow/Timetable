package com.sovereignops.timetable.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.ui.TestSupport
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
}
