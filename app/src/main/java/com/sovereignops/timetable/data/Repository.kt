package com.sovereignops.timetable.data

import android.content.Context
import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The single data entry point for the UI. Exposes domain models (not storage
 * rows) and hides Room behind suspend functions. All data is local; the app
 * holds no network permission and never leaves the device.
 */
class Repository(context: Context) {

    private val db = AppDatabase.get(context)
    private val bookingDao = db.bookingDao()
    private val availabilityDao = db.availabilityDao()

    /** Readable bookings plus the count of rows that could not be read (never deleted). */
    val bookingsLoaded: Flow<Loaded<Booking>> =
        bookingDao.observeAll().map { rows -> Loaded.from(rows) { it.toModelResult() } }

    val availabilityLoaded: Flow<Loaded<AvailabilityWindow>> =
        availabilityDao.observeAll().map { rows -> Loaded.from(rows) { it.toModelResult() } }

    val bookings: Flow<List<Booking>> = bookingsLoaded.map { it.items }

    val availability: Flow<List<AvailabilityWindow>> = availabilityLoaded.map { it.items }

    /** Insert (id == 0) or update. Returns the row id. */
    suspend fun saveBooking(booking: Booking): Long =
        if (booking.id == 0L) {
            bookingDao.insert(booking.toEntity())
        } else {
            bookingDao.update(booking.toEntity()); booking.id
        }

    /**
     * Change one booking's status from the stored row, so a stale in-memory copy
     * can never overwrite other fields. Returns false when the row no longer exists.
     */
    suspend fun setBookingStatus(id: Long, status: BookingStatus): Boolean {
        val stored = bookingDao.getById(id)?.toModelResult()?.getOrNull() ?: return false
        bookingDao.update(stored.withStatus(status).toEntity())
        return true
    }

    suspend fun deleteBooking(id: Long) = bookingDao.deleteById(id)

    /** Put a deleted booking back with its original id (undo). */
    suspend fun restoreBooking(booking: Booking) {
        bookingDao.insert(booking.toEntity())
    }

    suspend fun bookingById(id: Long): Booking? = bookingDao.getById(id)?.toModelResult()?.getOrNull()

    suspend fun saveAvailability(window: AvailabilityWindow): Long =
        availabilityDao.insert(window.toEntity())

    suspend fun deleteAvailability(id: Long) = availabilityDao.deleteById(id)

    /** Put a deleted window back with its original id (undo). */
    suspend fun restoreAvailability(window: AvailabilityWindow) {
        availabilityDao.insert(window.toEntity())
    }

    suspend fun availabilityById(id: Long): AvailabilityWindow? =
        availabilityDao.getById(id)?.toModelResult()?.getOrNull()
}
