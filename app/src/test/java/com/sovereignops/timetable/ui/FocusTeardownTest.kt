package com.sovereignops.timetable.ui

import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.sovereignops.timetable.R
import com.sovereignops.timetable.data.AppDatabase
import com.sovereignops.timetable.data.Settings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Regression: a numeric settings field that still had focus when its screen went
 * away ran its focus listener after the view was destroyed and crashed on the
 * cleared binding. The value typed is kept, not lost.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class FocusTeardownTest {

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

    @Test fun leavingTheAvailabilityTabWithTheBufferFocusedKeepsTheValueAndDoesNotCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val nav = activity.findViewById<BottomNavigationView>(R.id.bottom_nav)
                nav.selectedItemId = R.id.nav_availability
                shadowOf(android.os.Looper.getMainLooper()).idle()
                val input = activity.findViewById<EditText>(R.id.buffer_input)
                input.requestFocus()
                input.setText("30")
                nav.selectedItemId = R.id.nav_agenda
                shadowOf(android.os.Looper.getMainLooper()).idle()
                assertEquals(30, Settings(activity).travelBufferMinutes)
            }
        }
    }

    @Test fun leavingSettingsWithTheLeadTimeFocusedDoesNotCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.openSettings()
                shadowOf(android.os.Looper.getMainLooper()).idle()
                val input = activity.findViewById<EditText>(R.id.lead_input)
                input.requestFocus()
                input.setText("15")
                activity.onBackPressedDispatcher.onBackPressed()
                shadowOf(android.os.Looper.getMainLooper()).idle()
                assertEquals(
                    15,
                    com.sovereignops.timetable.reminders.ReminderSettings(activity).leadMinutes,
                )
            }
        }
    }
}
