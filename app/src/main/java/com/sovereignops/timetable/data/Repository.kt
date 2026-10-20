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

    val bookings: Flow<List<Booking>> =
        bookingDao.observeAll().map { rows -> rows.map { it.toModel() } }

    val availability: Flow<List<AvailabilityWindow>> =
        availabilityDao.observeAll().map { rows -> rows.map { it.toModel() } }

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
        val stored = bookingDao.getById(id)?.toModel() ?: return false
        bookingDao.update(stored.withStatus(status).toEntity())
        return true
    }

    suspend fun deleteBooking(id: Long) = bookingDao.deleteById(id)

    suspend fun bookingById(id: Long): Booking? = bookingDao.getById(id)?.toModel()

    suspend fun saveAvailability(window: AvailabilityWindow): Long =
        availabilityDao.insert(window.toEntity())

    suspend fun deleteAvailability(id: Long) = availabilityDao.deleteById(id)
}
