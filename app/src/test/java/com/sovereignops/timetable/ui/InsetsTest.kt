package com.sovereignops.timetable.ui

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentContainerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.sovereignops.timetable.R
import com.sovereignops.timetable.data.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Edge-to-edge regression: synthetic status-bar, navigation-bar and cutout
 * insets are dispatched to the activity and the content area and the bottom bar
 * must be padded by exactly those amounts. Without the listener the content
 * draws under the system bars.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class InsetsTest {

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

    private fun dispatch(activity: MainActivity, bars: Insets, cutout: Insets = Insets.NONE) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), bars)
            .setInsets(WindowInsetsCompat.Type.displayCutout(), cutout)
            .build()
        ViewCompat.dispatchApplyWindowInsets(activity.findViewById<View>(R.id.root), insets)
    }

    @Test
    fun statusAndNavigationBarInsetsPadContentAndBottomBar() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                dispatch(activity, Insets.of(0, 72, 0, 96))

                val container = activity.findViewById<FragmentContainerView>(R.id.container)
                val bottomNav = activity.findViewById<BottomNavigationView>(R.id.bottom_nav)
                assertEquals("status bar pads the content top", 72, container.paddingTop)
                assertEquals("navigation bar pads the bottom bar", 96, bottomNav.paddingBottom)
                assertEquals(0, container.paddingBottom)
                assertEquals(0, bottomNav.paddingTop)
            }
        }
    }

    @Test
    fun sideInsetsFromCutoutPadBothBars() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                dispatch(activity, Insets.of(0, 0, 0, 0), cutout = Insets.of(60, 0, 40, 0))

                val container = activity.findViewById<FragmentContainerView>(R.id.container)
                val bottomNav = activity.findViewById<BottomNavigationView>(R.id.bottom_nav)
                assertEquals(60, container.paddingLeft)
                assertEquals(40, container.paddingRight)
                assertEquals(60, bottomNav.paddingLeft)
                assertEquals(40, bottomNav.paddingRight)
            }
        }
    }

    @Test
    fun clearingInsetsRemovesPadding() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                dispatch(activity, Insets.of(0, 72, 0, 96))
                dispatch(activity, Insets.NONE)

                val container = activity.findViewById<FragmentContainerView>(R.id.container)
                val bottomNav = activity.findViewById<BottomNavigationView>(R.id.bottom_nav)
                assertEquals(0, container.paddingTop)
                assertEquals(0, bottomNav.paddingBottom)
            }
        }
    }
}
