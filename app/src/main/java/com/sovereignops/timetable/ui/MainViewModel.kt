package com.sovereignops.timetable.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.core.schedule.Conflict
import com.sovereignops.timetable.core.schedule.ConflictDetector
import com.sovereignops.timetable.data.Repository
import com.sovereignops.timetable.data.Settings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

/**
 * Activity-scoped state shared by the three tabs. Holds the live booking and
 * availability lists and the current settings, and answers the one cross-cutting
 * question the editor needs: what conflicts does this candidate booking raise?
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app)
    private val settings = Settings(app)

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()
    private val undoActions = mutableMapOf<Long, suspend () -> Unit>()
    private var nextToken = 1L

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

    /** Conflicts for a candidate booking against the current live data. */
    fun conflictsFor(candidate: Booking): List<Conflict> =
        ConflictDetector(settings.travelBufferMinutes)
            .check(candidate, bookingSnapshot.value, availabilitySnapshot.value)

    fun saveBooking(booking: Booking) = viewModelScope.launch { repo.saveBooking(booking) }

    /** Record how a booking ended (or change its status); reads the stored row first. */
    fun setBookingStatus(id: Long, status: BookingStatus) =
        viewModelScope.launch { repo.setBookingStatus(id, status) }

    /** Delete a booking and offer to put it back ([UiEvent.Deleted]). */
    fun deleteBooking(id: Long) = viewModelScope.launch {
        val booking = repo.bookingById(id) ?: return@launch
        repo.deleteBooking(id)
        val token = rememberUndo { repo.restoreBooking(booking) }
        _events.emit(UiEvent.Deleted(UiEvent.Kind.BOOKING, booking.clientAlias, token))
    }

    fun saveAvailability(window: AvailabilityWindow) =
        viewModelScope.launch { repo.saveAvailability(window) }

    /** Delete an availability window and offer to put it back ([UiEvent.Deleted]). */
    fun deleteAvailability(id: Long) = viewModelScope.launch {
        val window = repo.availabilityById(id) ?: return@launch
        repo.deleteAvailability(id)
        val token = rememberUndo { repo.restoreAvailability(window) }
        val day = window.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        _events.emit(UiEvent.Deleted(UiEvent.Kind.AVAILABILITY_WINDOW, day, token))
    }

    /** Run the undo registered for [token] once; later calls with the same token do nothing. */
    fun undo(token: Long) {
        val action = undoActions.remove(token) ?: return
        viewModelScope.launch { action() }
    }

    private fun rememberUndo(action: suspend () -> Unit): Long {
        val token = nextToken++
        undoActions[token] = action
        return token
    }
}
