package com.sovereignops.timetable.ui.editor

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingDraft
import com.sovereignops.timetable.core.model.DraftError
import com.sovereignops.timetable.core.model.DraftResult
import com.sovereignops.timetable.data.Repository
import com.sovereignops.timetable.data.Settings
import com.sovereignops.timetable.ui.AppClock
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Where the editor reads a stored booking from. [Repository] implements it. */
fun interface BookingStore {
    suspend fun bookingById(id: Long): Booking?
}

/** The editor cannot save yet: the stored booking has not been read. */
data class NotLoaded(val bookingId: Long)

/**
 * State for one editor session. The form lives in [SavedStateHandle], so it
 * survives rotation and process death. An existing booking is read by id from
 * the database (never from a list that may not have loaded yet), and nothing can
 * be saved until that read has finished: a blank form can never overwrite a
 * stored booking.
 */
class BookingEditorViewModel(
    private val handle: SavedStateHandle,
    private val store: BookingStore,
    defaultDurationMinutes: Int,
    today: () -> LocalDate,
) : ViewModel() {

    /** 0 for a new booking. */
    val bookingId: Long = handle.get<Long>(ARG_ID) ?: 0L

    /** Null until the booking has been read; a new booking starts with a blank draft. */
    val draft: LiveData<BookingDraft?> = handle.getLiveData<BookingDraft?>(KEY_DRAFT, null)

    /** True once the stored booking was read (always true for a new one). */
    val isLoaded: Boolean get() = handle.get<BookingDraft>(KEY_DRAFT) != null

    /** True when the booking to edit no longer exists (for example it was deleted). */
    val notFound: LiveData<Boolean> = handle.getLiveData(KEY_NOT_FOUND, false)

    init {
        if (handle.get<BookingDraft>(KEY_DRAFT) == null) {
            if (bookingId == 0L) {
                handle[KEY_DRAFT] = BookingDraft.blank(today(), defaultDurationMinutes)
            } else {
                viewModelScope.launch {
                    val stored = store.bookingById(bookingId)
                    when {
                        stored == null -> handle[KEY_NOT_FOUND] = true
                        handle.get<BookingDraft>(KEY_DRAFT) == null -> handle[KEY_DRAFT] = BookingDraft.from(stored)
                    }
                }
            }
        }
    }

    /** Apply [change] to the form. Ignored before the booking is loaded. */
    fun update(change: (BookingDraft) -> BookingDraft) {
        val current = handle.get<BookingDraft>(KEY_DRAFT) ?: return
        val next = change(current)
        if (next != current) handle[KEY_DRAFT] = next
    }

    /** Validate and build the booking to save, or say why it cannot be saved. */
    fun build(): Result {
        val current = handle.get<BookingDraft>(KEY_DRAFT) ?: return Result.Blocked(NotLoaded(bookingId))
        return when (val result = current.toBooking(bookingId)) {
            is DraftResult.Ok -> Result.Ready(result.booking)
            is DraftResult.Invalid -> Result.Errors(result.errors)
        }
    }

    sealed interface Result {
        data class Ready(val booking: Booking) : Result
        data class Errors(val errors: Set<DraftError>) : Result
        data class Blocked(val reason: NotLoaded) : Result
    }

    companion object {
        const val ARG_ID = "booking_id"
        private const val KEY_DRAFT = "draft"
        private const val KEY_NOT_FOUND = "not_found"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                val repo = Repository(app)
                BookingEditorViewModel(
                    createSavedStateHandle(),
                    BookingStore { repo.bookingById(it) },
                    Settings(app).defaultDurationMinutes,
                    AppClock::today,
                )
            }
        }
    }
}
