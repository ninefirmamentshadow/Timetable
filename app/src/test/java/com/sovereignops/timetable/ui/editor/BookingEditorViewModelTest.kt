package com.sovereignops.timetable.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingDraft
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.core.model.DraftError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// Runs under Robolectric because SavedStateHandle live data asserts the main looper.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BookingEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 20)

    private val stored = Booking(
        5, LocalDateTime.of(2026, 10, 19, 19, 0), 90, "Alias A", Money.dollars(300),
        BookingStatus.CONFIRMED, "Place 1", true, true, "note",
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun handle(id: Long, vararg extra: Pair<String, Any?>) =
        SavedStateHandle(mapOf<String, Any?>(BookingEditorViewModel.ARG_ID to id, *extra))

    private fun vm(handle: SavedStateHandle, store: BookingStore) =
        BookingEditorViewModel(handle, store, 60) { today }

    // Regression: after process death the list was empty, the editor opened blank
    // for an existing id, and Save overwrote the stored booking with blanks.
    @Test fun saveIsBlockedUntilTheStoredBookingIsRead() = runTest(dispatcher) {
        val gate = CompletableDeferred<Booking?>()
        val editor = vm(handle(5), BookingStore { gate.await() })

        assertFalse(editor.isLoaded)
        assertTrue(editor.build() is BookingEditorViewModel.Result.Blocked)
        editor.update { it.copy(alias = "typed too early") } // ignored: nothing to edit yet
        assertTrue(editor.build() is BookingEditorViewModel.Result.Blocked)

        gate.complete(stored)
        advanceUntilIdle()

        assertTrue(editor.isLoaded)
        val ready = editor.build() as BookingEditorViewModel.Result.Ready
        assertEquals(stored, ready.booking) // same id, same fields: nothing blanked
    }

    @Test fun loadsByIdFromTheStoreNotFromAList() = runTest(dispatcher) {
        var asked = -1L
        val editor = vm(handle(5), BookingStore { asked = it; stored })
        advanceUntilIdle()
        assertEquals(5L, asked)
        assertEquals(BookingDraft.from(stored), editor.draft.value)
    }

    @Test fun missingBookingIsReportedAndNeverSavable() = runTest(dispatcher) {
        val editor = vm(handle(9), BookingStore { null })
        advanceUntilIdle()
        assertEquals(true, editor.notFound.value)
        assertFalse(editor.isLoaded)
        assertTrue(editor.build() is BookingEditorViewModel.Result.Blocked)
    }

    @Test fun editsSurviveProcessDeathAndAreNotClobberedByAReload() = runTest(dispatcher) {
        // First process: load, then the user edits.
        val first = handle(5)
        val editor = vm(first, BookingStore { stored })
        advanceUntilIdle()
        editor.update { it.copy(alias = "Edited", time = LocalTime.of(21, 15), status = BookingStatus.COMPLETED) }

        // Process death: only the SavedStateHandle contents survive.
        val restored = SavedStateHandle(
            mapOf(
                BookingEditorViewModel.ARG_ID to 5L,
                "draft" to first.get<BookingDraft>("draft"),
            ),
        )
        var storeCalls = 0
        val revived = vm(restored, BookingStore { storeCalls++; stored })
        advanceUntilIdle()

        assertEquals(0, storeCalls) // no reload to clobber the edits
        val ready = revived.build() as BookingEditorViewModel.Result.Ready
        assertEquals("Edited", ready.booking.clientAlias)
        assertEquals(LocalDateTime.of(2026, 10, 19, 21, 15), ready.booking.start)
        assertEquals(BookingStatus.COMPLETED, ready.booking.status)
        assertEquals(5L, ready.booking.id)
    }

    @Test fun newBookingIsReadyImmediatelyWithTodayAndDefaultDuration() = runTest(dispatcher) {
        val editor = vm(handle(0), BookingStore { error("a new booking reads nothing") })
        assertTrue(editor.isLoaded)
        val draft = editor.draft.value!!
        assertEquals(today, draft.date)
        assertEquals("60", draft.durationText)
        val blocked = editor.build() as BookingEditorViewModel.Result.Errors
        assertTrue(DraftError.ALIAS_REQUIRED in blocked.errors)
    }

    @Test fun invalidFormReportsErrorsAndSavesNothing() = runTest(dispatcher) {
        val editor = vm(handle(5), BookingStore { stored })
        advanceUntilIdle()
        editor.update { it.copy(alias = "", rateText = "abc", durationText = "0") }
        val errors = (editor.build() as BookingEditorViewModel.Result.Errors).errors
        assertEquals(setOf(DraftError.ALIAS_REQUIRED, DraftError.RATE_INVALID, DraftError.DURATION_INVALID), errors)
    }
}
