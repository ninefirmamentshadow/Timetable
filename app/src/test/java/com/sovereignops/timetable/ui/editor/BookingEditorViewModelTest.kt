package com.sovereignops.timetable.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingDraft
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.core.model.DraftError
import com.sovereignops.timetable.core.model.SavedDraft
import com.sovereignops.timetable.ui.editor.BookingEditorViewModel.Restored
import com.sovereignops.timetable.ui.editor.BookingEditorViewModel.SaveState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** An in-memory [EditorStore] whose reads can be gated and whose writes can be made to fail. */
class FakeEditorStore(
    bookings: List<Booking> = emptyList(),
    drafts: List<SavedDraft> = emptyList(),
) : EditorStore {
    val bookings = bookings.associateBy { it.id }.toMutableMap()
    val drafts = drafts.associateBy { it.bookingId }.toMutableMap()
    var bookingGate: CompletableDeferred<Unit>? = null
    var failBookingRead = false
    var failDraftRead = false
    var failDraftWrites = false
    var failSave = false
    val bookingReads = mutableListOf<Long>()
    var draftWrites = 0
    private var nextId = 100L

    override suspend fun bookingById(id: Long): Booking? {
        bookingReads += id
        bookingGate?.await()
        if (failBookingRead) throw IOException("disk read failed")
        return bookings[id]
    }

    override suspend fun draftFor(bookingId: Long): SavedDraft? {
        if (failDraftRead) throw IOException("draft read failed")
        return drafts[bookingId]
    }

    override suspend fun saveDraft(bookingId: Long, draft: BookingDraft, at: LocalDateTime) {
        if (failDraftWrites) throw IOException("draft write failed")
        draftWrites++
        drafts[bookingId] = SavedDraft(bookingId, draft, at)
    }

    override suspend fun clearDraft(bookingId: Long) {
        if (failDraftWrites) throw IOException("draft write failed")
        drafts.remove(bookingId)
    }

    override suspend fun saveBookingFromDraft(booking: Booking, draftKey: Long): Long {
        if (failSave) throw IOException("disk full")
        val id = if (booking.id == 0L) nextId++ else booking.id
        bookings[id] = booking.copy(id = id)
        drafts.remove(draftKey)
        return id
    }
}

