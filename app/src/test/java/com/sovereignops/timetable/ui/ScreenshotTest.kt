package com.sovereignops.timetable.ui

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.checkRoboAccessibility
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils.matchesCheck
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils.matchesViews
import com.google.android.apps.common.testing.accessibility.framework.checks.TouchTargetSizeCheck
import org.hamcrest.Matchers.allOf
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.sovereignops.timetable.R
import com.sovereignops.timetable.data.AppDatabase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden screenshots and accessibility checks of the three tabs, in the dark
 * theme, at 320 dp and 411 dp wide and at font scale 1.0 and 2.0.
 *
 * Goldens live in app/src/test/screenshots. scripts/verify.sh compares against
 * them. After an intended UI change, push a commit whose message contains
 * [record-screenshots]: CI records them on its JDK 17 and commits the images to
 * the branch (goldens recorded outside CI went stale). Review them before merging.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenshotTest {

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

    private fun shoot(screen: String, navId: Int, widthDp: Int, fontScale: Float, settings: Boolean = false, calendar: Boolean = false) {
        val height = if (widthDp <= 320) 640 else 891
        RuntimeEnvironment.setQualifiers("w${widthDp}dp-h${height}dp-night-xxhdpi")
        RuntimeEnvironment.setFontScale(fontScale)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var viewModel: MainViewModel
            scenario.onActivity { activity ->
                viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
                // LiveData is lazy: activate both so the wait below sees real values.
                viewModel.bookings.observeForever { }
                viewModel.availability.observeForever { }
                activity.findViewById<BottomNavigationView>(R.id.bottom_nav).selectedItemId = navId
                if (settings) activity.openSettings()
                if (calendar) activity.openCalendar()
            }
            TestSupport.awaitUntil {
                viewModel.bookings.value != null && viewModel.availability.value != null
            }
            // The data has arrived; now wait for the screen to finish drawing it (list diffs
            // run off the main thread), so the capture never races the rows.
            scenario.onActivity { activity ->
                TestSupport.awaitSettled(activity.window.decorView)
                // Scroll bars are a transient overlay whose thumb size depends on content below the
                // fold (the version line wraps differently in the debug and release variants), so
                // they are left out of the goldens.
                hideScrollBars(activity.window.decorView)
            }
            val name = "${screen}_w${widthDp}_f${fontScale}"
            onView(isRoot()).captureRoboImage("src/test/screenshots/$name.png")
            onView(isRoot()).checkRoboAccessibility(
                roborazziATFAccessibilityCheckOptions = RoborazziATFAccessibilityCheckOptions(
                    checker = RoborazziATFAccessibilityChecker(
                        preset = AccessibilityCheckPreset.LATEST,
                        // A seven-column month grid is 46 dp per cell at 320 dp wide, under the 48 dp
                        // guideline. This is the only suppression: each cell carries a full spoken
                        // description and the same day is reachable by paging and the day list below.
                        suppressions = allOf<AccessibilityViewCheckResult>(
                            matchesCheck(TouchTargetSizeCheck::class.java),
                            matchesViews(withId(R.id.calendar_day_cell)),
                        ),
                    ),
                    failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Error,
                ),
            )
        }
    }

    private fun hideScrollBars(view: android.view.View) {
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) hideScrollBars(view.getChildAt(i))
    }

    @Test fun agenda_w320_f1() = shoot("agenda", R.id.nav_agenda, 320, 1.0f)
    @Test fun agenda_w320_f2() = shoot("agenda", R.id.nav_agenda, 320, 2.0f)
    @Test fun agenda_w411_f1() = shoot("agenda", R.id.nav_agenda, 411, 1.0f)
    @Test fun agenda_w411_f2() = shoot("agenda", R.id.nav_agenda, 411, 2.0f)

    @Test fun availability_w320_f1() = shoot("availability", R.id.nav_availability, 320, 1.0f)
    @Test fun availability_w320_f2() = shoot("availability", R.id.nav_availability, 320, 2.0f)
    @Test fun availability_w411_f1() = shoot("availability", R.id.nav_availability, 411, 1.0f)
    @Test fun availability_w411_f2() = shoot("availability", R.id.nav_availability, 411, 2.0f)

    @Test fun earnings_w320_f1() = shoot("earnings", R.id.nav_earnings, 320, 1.0f)
    @Test fun earnings_w320_f2() = shoot("earnings", R.id.nav_earnings, 320, 2.0f)
    @Test fun earnings_w411_f1() = shoot("earnings", R.id.nav_earnings, 411, 1.0f)
    @Test fun earnings_w411_f2() = shoot("earnings", R.id.nav_earnings, 411, 2.0f)

    @Test fun settings_w320_f1() = shoot("settings", R.id.nav_agenda, 320, 1.0f, settings = true)
    @Test fun settings_w320_f2() = shoot("settings", R.id.nav_agenda, 320, 2.0f, settings = true)
    @Test fun settings_w411_f1() = shoot("settings", R.id.nav_agenda, 411, 1.0f, settings = true)
    @Test fun settings_w411_f2() = shoot("settings", R.id.nav_agenda, 411, 2.0f, settings = true)

    @Test fun calendar_w320_f1() = shoot("calendar", R.id.nav_agenda, 320, 1.0f, calendar = true)
    @Test fun calendar_w320_f2() = shoot("calendar", R.id.nav_agenda, 320, 2.0f, calendar = true)
    @Test fun calendar_w411_f1() = shoot("calendar", R.id.nav_agenda, 411, 1.0f, calendar = true)
    @Test fun calendar_w411_f2() = shoot("calendar", R.id.nav_agenda, 411, 2.0f, calendar = true)
}
