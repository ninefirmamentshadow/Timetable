package com.sovereignops.timetable.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.core.model.SavedDraft
import com.sovereignops.timetable.core.schedule.Conflict
import com.sovereignops.timetable.core.schedule.ConflictDetector
import com.sovereignops.timetable.data.Repository
import com.sovereignops.timetable.data.Settings
import com.sovereignops.timetable.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /**
     * A failed database write or read in any of the launches below becomes an
     * on-screen message ([UiEvent.Failed]) instead of taking the app down.
     */
    private val failures = CoroutineExceptionHandler { _, error ->
        _events.tryEmit(UiEvent.Failed(error.message ?: error.javaClass.simpleName))
    }

    private fun launchSafely(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launch(failures, block = block)

    private val bookingsLoaded = repo.bookingsLoaded.reportingFailures().asLiveData()
    private val availabilityLoaded = repo.availabilityLoaded.reportingFailures().asLiveData()

    /** Unsaved editor drafts kept on the device, most recent first (the Agenda offers to resume them). */
    val drafts = repo.drafts.reportingFailures().asLiveData()

    val bookings = bookingsLoaded.map { it.items }
    val availability = availabilityLoaded.map { it.items }

    /** Booking rows that could not be read; they are skipped, never deleted. */
    val unreadableBookings = bookingsLoaded.map { it.unreadable }

    /** Availability rows that could not be read. */
    val unreadableWindows = availabilityLoaded.map { it.unreadable }

    /** Latest snapshots kept for synchronous conflict checks from the editor. */
    private val bookingSnapshot = MutableStateFlow<List<Booking>>(emptyList())
    private val availabilitySnapshot = MutableStateFlow<List<AvailabilityWindow>>(emptyList())

    val snapshots = combine(bookingSnapshot, availabilitySnapshot) { b, a -> b to a }.asLiveData()

    private val reminders = ReminderScheduler(app)

    init {
        launchSafely {
            repo.bookings.collect {
                bookingSnapshot.value = it
                rescheduleRemindersNow()
            }
        }
        launchSafely { repo.availability.collect { availabilitySnapshot.value = it } }
    }

    /** A stream that fails (a storage error) reports it once and then stays at its last value. */
    private fun <T> kotlinx.coroutines.flow.Flow<T>.reportingFailures() = catch { error ->
        _events.tryEmit(UiEvent.Failed(error.message ?: error.javaClass.simpleName))
    }

    /** Re-set the reminder alarms from the current bookings and settings. */
    fun rescheduleReminders() {
        launchSafely { rescheduleRemindersNow() }
    }

    // A reminder that cannot be scheduled must never stop the bookings from loading.
    private suspend fun rescheduleRemindersNow() = withContext(Dispatchers.Default) {
        runCatching { reminders.rescheduleFromStorage(AppClock.now(), settings.travelBufferMinutes) }
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

    /** Record how a booking ended (or change its status); reads the stored row first. */
    fun setBookingStatus(id: Long, status: BookingStatus) =
        launchSafely { repo.setBookingStatus(id, status) }

    /** Throw away a kept editor draft (the Agenda's Discard). */
    fun discardDraft(draft: SavedDraft) = launchSafely { repo.clearDraft(draft.bookingId) }

    /** Delete a booking and offer to put it back ([UiEvent.Deleted]). */
    fun deleteBooking(id: Long) = launchSafely {
        val booking = repo.bookingById(id) ?: return@launch
        repo.deleteBooking(id)
        val token = rememberUndo { repo.restoreBooking(booking) }
        _events.emit(UiEvent.Deleted(UiEvent.Kind.BOOKING, booking.clientAlias, token))
    }

    fun saveAvailability(window: AvailabilityWindow) =
        launchSafely { repo.saveAvailability(window) }

    /** Delete an availability window and offer to put it back ([UiEvent.Deleted]). */
    fun deleteAvailability(id: Long) = launchSafely {
        val window = repo.availabilityById(id) ?: return@launch
        repo.deleteAvailability(id)
        val token = rememberUndo { repo.restoreAvailability(window) }
        val day = window.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        _events.emit(UiEvent.Deleted(UiEvent.Kind.AVAILABILITY_WINDOW, day, token))
    }

    /** Run the undo registered for [token] once; later calls with the same token do nothing. */
    fun undo(token: Long) {
        val action = undoActions.remove(token) ?: return
        launchSafely { action() }
    }

    private fun rememberUndo(action: suspend () -> Unit): Long {
        val token = nextToken++
        undoActions[token] = action
        return token
    }
}
