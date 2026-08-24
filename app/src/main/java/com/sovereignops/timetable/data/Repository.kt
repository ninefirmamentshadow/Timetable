package com.sovereignops.timetable.data

import android.content.Context
import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.core.model.Booking
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

    suspend fun deleteBooking(id: Long) = bookingDao.deleteById(id)

    suspend fun bookingById(id: Long): Booking? = bookingDao.byId(id)?.toModel()

    suspend fun saveAvailability(window: AvailabilityWindow): Long =
        availabilityDao.insert(window.toEntity())

    suspend fun deleteAvailability(id: Long) = availabilityDao.deleteById(id)
}
