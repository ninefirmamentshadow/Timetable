package com.sovereignops.timetable.ui.agenda

import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sovereignops.timetable.R
import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.data.AppDatabase
import com.sovereignops.timetable.data.toEntity
import com.sovereignops.timetable.ui.MainActivity
import com.sovereignops.timetable.ui.MainViewModel
import com.sovereignops.timetable.ui.TestSupport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AgendaScreenTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        TestSupport.fixClock()
        db = TestSupport.installInMemoryDatabase()
    }

    @After
    fun tearDown() {
        TestSupport.uninstallDatabase(db)
        TestSupport.restoreClock()
    }

    private fun insert(id: Long, start: LocalDateTime, status: BookingStatus) = runBlocking {
        db.bookingDao().insert(
            Booking(id, start, 60, "Alias $id", Money.dollars(125), status).toEntity(),
        )
    }

    private fun texts(activity: MainActivity): List<String> {
        val list = activity.findViewById<RecyclerView>(R.id.list)
        val out = mutableListOf<String>()
        for (i in 0 until (list.adapter?.itemCount ?: 0)) {
            val holder = list.findViewHolderForAdapterPosition(i) ?: continue
            holder.itemView.findViewById<TextView>(R.id.section_label)?.let { out += it.text.toString() }
            holder.itemView.findViewById<TextView>(R.id.alias_label)?.let { out += it.text.toString() }
        }
        return out
    }

    /** ListAdapter computes its diff on a background thread, so poll instead of asserting at once. */
    private fun awaitTexts(activity: MainActivity, expected: List<String>) {
        var last = emptyList<String>()
        try {
            TestSupport.awaitUntil { last = texts(activity); last == expected }
        } catch (e: IllegalStateException) {
            assertEquals(expected, last)
        }
    }

    // Regression: past bookings were never shown on the agenda.
    @Test fun pastAndOverdueBookingsAreShownInTheirSections() {
        insert(1, TestSupport.NOW.minusDays(3), BookingStatus.CONFIRMED) // overdue, unresolved
        insert(2, TestSupport.NOW.minusDays(1), BookingStatus.COMPLETED)
        insert(3, TestSupport.NOW.plusHours(5), BookingStatus.CONFIRMED)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: MainViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
            TestSupport.awaitUntil { vm.bookings.value?.size == 3 }
            scenario.onActivity { activity ->
                awaitTexts(activity, listOf("Needs review", "Alias 1", "Upcoming", "Alias 3", "Past and closed", "Alias 2"))
                assertEquals(
                    "1 booking needs review",
                    activity.findViewById<TextView>(R.id.review_summary).text.toString(),
                )
            }
        }
    }

    // Regression: marking an overdue booking recorded its outcome and moved it out of review.
    @Test fun markingAnOverdueBookingCompletedMovesItToPastAndEarned() {
        insert(1, TestSupport.NOW.minusDays(3), BookingStatus.CONFIRMED)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: MainViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
            TestSupport.awaitUntil { vm.bookings.value?.size == 1 }

            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.list)
                TestSupport.awaitUntil {
                    (0 until list.adapter!!.itemCount).any {
                        list.findViewHolderForAdapterPosition(it)?.itemView?.findViewById<TextView>(R.id.alias_label) != null
                    }
                }
                val item = (0 until list.adapter!!.itemCount)
                    .mapNotNull { list.findViewHolderForAdapterPosition(it) }
                    .first { it.itemView.findViewById<TextView>(R.id.alias_label) != null }
                item.itemView.performClick()
                val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
                assertTrue("resolve dialog is shown", dialog.isShowing)
                // 0 = Mark completed
                dialog.listView.performItemClick(null, 0, 0)
            }
            TestSupport.awaitUntil { vm.bookings.value?.single()?.status == BookingStatus.COMPLETED }
            scenario.onActivity { activity ->
                shadowOf(android.os.Looper.getMainLooper()).idle()
                awaitTexts(activity, listOf("Past and closed", "Alias 1"))
                assertEquals(android.view.View.GONE, activity.findViewById<TextView>(R.id.review_summary).visibility)
            }
        }
    }

    // Regression: one bad row crashed the list; now it is skipped and counted.
    @Test fun anUnreadableRowDoesNotCrashTheAgendaAndIsCounted() {
        insert(1, TestSupport.NOW.plusHours(5), BookingStatus.CONFIRMED)
        runBlocking {
            db.bookingDao().insert(
                com.sovereignops.timetable.data.entity.BookingEntity(
                    0, 1_792_000_000, 0, "Bad", 100, "CONFIRMED", "", false, false, "",
                ),
            )
            db.bookingDao().insert(
                com.sovereignops.timetable.data.entity.BookingEntity(
                    0, 1_792_000_000, 60, "Bad", 100, "???", "", false, false, "",
                ),
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: MainViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
            TestSupport.awaitUntil { vm.bookings.value?.size == 1 && vm.unreadableBookings.value == 2 }
            scenario.onActivity { activity ->
                awaitTexts(activity, listOf("Upcoming", "Alias 1"))
                val banner = activity.findViewById<TextView>(R.id.unreadable_banner)
                assertEquals(android.view.View.VISIBLE, banner.visibility)
                assertEquals("2 unreadable records are hidden. Nothing was deleted.", banner.text.toString())
            }
        }
    }
}