// Runs under Robolectric because SavedStateHandle live data asserts the main looper.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BookingEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 20)
    private val now = LocalDateTime.of(2026, 10, 20, 12, 0)

    private val stored = Booking(
        5, LocalDateTime.of(2026, 10, 19, 19, 0), 90, "Alias A", Money.dollars(125),
        BookingStatus.CONFIRMED, "Place 1", true, true, "note",
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun handle(id: Long, vararg extra: Pair<String, Any?>) =
        SavedStateHandle(mapOf<String, Any?>(BookingEditorViewModel.ARG_ID to id, *extra))

    private fun TestScope.vm(handle: SavedStateHandle, store: EditorStore) =
        BookingEditorViewModel(handle, store, 60, { today }, this, { now })

    // Regression: after process death the list was empty, the editor opened blank
    // for an existing id, and Save overwrote the stored booking with blanks.
    @Test fun saveIsBlockedUntilTheStoredBookingIsRead() = runTest(dispatcher) {
        val store = FakeEditorStore(listOf(stored)).apply { bookingGate = CompletableDeferred() }
        val editor = vm(handle(5), store)
        advanceUntilIdle()

        assertFalse(editor.isLoaded)
        assertTrue(editor.build() is BookingEditorViewModel.Result.Blocked)
        editor.update { it.copy(alias = "typed too early") } // ignored: nothing to edit yet
        assertTrue(editor.build() is BookingEditorViewModel.Result.Blocked)

        store.bookingGate!!.complete(Unit)
        advanceUntilIdle()

        assertTrue(editor.isLoaded)
        val ready = editor.build() as BookingEditorViewModel.Result.Ready
        assertEquals(stored, ready.booking) // same id, same fields: nothing blanked
        assertTrue(store.drafts.isEmpty()) // the early keystroke wrote no draft either
    }

    @Test fun loadsByIdFromTheStoreNotFromAList() = runTest(dispatcher) {
        val store = FakeEditorStore(listOf(stored))
        val editor = vm(handle(5), store)
        advanceUntilIdle()
        assertEquals(listOf(5L), store.bookingReads)
        assertEquals(BookingDraft.from(stored), editor.draft.value)
        assertFalse(editor.hasChanges)
        assertNull(editor.restored.value)
    }

    @Test fun missingBookingIsReportedAndNeverSavable() = runTest(dispatcher) {
        val editor = vm(handle(9), FakeEditorStore())
        advanceUntilIdle()
        assertEquals(true, editor.notFound.value)
        assertFalse(editor.isLoaded)
        assertTrue(editor.build() is BookingEditorViewModel.Result.Blocked)
    }

    // Regression: a storage error while reading the booking crashed the editor.
    @Test fun aFailedReadClosesTheEditorWithoutCrashingOrWriting() = runTest(dispatcher) {
        val store = FakeEditorStore(listOf(stored)).apply { failBookingRead = true }
        val editor = vm(handle(5), store)
        advanceUntilIdle()
        assertEquals(true, editor.loadFailed.value)
        assertFalse(editor.isLoaded)
        assertEquals(stored, store.bookings[5])
    }

    @Test fun editsSurviveProcessDeathAndAreNotClobberedByAReload() = runTest(dispatcher) {
        // First process: load, then the user edits.
        val first = handle(5)
        val editor = vm(first, FakeEditorStore(listOf(stored)))
        advanceUntilIdle()
        editor.update { it.copy(alias = "Edited", time = LocalTime.of(21, 15), status = BookingStatus.COMPLETED) }

        // Process death: only the SavedStateHandle contents survive.
        val restoredHandle = SavedStateHandle(first.keys().associateWith { first.get<Any?>(it) })
        val store = FakeEditorStore(listOf(stored))
        val revived = vm(restoredHandle, store)
        advanceUntilIdle()

        assertTrue(store.bookingReads.isEmpty()) // no reload to clobber the edits
        val ready = revived.build() as BookingEditorViewModel.Result.Ready
        assertEquals("Edited", ready.booking.clientAlias)
        assertEquals(LocalDateTime.of(2026, 10, 19, 21, 15), ready.booking.start)
        assertEquals(BookingStatus.COMPLETED, ready.booking.status)
        assertEquals(5L, ready.booking.id)
        assertTrue(revived.hasChanges)
    }

    @Test fun newBookingOpensWithTodayAndDefaultDuration() = runTest(dispatcher) {
        val store = FakeEditorStore()
        val editor = vm(handle(0), store)
        advanceUntilIdle()
        assertTrue(editor.isLoaded)
        assertTrue(store.bookingReads.isEmpty()) // a new booking reads no booking
        val draft = editor.draft.value!!
        assertEquals(today, draft.date)
        assertEquals("60", draft.durationText)
        assertFalse(editor.hasChanges)
        val blocked = editor.build() as BookingEditorViewModel.Result.Errors
        assertTrue(DraftError.ALIAS_REQUIRED in blocked.errors)
    }

    @Test fun invalidFormReportsErrorsAndSavesNothing() = runTest(dispatcher) {
        val editor = vm(handle(5), FakeEditorStore(listOf(stored)))
        advanceUntilIdle()
        editor.update { it.copy(alias = "", rateText = "abc", durationText = "0") }
        val errors = (editor.build() as BookingEditorViewModel.Result.Errors).errors
        assertEquals(setOf(DraftError.ALIAS_REQUIRED, DraftError.RATE_INVALID, DraftError.DURATION_INVALID), errors)
    }

    // ------------------------------------------------------------ kept drafts

    // Regression: a new booking typed and then lost to a crash, a swipe from recents or a
    // tap outside the dialog was gone for good.
    @Test fun typingKeepsADraftAndANewEditorPicksItUp() = runTest(dispatcher) {
        val store = FakeEditorStore()
        val first = vm(handle(0), store)
        advanceUntilIdle()
        first.update { it.copy(alias = "Alias N", rateText = "50") }
        advanceUntilIdle()
        assertEquals("Alias N", store.drafts[0L]!!.draft.alias)
        assertEquals(now, store.drafts[0L]!!.updatedAt)

        // The app dies without saved state: a fresh editor session starts from the store only.
        val second = vm(handle(0), store)
        advanceUntilIdle()
        assertEquals("Alias N", second.draft.value!!.alias)
        assertEquals("50", second.draft.value!!.rateText)
        assertEquals(Restored.DRAFT, second.restored.value)
        assertTrue(second.hasChanges)
    }

    @Test fun aKeptDraftOfAnExistingBookingIsRestoredOverTheStoredValues() = runTest(dispatcher) {
        val changed = BookingDraft.from(stored).copy(notes = "changed but not saved")
        val store = FakeEditorStore(listOf(stored), listOf(SavedDraft(5, changed, now)))
        val editor = vm(handle(5), store)
        advanceUntilIdle()
        assertEquals(changed, editor.draft.value)
        assertEquals(Restored.DRAFT, editor.restored.value)
        assertTrue(editor.hasChanges)
    }

    @Test fun aRestoredDraftEditedBackToTheStoredValuesHasNothingToLose() = runTest(dispatcher) {
        val changed = BookingDraft.from(stored).copy(notes = "changed but not saved")
        val store = FakeEditorStore(listOf(stored), listOf(SavedDraft(5, changed, now)))
        val editor = vm(handle(5), store)
        advanceUntilIdle()
        editor.update { it.copy(notes = stored.notes) }
        advanceUntilIdle()
        assertFalse(editor.hasChanges)
        assertFalse(5L in store.drafts)
    }

    @Test fun aKeptDraftIdenticalToTheStoredBookingIsNotAnnounced() = runTest(dispatcher) {
        val store = FakeEditorStore(listOf(stored), listOf(SavedDraft(5, BookingDraft.from(stored), now)))
        val editor = vm(handle(5), store)
        advanceUntilIdle()
        assertNull(editor.restored.value)
        assertFalse(editor.hasChanges)
    }

    @Test fun editingBackToTheStoredValuesRemovesTheDraft() = runTest(dispatcher) {
        val store = FakeEditorStore(listOf(stored))
        val editor = vm(handle(5), store)
        advanceUntilIdle()
        editor.update { it.copy(alias = "Other") }
        advanceUntilIdle()
        assertTrue(5L in store.drafts)
        editor.update { it.copy(alias = "Alias A") }
        advanceUntilIdle()
        assertFalse(5L in store.drafts)
        assertFalse(editor.hasChanges)
    }

    @Test fun rapidEditsLeaveTheLatestFormStoredNeverAnOlderOne() = runTest(dispatcher) {
        val store = FakeEditorStore()
        val editor = vm(handle(0), store)
        advanceUntilIdle()
        for (n in 1..25) editor.update { it.copy(alias = "Alias $n") }
        advanceUntilIdle()
        assertEquals("Alias 25", store.drafts[0L]!!.draft.alias)
        assertTrue("superseded writes are skipped", store.draftWrites < 25)
    }

    // A draft of a booking that was deleted meanwhile is not thrown away: it becomes a new booking.
    @Test fun aDraftOfADeletedBookingIsKeptAndSavesAsANewBooking() = runTest(dispatcher) {
        val changed = BookingDraft.from(stored).copy(alias = "Alias Kept")
        val store = FakeEditorStore(drafts = listOf(SavedDraft(5, changed, now)))
        val editor = vm(handle(5), store)
        advanceUntilIdle()
        assertEquals(false, editor.notFound.value)
        assertEquals(Restored.DRAFT_OF_DELETED_BOOKING, editor.restored.value)
        assertEquals(0L, editor.bookingId)
        val ready = editor.build() as BookingEditorViewModel.Result.Ready
        assertEquals(0L, ready.booking.id)

        editor.save(ready.booking)
        advanceUntilIdle()
        assertEquals(SaveState.Saved, editor.saveState.value)
        assertEquals("Alias Kept", store.bookings.values.single().clientAlias)
        assertTrue(store.drafts.isEmpty())
    }

    @Test fun anUnreadableDraftNeverStopsTheEditorOpening() = runTest(dispatcher) {
        val store = FakeEditorStore(listOf(stored)).apply { failDraftRead = true }
        val editor = vm(handle(5), store)
        advanceUntilIdle()
        assertEquals(BookingDraft.from(stored), editor.draft.value)
    }

    @Test fun aFailedDraftWriteIsReportedAndTheFormIsUntouched() = runTest(dispatcher) {
        val store = FakeEditorStore().apply { failDraftWrites = true }
        val editor = vm(handle(0), store)
        advanceUntilIdle()
        editor.update { it.copy(alias = "Alias W") }
        advanceUntilIdle()
        assertEquals(true, editor.draftWriteFailed.value)
        assertEquals("Alias W", editor.draft.value!!.alias)
    }

    // ------------------------------------------------------------------ saving

    @Test fun savingWritesTheBookingAndRemovesItsDraft() = runTest(dispatcher) {
        val store = FakeEditorStore(listOf(stored))
        val editor = vm(handle(5), store)
        advanceUntilIdle()
        editor.update { it.copy(alias = "Saved Alias") }
        advanceUntilIdle()
        assertTrue(5L in store.drafts)

        editor.save((editor.build() as BookingEditorViewModel.Result.Ready).booking)
        assertEquals(SaveState.Saving, editor.saveState.value)
        advanceUntilIdle()

        assertEquals(SaveState.Saved, editor.saveState.value)
        assertEquals("Saved Alias", store.bookings[5]!!.clientAlias)
        assertTrue(store.drafts.isEmpty())
        // Late edits (a text watcher firing while the dialog closes) do not bring the draft back.
        editor.update { it.copy(alias = "after save") }
        advanceUntilIdle()
        assertTrue(store.drafts.isEmpty())
    }

    // Regression: the editor closed before the write ran, so a failed write lost the booking.
    @Test fun aFailedSaveKeepsTheFormAndTheDraftForARetry() = runTest(dispatcher) {
        val store = FakeEditorStore().apply { failSave = true }
        val editor = vm(handle(0), store)
        advanceUntilIdle()
        editor.update { it.copy(alias = "Alias F", rateText = "50") }
        advanceUntilIdle()

        editor.save((editor.build() as BookingEditorViewModel.Result.Ready).booking)
        advanceUntilIdle()
        val failed = editor.saveState.value as SaveState.Failed
        assertEquals("disk full", failed.reason)
        assertEquals("Alias F", editor.draft.value!!.alias)
        assertEquals("Alias F", store.drafts[0L]!!.draft.alias)
        assertTrue(store.bookings.isEmpty())

        // The retry goes through once the problem is gone.
        store.failSave = false
        editor.save((editor.build() as BookingEditorViewModel.Result.Ready).booking)
        advanceUntilIdle()
        assertEquals(SaveState.Saved, editor.saveState.value)
        assertEquals("Alias F", store.bookings.values.single().clientAlias)
        assertTrue(store.drafts.isEmpty())
    }

    @Test fun aSecondSaveWhileTheFirstRunsIsIgnored() = runTest(dispatcher) {
        val store = FakeEditorStore()
        val editor = vm(handle(0), store)
        advanceUntilIdle()
        editor.update { it.copy(alias = "Alias D", rateText = "50") }
        val booking = (editor.build() as BookingEditorViewModel.Result.Ready).booking
        editor.save(booking)
        editor.save(booking)
        advanceUntilIdle()
        assertEquals(1, store.bookings.size)
    }

    // --------------------------------------------------------------- closing

    @Test fun discardRemovesTheDraftEvenWithWritesStillQueued() = runTest(dispatcher) {
        val store = FakeEditorStore()
        val editor = vm(handle(0), store)
        advanceUntilIdle()
        editor.update { it.copy(alias = "Alias X") }
        editor.discard()
        advanceUntilIdle()
        assertTrue(store.drafts.isEmpty())
    }

    @Test fun keepForLaterLeavesTheDraftInPlace() = runTest(dispatcher) {
        val store = FakeEditorStore()
        val editor = vm(handle(0), store)
        advanceUntilIdle()
        editor.update { it.copy(alias = "Alias K") }
        editor.keepForLater()
        advanceUntilIdle()
        assertEquals("Alias K", store.drafts[0L]!!.draft.alias)
    }
}
