package com.sovereignops.timetable.ui

import android.view.View
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kizitonwose.calendar.view.CalendarView
import com.sovereignops.timetable.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import com.sovereignops.timetable.data.AppDatabase

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class CalendarScreenTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        TestSupport.fixClock()
        db = TestSupport.installInMemoryDatabase()
        TestSupport.seed(db)
    }

    @After
    fun tearDown() {
        TestSupport.uninstallDatabase(db)
        TestSupport.restoreClock()
    }

    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idle()

    @Test fun monthViewMarksDaysWithBookingsAndListsTheSelectedDay() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: MainViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
            scenario.onActivity { it.findViewById<View>(R.id.month_button).performClick() }
            TestSupport.awaitUntil { vm.bookings.value?.size == 5 }
            scenario.onActivity { activity ->
                idle()
                assertEquals("October 2026", activity.findViewById<TextView>(R.id.month_title).text.toString())
                // The selected day starts as today (20 Oct, one booking: Alias D).
                assertEquals("Tuesday, October 20, 2026", activity.findViewById<TextView>(R.id.day_title).text.toString())
                val list = activity.findViewById<RecyclerView>(R.id.list)
                TestSupport.awaitUntil { list.adapter!!.itemCount == 1 } // the diff is computed off the main thread
                assertEquals(View.GONE, activity.findViewById<View>(R.id.day_empty).visibility)
                assertTrue(activity.findViewById<CalendarView>(R.id.calendar).childCount > 0)
            }
        }
    }

    @Test fun backReturnsToTheAgenda() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.month_button).performClick() }
            scenario.onActivity { activity ->
                idle()
                assertTrue(activity.findViewById<View>(R.id.calendar) != null)
                activity.onBackPressedDispatcher.onBackPressed()
                idle()
                assertTrue(activity.findViewById<View>(R.id.calendar) == null)
                assertTrue(activity.findViewById<View>(R.id.add_button) != null)
            }
        }
    }
}
