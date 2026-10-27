package com.sovereignops.timetable.ui.editor

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
import com.sovereignops.timetable.core.model.SavedDraft
import com.sovereignops.timetable.data.Repository
import com.sovereignops.timetable.data.Settings
import com.sovereignops.timetable.ui.AppClock
import com.sovereignops.timetable.ui.AppScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

/** Where the editor reads and writes. [Repository] implements it through [RepositoryEditorStore]. */
interface EditorStore {
    suspend fun bookingById(id: Long): Booking?
    suspend fun draftFor(bookingId: Long): SavedDraft?
    suspend fun saveDraft(bookingId: Long, draft: BookingDraft, at: LocalDateTime)
    suspend fun clearDraft(bookingId: Long)

    /** Store the booking and drop the draft under [draftKey] together (one transaction). */
    suspend fun saveBookingFromDraft(booking: Booking, draftKey: Long): Long
}

/** The editor cannot save yet: the stored booking has not been read. */
data class NotLoaded(val bookingId: Long)

/**
 * State for one editor session.
 *
 * The form lives in [SavedStateHandle], so it survives rotation and process death,
 * and every change is also written to the device as a draft (see [EditorStore]),
 * so a crash, a swipe from recents or a closed dialog never loses what was typed.
 * Opening the editor again for the same booking (or a new booking) picks the draft
 * back up. The draft is removed only when the booking is saved (in the same
 * transaction) or the operator discards it.
 *
 * An existing booking is read by id from the database (never from a list that may
 * not have loaded yet), and nothing can be saved until that read has finished: a
 * blank form can never overwrite a stored booking. A save is a write the editor
 * waits for: it closes only after the write succeeded, and a failed write leaves
 * the form open with everything still in it.
 *
 * Writes run on [writer] (an application-wide scope in production), not the view
 * model's scope, so closing the editor never cancels a draft or booking write
 * half way.
 */
