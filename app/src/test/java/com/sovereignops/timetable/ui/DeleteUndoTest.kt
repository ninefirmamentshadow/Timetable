package com.sovereignops.timetable.ui

import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.sovereignops.timetable.R
import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.data.AppDatabase
import com.sovereignops.timetable.data.toEntity
import com.sovereignops.timetable.data.toModel
import com.sovereignops.timetable.ui.editor.BookingEditorFragment
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
import org.robolectric.shadows.ShadowDialog
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/** Deleting asks first and can be undone; availability windows can be edited and never overlap. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class DeleteUndoTest {

    private lateinit var db: AppDatabase

    private val booking = Booking(
        1, LocalDateTime.of(2026, 10, 21, 19, 0), 90, "Alias A", Money.dollars(125),
        BookingStatus.CONFIRMED, "Place 1", true, true, "note",
    )
    private val friday = AvailabilityWindow(1, DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.MIDNIGHT)

    @Before
    fun setUp() {
        TestSupport.fixClock()
        db = TestSupport.installInMemoryDatabase()
        runBlocking {
            db.bookingDao().insert(booking.toEntity())
            db.availabilityDao().insert(friday.toEntity())
        }
    }

    @After
    fun tearDown() {
        TestSupport.uninstallDatabase(db)
        TestSupport.restoreClock()
    }

    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idle()
    private fun storedBooking() = runBlocking { db.bookingDao().getById(1) }?.toModel()
    private fun storedWindows() = runBlocking { db.availabilityDao().getById(1) }?.toModel()
    private fun latestDialog() = ShadowDialog.getLatestDialog() as AlertDialog

    private fun snackbarAction(activity: MainActivity): Button? =
        activity.findViewById(com.google.android.material.R.id.snackbar_action)

    @Test fun deletingABookingAsksFirstThenUndoRestoresItExactly() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { BookingEditorFragment.newInstance(1).show(it.supportFragmentManager, "editor") }
            scenario.onActivity { activity ->
                TestSupport.awaitUntil {
                    val dialog = (activity.supportFragmentManager.findFragmentByTag("editor") as? BookingEditorFragment)?.dialog as? AlertDialog
                    dialog?.findViewById<android.widget.EditText>(R.id.alias_input)?.text.toString() == "Alias A"
                }
                val editor = (activity.supportFragmentManager.findFragmentByTag("editor") as BookingEditorFragment).dialog as AlertDialog
                editor.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
                idle()
                // A confirmation is showing and nothing is deleted yet.
                val confirm = latestDialog()
                assertTrue(confirm !== editor)
                assertEquals(booking, storedBooking())
                confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            TestSupport.awaitUntil { storedBooking() == null }
            scenario.onActivity { activity ->
                TestSupport.awaitUntil { snackbarAction(activity) != null }
                snackbarAction(activity)!!.performClick()
            }
            TestSupport.awaitUntil { storedBooking() != null }
            assertEquals("every field and the id come back", booking, storedBooking())
        }
    }

    @Test fun cancellingTheDeleteConfirmationDeletesNothing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { BookingEditorFragment.newInstance(1).show(it.supportFragmentManager, "editor") }
            scenario.onActivity { activity ->
                TestSupport.awaitUntil {
                    val dialog = (activity.supportFragmentManager.findFragmentByTag("editor") as? BookingEditorFragment)?.dialog as? AlertDialog
                    dialog?.findViewById<android.widget.EditText>(R.id.alias_input)?.text.toString() == "Alias A"
                }
                val editor = (activity.supportFragmentManager.findFragmentByTag("editor") as BookingEditorFragment).dialog as AlertDialog
                editor.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
                idle()
                latestDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                idle()
                assertEquals(booking, storedBooking())
                assertNull(snackbarAction(activity))
            }
        }
    }

    private fun openAvailabilityTab(activity: MainActivity) {
        activity.findViewById<BottomNavigationView>(R.id.bottom_nav).selectedItemId = R.id.nav_availability
        idle()
    }

    private fun windowRow(activity: MainActivity): RecyclerView.ViewHolder {
        lateinit var holder: RecyclerView.ViewHolder
        TestSupport.awaitUntil {
            val list = activity.findViewById<RecyclerView>(R.id.list)
            holder = list?.findViewHolderForAdapterPosition(0) ?: return@awaitUntil false
            true
        }
        return holder
    }

    @Test fun deletingAWindowAsksFirstThenUndoRestoresIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openAvailabilityTab(activity)
                windowRow(activity).itemView.findViewById<View>(R.id.delete_button).performClick()
                idle()
                assertEquals(friday, storedWindows()) // confirmation first
                latestDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            TestSupport.awaitUntil { storedWindows() == null }
            scenario.onActivity { activity ->
                TestSupport.awaitUntil { snackbarAction(activity) != null }
                snackbarAction(activity)!!.performClick()
            }
            TestSupport.awaitUntil { storedWindows() != null }
            assertEquals(friday, storedWindows())
        }
    }

    // Regression: windows could only be added or deleted, never edited.
    @Test fun tappingAWindowOpensItForEditingAndSavingKeepsTheSameRow() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openAvailabilityTab(activity)
                windowRow(activity).itemView.performClick()
                idle()
                val dialog = latestDialog()
                assertEquals("Friday", dialog.findViewById<android.widget.Spinner>(R.id.day_spinner)!!.selectedItem.toString())
                assertEquals("Midnight", dialog.findViewById<Button>(R.id.end_button)!!.text.toString())
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            idle()
            assertEquals(friday, storedWindows())
            val count = runBlocking { db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM availability").use { it.moveToFirst(); it.getInt(0) } }
            assertEquals(1, count)
        }
    }

    @Test fun overlappingWindowIsRefusedAndTheDialogStaysOpen() {
        runBlocking {
            // The add dialog defaults to Monday 18:00-23:00, so this window collides with it.
            db.availabilityDao().insert(
                AvailabilityWindow(2, DayOfWeek.MONDAY, LocalTime.of(20, 0), LocalTime.of(22, 0)).toEntity(),
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openAvailabilityTab(activity)
                TestSupport.awaitUntil { activity.findViewById<RecyclerView>(R.id.list).adapter!!.itemCount == 2 }
                activity.findViewById<View>(R.id.add_button).performClick()
                idle()
                val dialog = latestDialog()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                idle()
                assertTrue(dialog.isShowing)
                val error = dialog.findViewById<TextView>(R.id.error_text)!!
                assertEquals(View.VISIBLE, error.visibility)
                assertTrue(error.text.toString().startsWith("Overlaps another window: Monday"))
                assertNotNull(storedWindows())
            }
            val count = runBlocking { db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM availability").use { it.moveToFirst(); it.getInt(0) } }
            assertEquals("nothing was added", 2, count)
        }
    }
}
