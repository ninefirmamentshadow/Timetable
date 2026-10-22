package com.sovereignops.timetable.reminders

import android.app.AlarmManager
import android.app.Application
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.materialswitch.MaterialSwitch
import com.sovereignops.timetable.R
import com.sovereignops.timetable.data.AppDatabase
import com.sovereignops.timetable.ui.MainActivity
import com.sovereignops.timetable.ui.TestSupport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ReminderSettingsScreenTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        TestSupport.fixClock()
        db = TestSupport.installInMemoryDatabase()
        TestSupport.seed(db)
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        ReminderSettings(app).apply { enabled = false; leadMinutes = 60; scheduledIds = emptySet() }
    }

    @After
    fun tearDown() {
        TestSupport.uninstallDatabase(db)
        TestSupport.restoreClock()
    }

    private fun alarms() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idle()

    @Test fun remindersAreOffByDefaultAndSwitchingOnSchedulesTheConfirmedBookings() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.openSettings() }
            scenario.onActivity { activity ->
                idle()
                val toggle = activity.findViewById<MaterialSwitch>(R.id.reminders_switch)
                assertFalse("default off", toggle.isChecked)
                assertTrue(alarms().isEmpty())
                toggle.performClick()
            }
            // The seeded confirmed booking on the 20th at 19:00 is the only future confirmed one.
            TestSupport.awaitUntil { alarms().size == 1 }
            assertTrue(ReminderSettings(app).enabled)
            scenario.onActivity { activity ->
                activity.findViewById<MaterialSwitch>(R.id.reminders_switch).performClick()
            }
            TestSupport.awaitUntil { alarms().isEmpty() }
            assertFalse(ReminderSettings(app).enabled)
        }
    }

    @Test fun changingTheLeadTimeIsSavedAndExplained() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.openSettings() }
            scenario.onActivity { activity ->
                idle()
                assertTrue(
                    activity.findViewById<TextView>(R.id.reminders_help).text.toString()
                        .startsWith("A reminder arrives 60 minutes before a confirmed booking plus your 45-minute travel buffer, so about 105 minutes ahead."),
                )
                val lead = activity.findViewById<EditText>(R.id.lead_input)
                lead.setText("30")
                lead.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
                assertEquals(30, ReminderSettings(app).leadMinutes)
                assertTrue(activity.findViewById<TextView>(R.id.reminders_help).text.toString().contains("about 75 minutes ahead"))
                lead.setText("5000")
                lead.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
                assertEquals("an out-of-range value is not saved", 30, ReminderSettings(app).leadMinutes)
            }
        }
    }
}
