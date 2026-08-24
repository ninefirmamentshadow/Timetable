package com.sovereignops.timetable.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.schedule.Conflict
import com.sovereignops.timetable.core.schedule.ConflictDetector
import com.sovereignops.timetable.data.Repository
import com.sovereignops.timetable.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Activity-scoped state shared by the three tabs. Holds the live booking and
 * availability lists and the current settings, and answers the one cross-cutting
 * question the editor needs: what conflicts does this candidate booking raise?
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app)
    private val settings = Settings(app)

    val bookings = repo.bookings.asLiveData()
    val availability = repo.availability.asLiveData()

    /** Latest snapshots kept for synchronous conflict checks from the editor. */
    private val bookingSnapshot = MutableStateFlow<List<Booking>>(emptyList())
    private val availabilitySnapshot = MutableStateFlow<List<AvailabilityWindow>>(emptyList())

    val snapshots = combine(bookingSnapshot, availabilitySnapshot) { b, a -> b to a }.asLiveData()

    init {
        viewModelScope.launch { repo.bookings.collect { bookingSnapshot.value = it } }
        viewModelScope.launch { repo.availability.collect { availabilitySnapshot.value = it } }
    }

    var travelBufferMinutes: Int
        get() = settings.travelBufferMinutes
        set(value) { settings.travelBufferMinutes = value }

    var defaultDurationMinutes: Int
        get() = settings.defaultDurationMinutes
        set(value) { settings.defaultDurationMinutes = value }

    /** The current stored booking with [id], or null — used to seed the editor. */
    fun currentBooking(id: Long): Booking? =
        bookingSnapshot.value.firstOrNull { it.id == id }

    /** Conflicts for a candidate booking against the current live data. */
    fun conflictsFor(candidate: Booking): List<Conflict> =
        ConflictDetector(settings.travelBufferMinutes)
            .check(candidate, bookingSnapshot.value, availabilitySnapshot.value)

    fun saveBooking(booking: Booking) = viewModelScope.launch { repo.saveBooking(booking) }

    fun deleteBooking(id: Long) = viewModelScope.launch { repo.deleteBooking(id) }

    fun saveAvailability(window: AvailabilityWindow) =
        viewModelScope.launch { repo.saveAvailability(window) }

    fun deleteAvailability(id: Long) = viewModelScope.launch { repo.deleteAvailability(id) }
}
