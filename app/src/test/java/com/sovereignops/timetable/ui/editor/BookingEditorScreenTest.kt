package com.sovereignops.timetable.ui.editor

import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sovereignops.timetable.R
import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.data.AppDatabase
import com.sovereignops.timetable.data.toEntity
import com.sovereignops.timetable.data.toModel
import com.sovereignops.timetable.ui.MainActivity
import com.sovereignops.timetable.ui.MainViewModel
import com.sovereignops.timetable.ui.TestSupport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import android.view.KeyEvent
import android.view.View
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class BookingEditorScreenTest {

    private lateinit var db: AppDatabase

    private val stored = Booking(
        1, LocalDateTime.of(2026, 10, 21, 19, 0), 90, "Alias A", Money.dollars(125),
        BookingStatus.CONFIRMED, "Place 1", true, true, "note",
    )

    @Before
    fun setUp() {
        TestSupport.fixClock()
        db = TestSupport.installInMemoryDatabase()
        runBlocking { db.bookingDao().insert(stored.toEntity()) }
    }

    @After
    fun tearDown() {
        TestSupport.uninstallDatabase(db)
        TestSupport.restoreClock()
    }

    private fun editorDialog(activity: MainActivity): AlertDialog? {
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val fragment = activity.supportFragmentManager.findFragmentByTag("editor") as? BookingEditorFragment
        return fragment?.dialog as? AlertDialog
    }

    private fun open(scenario: ActivityScenario<MainActivity>, id: Long) {
        scenario.onActivity { BookingEditorFragment.newInstance(id).show(it.supportFragmentManager, "editor") }
        scenario.onActivity { shadowOf(android.os.Looper.getMainLooper()).idle() }
    }

    @Test fun existingBookingIsReadByIdAndSaveIsEnabledOnceLoaded() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario, 1)
            scenario.onActivity { activity ->
                TestSupport.awaitUntil { editorDialog(activity)?.findViewById<EditText>(R.id.alias_input)?.text.toString() == "Alias A" }
                val dialog = editorDialog(activity)!!
                assertEquals("90", dialog.findViewById<EditText>(R.id.duration_input)!!.text.toString())
                assertEquals("$125", dialog.findViewById<EditText>(R.id.rate_input)!!.text.toString())
                assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            }
        }
    }

    // Regression: rotating the screen dropped the chosen date, time and status.
    @Test fun rotationKeepsEditsDateTimeAndStatus() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario, 1)
            scenario.onActivity { activity ->
                TestSupport.awaitUntil { editorDialog(activity)?.findViewById<EditText>(R.id.alias_input)?.text.toString() == "Alias A" }
                val editor = androidx.lifecycle.ViewModelProvider(
                    activity.supportFragmentManager.findFragmentByTag("editor")!!,
                )[BookingEditorViewModel::class.java]
                editor.update {
                    it.copy(alias = "Edited", time = java.time.LocalTime.of(22, 15), status = BookingStatus.NO_SHOW)
                }
                shadowOf(android.os.Looper.getMainLooper()).idle()
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                TestSupport.awaitUntil { editorDialog(activity)?.findViewById<EditText>(R.id.alias_input)?.text.toString() == "Edited" }
                val dialog = editorDialog(activity)!!
                assertEquals("10:15 PM", dialog.findViewById<Button>(R.id.time_button)!!.text.toString())
                val editor = ViewModelProvider(
                    activity.supportFragmentManager.findFragmentByTag("editor")!!,
                )[BookingEditorViewModel::class.java]
                val ready = editor.build() as BookingEditorViewModel.Result.Ready
                assertEquals(BookingStatus.NO_SHOW, ready.booking.status)
                assertEquals(1L, ready.booking.id)
            }
        }
    }

    @Test fun editorForAMissingBookingClosesInsteadOfOpeningBlank() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario, 404)
            scenario.onActivity { activity ->
                shadowOf(android.os.Looper.getMainLooper()).idle()
                assertNull(activity.supportFragmentManager.findFragmentByTag("editor"))
                val vm = ViewModelProvider(activity)[MainViewModel::class.java]
                assertNotNull(vm)
            }
        }
    }

    @Test fun savingTheLoadedEditorKeepsEveryStoredField() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario, 1)
            scenario.onActivity { activity ->
                TestSupport.awaitUntil { editorDialog(activity)?.findViewById<EditText>(R.id.alias_input)?.text.toString() == "Alias A" }
                val dialog = editorDialog(activity)!!
                // No other bookings or availability: no conflicts, so Save goes straight through.
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            TestSupport.awaitUntil { runBlocking { db.bookingDao().getById(1) }?.toModel() == stored }
        }
    }

    private fun pressBack(dialog: android.app.Dialog) {
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun typeNewBooking(activity: MainActivity, alias: String): AlertDialog {
        TestSupport.awaitUntil { editorDialog(activity)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled == true }
        val dialog = editorDialog(activity)!!
        dialog.findViewById<EditText>(R.id.alias_input)!!.setText(alias)
        dialog.findViewById<EditText>(R.id.rate_input)!!.setText("50")
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return dialog
    }

    // Regression: a tap outside the editor or Back closed it and threw away what was typed.
    @Test fun backWithUnsavedChangesAsksInsteadOfClosing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario, 0)
            scenario.onActivity { activity ->
                val dialog = typeNewBooking(activity, "Alias Back")
                val fragment = activity.supportFragmentManager.findFragmentByTag("editor") as BookingEditorFragment
                assertTrue(!fragment.isCancelable) // outside taps and Back never cancel it directly
                pressBack(dialog)
                assertNotNull(activity.supportFragmentManager.findFragmentByTag("editor"))
                val prompt = ShadowDialog.getLatestDialog() as AlertDialog
                assertEquals(activity.getString(R.string.keep_draft), prompt.getButton(AlertDialog.BUTTON_POSITIVE).text)
                // Keep editing: nothing closes, nothing is lost.
                prompt.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
                shadowOf(android.os.Looper.getMainLooper()).idle()
                assertEquals("Alias Back", editorDialog(activity)!!.findViewById<EditText>(R.id.alias_input)!!.text.toString())
            }
        }
    }

    @Test fun backWithNothingTypedClosesAtOnce() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario, 0)
            scenario.onActivity { activity ->
                TestSupport.awaitUntil { editorDialog(activity)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled == true }
                pressBack(editorDialog(activity)!!)
                assertNull(activity.supportFragmentManager.findFragmentByTag("editor"))
            }
        }
    }

    // A kept draft survives closing the editor and comes back through the Agenda banner.
    @Test fun aKeptDraftIsOfferedOnTheAgendaAndResumes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario, 0)
            scenario.onActivity { activity ->
                val dialog = typeNewBooking(activity, "Alias Later")
                TestSupport.awaitUntil { runBlocking { db.draftDao().get(0) }?.alias == "Alias Later" }
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                shadowOf(android.os.Looper.getMainLooper()).idle()
                (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                shadowOf(android.os.Looper.getMainLooper()).idle()
                assertNull(activity.supportFragmentManager.findFragmentByTag("editor"))

                TestSupport.awaitUntil { activity.findViewById<View>(R.id.draft_banner)?.visibility == View.VISIBLE }
                activity.findViewById<View>(R.id.draft_resume_button).performClick()
                TestSupport.awaitUntil {
                    editorDialog(activity)?.findViewById<EditText>(R.id.alias_input)?.text.toString() == "Alias Later"
                }
                assertEquals(View.VISIBLE, editorDialog(activity)!!.findViewById<View>(R.id.restored_note)!!.visibility)
            }
        }
    }

    @Test fun discardingRemovesTheKeptDraft() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario, 0)
            scenario.onActivity { activity ->
                val dialog = typeNewBooking(activity, "Alias Gone")
                TestSupport.awaitUntil { runBlocking { db.draftDao().get(0) } != null }
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                shadowOf(android.os.Looper.getMainLooper()).idle()
                (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            TestSupport.awaitUntil { runBlocking { db.draftDao().get(0) } == null }
        }
    }

    @Test fun savingANewBookingClosesOnlyAfterTheWriteAndLeavesNoDraft() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario, 0)
            scenario.onActivity { activity ->
                val dialog = typeNewBooking(activity, "Alias Saved")
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                TestSupport.awaitUntil { activity.supportFragmentManager.findFragmentByTag("editor") == null }
            }
            assertTrue(runBlocking { db.bookingDao().observeAllOnce() }.any { it.clientAlias == "Alias Saved" })
            TestSupport.awaitUntil { runBlocking { db.draftDao().get(0) } == null }
        }
    }

    // Regression: a double tap on + opened two editors on top of each other.
    @Test fun openingTheEditorTwiceShowsOne() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                BookingEditorFragment.show(activity.supportFragmentManager, 0)
                BookingEditorFragment.show(activity.supportFragmentManager, 0)
                shadowOf(android.os.Looper.getMainLooper()).idle()
                assertEquals(1, activity.supportFragmentManager.fragments.count { it is BookingEditorFragment })
            }
        }
    }
}

private suspend fun com.sovereignops.timetable.data.dao.BookingDao.observeAllOnce() = observeAll().first()