class BookingEditorViewModel(
    private val handle: SavedStateHandle,
    private val store: EditorStore,
    defaultDurationMinutes: Int,
    today: () -> LocalDate,
    private val writer: CoroutineScope,
    private val now: () -> LocalDateTime,
) : ViewModel() {

    /** The booking this editor was opened for; also the key its draft is kept under. 0 for a new booking. */
    val draftKey: Long = handle.get<Long>(ARG_ID) ?: 0L

    /**
     * The id a save writes to. Normally [draftKey]; 0 (a new booking) when the booking
     * was deleted while an unsaved draft of it was kept, so those changes are not lost.
     */
    val bookingId: Long get() = handle.get<Long>(KEY_TARGET_ID) ?: draftKey

    /** Null until the booking (and any kept draft) has been read. */
    val draft: LiveData<BookingDraft?> = handle.getLiveData<BookingDraft?>(KEY_DRAFT, null)

    /** True once the stored booking was read (or, for a new booking, the kept draft looked up). */
    val isLoaded: Boolean get() = handle.get<BookingDraft>(KEY_DRAFT) != null

    /** True when the booking to edit no longer exists and there was nothing unsaved to keep. */
    val notFound: LiveData<Boolean> = handle.getLiveData(KEY_NOT_FOUND, false)

    /** True when the booking could not be read (a storage error). The editor closes; nothing was changed. */
    val loadFailed: LiveData<Boolean> = handle.getLiveData(KEY_LOAD_FAILED, false)

    /** Why the form shows content the operator did not just type, or null. */
    val restored: LiveData<Restored?> = handle.getLiveData<Restored?>(KEY_RESTORED, null)

    enum class Restored {
        /** Unsaved changes from an earlier session were put back. */
        DRAFT,

        /** As [DRAFT], but the booking itself was deleted meanwhile: saving creates it again. */
        DRAFT_OF_DELETED_BOOKING,
    }

    private val _saveState = MutableLiveData<SaveState>(SaveState.Idle)
    val saveState: LiveData<SaveState> = _saveState

    sealed interface SaveState {
        data object Idle : SaveState
        data object Saving : SaveState
        data object Saved : SaveState

        /** The write failed; the form and its draft are untouched. [reason] is for the operator. */
        data class Failed(val reason: String) : SaveState
    }

    /** True when the last background draft write failed (the form itself is still intact). */
    private val _draftWriteFailed = MutableLiveData(false)
    val draftWriteFailed: LiveData<Boolean> = _draftWriteFailed

    /** What the form held before this session's edits: a blank form or the stored booking. */
    private val baseline: BookingDraft? get() = handle.get<BookingDraft>(KEY_BASELINE)

    /** True when closing now would lose something: the form differs from what is stored, or holds a kept draft. */
    val hasChanges: Boolean
        get() {
            val current = handle.get<BookingDraft>(KEY_DRAFT) ?: return false
            // A kept draft of a deleted booking would be lost on close even if it matches the blank baseline.
            return current != baseline || handle.get<Restored>(KEY_RESTORED) == Restored.DRAFT_OF_DELETED_BOOKING
        }

    /** Set once the session is finished (saved or discarded); later edits are ignored. */
    private var finished = false

    // Draft writes are serialized, and a write that a newer one supersedes is skipped,
    // so the stored draft always ends at the latest form and never at an older one.
    private val writes = Mutex()
    private val writeSeq = AtomicLong()

    init {
        if (handle.get<BookingDraft>(KEY_DRAFT) == null) {
            viewModelScope.launch { load(today(), defaultDurationMinutes) }
        }
    }

    private suspend fun load(today: LocalDate, defaultDurationMinutes: Int) {
        val blank = BookingDraft.blank(today, defaultDurationMinutes)
        // A draft that cannot be read is treated as absent: the form still opens.
        val kept = readOrNull { store.draftFor(draftKey) }?.draft
        if (draftKey == 0L) {
            open(baseline = blank, form = kept ?: blank, restored = kept?.let { Restored.DRAFT })
            return
        }
        val stored = try {
            store.bookingById(draftKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handle[KEY_LOAD_FAILED] = true
            return
        }
        when {
            stored != null -> {
                val base = BookingDraft.from(stored)
                val form = kept?.takeIf { it != base }
                open(baseline = base, form = form ?: base, restored = form?.let { Restored.DRAFT })
            }
            kept != null -> {
                handle[KEY_TARGET_ID] = 0L
                open(baseline = blank, form = kept, restored = Restored.DRAFT_OF_DELETED_BOOKING)
            }
            else -> handle[KEY_NOT_FOUND] = true
        }
    }

    private fun open(baseline: BookingDraft, form: BookingDraft, restored: Restored?) {
        if (handle.get<BookingDraft>(KEY_DRAFT) != null) return
        handle[KEY_BASELINE] = baseline
        handle[KEY_RESTORED] = restored
        handle[KEY_DRAFT] = form
    }

    /** Apply [change] to the form and keep it as a draft. Ignored before loading and after the session ends. */
    fun update(change: (BookingDraft) -> BookingDraft) {
        if (finished || _saveState.value == SaveState.Saving) return
        val current = handle.get<BookingDraft>(KEY_DRAFT) ?: return
        val next = change(current)
        if (next == current) return
        handle[KEY_DRAFT] = next
        persist(next)
    }

    /** Write [form] as this session's draft, or remove the draft when nothing would be lost without it. */
    private fun persist(form: BookingDraft) {
        val seq = writeSeq.incrementAndGet()
        val keep = form != baseline || handle.get<Restored>(KEY_RESTORED) == Restored.DRAFT_OF_DELETED_BOOKING
        val key = draftKey
        writer.launch {
            writes.withLock {
                if (seq != writeSeq.get()) return@withLock // a newer form is queued behind this one
                val ok = attempt { if (keep) store.saveDraft(key, form, now()) else store.clearDraft(key) }
                withContext(Dispatchers.Main) { _draftWriteFailed.value = !ok }
            }
        }
    }

    /** Validate and build the booking to save, or say why it cannot be saved. */
    fun build(): Result {
        val current = handle.get<BookingDraft>(KEY_DRAFT) ?: return Result.Blocked(NotLoaded(draftKey))
        return when (val result = current.toBooking(bookingId)) {
            is DraftResult.Ok -> Result.Ready(result.booking)
            is DraftResult.Invalid -> Result.Errors(result.errors)
        }
    }

    /**
     * Write [booking] (from [build], after any conflict warning was accepted). [saveState]
     * goes to Saved only once the write succeeded; on failure it is Failed and the form,
     * its draft and the editor stay as they were so the operator can retry.
     */
    fun save(booking: Booking) {
        if (finished || _saveState.value == SaveState.Saving) return
        _saveState.value = SaveState.Saving
        val key = draftKey
        writer.launch {
            val failure = writes.withLock {
                try {
                    store.saveBookingFromDraft(booking, key)
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e
                }
            }
            withContext(Dispatchers.Main) {
                if (failure == null) {
                    finished = true
                    _saveState.value = SaveState.Saved
                } else {
                    _saveState.value = SaveState.Failed(failure.message ?: failure.javaClass.simpleName)
                    // Make sure the latest form is kept even if a draft write was skipped meanwhile.
                    handle.get<BookingDraft>(KEY_DRAFT)?.let { persist(it) }
                }
            }
        }
    }

    /** Throw away this session's unsaved changes, including the kept draft. */
    fun discard() {
        finished = true
        writeSeq.incrementAndGet() // queued draft writes are now stale and skip
        val key = draftKey
        writer.launch { writes.withLock { attempt { store.clearDraft(key) } } }
    }

    /** Close without discarding: the draft stays on the device to finish later. */
    fun keepForLater() {
        finished = true
    }

    private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private suspend fun <T> readOrNull(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    sealed interface Result {
        data class Ready(val booking: Booking) : Result
        data class Errors(val errors: Set<DraftError>) : Result
        data class Blocked(val reason: NotLoaded) : Result
    }

    companion object {
        const val ARG_ID = "booking_id"
        private const val KEY_DRAFT = "draft"
        private const val KEY_BASELINE = "baseline"
        private const val KEY_RESTORED = "restored"
        private const val KEY_TARGET_ID = "target_id"
        private const val KEY_NOT_FOUND = "not_found"
        private const val KEY_LOAD_FAILED = "load_failed"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                BookingEditorViewModel(
                    createSavedStateHandle(),
                    RepositoryEditorStore(Repository(app)),
                    Settings(app).defaultDurationMinutes,
                    AppClock::today,
                    AppScope.writes,
                    AppClock::now,
                )
            }
        }
    }
}

/** The production [EditorStore]. */
class RepositoryEditorStore(private val repo: Repository) : EditorStore {
    override suspend fun bookingById(id: Long) = repo.bookingById(id)
    override suspend fun draftFor(bookingId: Long) = repo.draftFor(bookingId)
    override suspend fun saveDraft(bookingId: Long, draft: BookingDraft, at: LocalDateTime) =
        repo.saveDraft(bookingId, draft, at)
    override suspend fun clearDraft(bookingId: Long) = repo.clearDraft(bookingId)
    override suspend fun saveBookingFromDraft(booking: Booking, draftKey: Long) =
        repo.saveBookingFromDraft(booking, draftKey)
}
