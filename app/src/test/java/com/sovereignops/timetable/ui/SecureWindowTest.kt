package com.sovereignops.timetable.ui

import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sovereignops.timetable.R
import com.sovereignops.timetable.data.AppDatabase
import com.sovereignops.timetable.data.Settings
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
class SecureWindowTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = TestSupport.installInMemoryDatabase()
        Privacy.forcedOn = { false }
    }

    @After
    fun tearDown() {
        TestSupport.uninstallDatabase(db)
        Privacy.forcedOn = { false }
    }

    private fun secure(activity: MainActivity) =
        activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test fun screenshotsAreBlockedByDefault() {
        assertTrue(Settings(ApplicationProvider.getApplicationContext()).secureWindow)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(secure(it)) }
        }
    }

    @Test fun theToggleInSettingsClearsAndRestoresTheFlag() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.openSettings() }
            scenario.onActivity { activity ->
                shadowOf(android.os.Looper.getMainLooper()).idle()
                val toggle = activity.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.secure_switch)
                assertTrue(toggle.isChecked)
                toggle.performClick()
                assertFalse(Settings(activity).secureWindow)
                assertFalse(secure(activity))
                toggle.performClick()
                assertTrue(Settings(activity).secureWindow)
                assertTrue(secure(activity))
            }
        }
    }

    @Test fun anotherFeatureCanForceTheFlagOnEvenWhenTheSettingIsOff() {
        Settings(ApplicationProvider.getApplicationContext()).secureWindow = false
        Privacy.forcedOn = { true }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(secure(it)) }
        }
    }

    @Test fun settingOffWithNothingForcingItLeavesTheWindowOpenToScreenshots() {
        Settings(ApplicationProvider.getApplicationContext()).secureWindow = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertFalse(secure(it)) }
        }
        assertEquals(false, Settings(ApplicationProvider.getApplicationContext()).secureWindow)
    }
}
