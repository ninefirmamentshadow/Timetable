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
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class BookingEditorScreenTest {

    private lateinit var db: AppDatabase

    private val stored = Booking(
        1, LocalDateTime.of(2026, 10, 21, 19, 0), 90, "Alias A", Money.dollars(300),
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
                assertEquals("$300", dialog.findViewById<EditText>(R.id.rate_input)!!.text.toString())
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
}
