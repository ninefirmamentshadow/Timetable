package com.sovereignops.timetable.applock

import android.app.Application
import android.content.Intent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.materialswitch.MaterialSwitch
import com.sovereignops.timetable.R
import com.sovereignops.timetable.data.AppDatabase
import com.sovereignops.timetable.data.Settings
import com.sovereignops.timetable.ui.MainActivity
import com.sovereignops.timetable.ui.Privacy
import com.sovereignops.timetable.ui.TestSupport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AppLockTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private var now = 0L

    /** A stand-in for the system prompt: records calls and answers as told. */
    private class FakeAuthenticator(var available: Boolean = true, var result: AuthResult = AuthResult.SUCCESS) : Authenticator {
        var prompts = 0
        var pending: ((AuthResult) -> Unit)? = null
        var answerImmediately = true
        override fun isAvailable() = available
        override fun authenticate(title: String, subtitle: String, onResult: (AuthResult) -> Unit) {
            prompts++
            if (answerImmediately) onResult(result) else pending = onResult
        }
    }

    private lateinit var fake: FakeAuthenticator

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        TestSupport.fixClock()
        db = TestSupport.installInMemoryDatabase()
        AppLock.resetForTesting()
        now = 1_000_000
        AppLock.clock = { now }
        fake = FakeAuthenticator()
        AppLock.authenticatorFactory = AuthenticatorFactory { fake }
        Privacy.forcedOn = { context -> AppLockSettings(context).enabled }
        AppLockSettings(app).apply { enabled = false; timeoutSeconds = 30 }
    }

    @After
    fun tearDown() {
        TestSupport.uninstallDatabase(db)
        TestSupport.restoreClock()
        AppLock.resetForTesting()
        AppLock.clock = { android.os.SystemClock.elapsedRealtime() }
        AppLock.authenticatorFactory = AuthenticatorFactory { SystemAuthenticator(it) }
        Privacy.forcedOn = { false }
    }

    private fun nextActivity(activity: AppCompatActivity): Intent? = shadowOf(activity).nextStartedActivity
    private fun isLockScreen(intent: Intent?) = intent?.component?.className == LockActivity::class.java.name

    // --- the lock is opt-in ---------------------------------------------------

    @Test fun offByDefaultTheAppOpensWithoutALockScreen() {
        assertFalse(AppLockSettings(app).enabled)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNull(nextActivity(activity))
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.root).visibility)
            }
        }
    }

    // --- cold start -----------------------------------------------------------

    @Test fun whenEnabledAColdStartShowsTheLockScreenAndHidesTheContent() {
        AppLockSettings(app).enabled = true
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(isLockScreen(nextActivity(activity)))
                assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.root).visibility)
                assertEquals(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                    activity.findViewById<View>(R.id.root).importantForAccessibility,
                )
            }
        }
    }

    // --- the lock screen ------------------------------------------------------

    @Test fun theLockScreenPromptsOnItsOwnAndUnlocksOnSuccess() {
        AppLockSettings(app).enabled = true
        val controller = Robolectric.buildActivity(LockActivity::class.java).create().start().resume()
        val activity = controller.get()
        assertEquals(1, fake.prompts)
        assertTrue("success unlocks", !AppLock.isLocked(app))
        assertTrue("and the lock screen closes", activity.isFinishing)
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }

    @Test fun aFailedOrCancelledPromptLeavesItLockedWithARetryButton() {
        AppLockSettings(app).enabled = true
        fake.result = AuthResult.FAILED
        val activity = Robolectric.buildActivity(LockActivity::class.java).create().start().resume().get()
        assertTrue(AppLock.isLocked(app))
        assertFalse(activity.isFinishing)
        assertEquals("Not unlocked. Try again.", activity.findViewById<TextView>(R.id.lock_status).text.toString())

        fake.result = AuthResult.SUCCESS
        activity.findViewById<View>(R.id.unlock_button).performClick()
        assertEquals(2, fake.prompts)
        assertFalse(AppLock.isLocked(app))
        assertTrue(activity.isFinishing)
    }

    @Test fun backOnTheLockScreenDoesNotUnlock() {
        AppLockSettings(app).enabled = true
        fake.result = AuthResult.FAILED
        val activity = Robolectric.buildActivity(LockActivity::class.java).create().start().resume().get()
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(AppLock.isLocked(app))
    }

    @Test fun withoutAScreenLockTheLockTurnsItselfOffInsteadOfLockingThePersonOut() {
        AppLockSettings(app).enabled = true
        fake.available = false
        val activity = Robolectric.buildActivity(LockActivity::class.java).create().start().resume().get()
        assertFalse(AppLockSettings(app).enabled)
        assertFalse(AppLock.isLocked(app))
        assertTrue(activity.isFinishing)
        assertEquals(0, fake.prompts)
    }

    // --- timeout --------------------------------------------------------------

    @Test fun backgroundForLessThanTheTimeoutDoesNotRelockAndLongerDoes() {
        AppLockSettings(app).apply { enabled = true; timeoutSeconds = 30 }
        AppLock.unlock(app) // the person has already unlocked this process

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // The first launch: unlocked, so no lock screen.
            scenario.onActivity { assertNull(nextActivity(it)) }

            // Leave for 10 s, come back: still unlocked.
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED) // stopped
            now += 10_000
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            scenario.onActivity { assertNull(nextActivity(it)) }

            // Leave for 31 s: locked on return.
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            now += 31_000
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertTrue(isLockScreen(nextActivity(activity)))
                assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.root).visibility)
            }
        }
    }

    @Test fun rotationIsNotLeavingTheApp() {
        AppLockSettings(app).apply { enabled = true; timeoutSeconds = 0 } // lock on any leave
        AppLock.unlock(app)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            scenario.onActivity { activity ->
                assertNull("recreate must not lock", nextActivity(activity))
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.root).visibility)
            }
        }
    }

    // --- screenshots stay blocked while the lock is on -------------------------

    @Test fun screenshotsStayBlockedWhileTheLockIsOnEvenIfThePrivacySwitchIsOff() {
        Settings(app).secureWindow = false
        AppLockSettings(app).enabled = true
        AppLock.unlock(app)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
        }
    }

    // --- Settings -------------------------------------------------------------

    private fun openSettings(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { it.openSettings() }
        scenario.onActivity { shadowOf(android.os.Looper.getMainLooper()).idle() }
    }

    @Test fun enablingTheLockProvesTheCredentialFirstAndThenTurnsItOn() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openSettings(scenario)
            scenario.onActivity { activity ->
                activity.findViewById<MaterialSwitch>(R.id.lock_switch).performClick()
                assertEquals("the prompt ran before enabling", 1, fake.prompts)
                assertTrue(AppLockSettings(app).enabled)
                assertFalse("the person is in the app, so it stays unlocked", AppLock.isLocked(app))
            }
        }
    }

    @Test fun aFailedConfirmationLeavesTheLockOff() {
        fake.result = AuthResult.FAILED
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openSettings(scenario)
            scenario.onActivity { activity ->
                val toggle = activity.findViewById<MaterialSwitch>(R.id.lock_switch)
                toggle.performClick()
                assertFalse(AppLockSettings(app).enabled)
                assertFalse(toggle.isChecked)
            }
        }
    }

    @Test fun noScreenLockMeansTheLockCannotBeEnabled() {
        fake.available = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openSettings(scenario)
            scenario.onActivity { activity ->
                val toggle = activity.findViewById<MaterialSwitch>(R.id.lock_switch)
                toggle.performClick()
                assertFalse(AppLockSettings(app).enabled)
                assertFalse(toggle.isChecked)
                assertEquals(0, fake.prompts)
            }
        }
    }

    @Test fun theTimeoutIsSavedAndDefaultsToThirtySeconds() {
        assertEquals(30, AppLockSettings(app).timeoutSeconds)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openSettings(scenario)
            scenario.onActivity { activity ->
                val input = activity.findViewById<android.widget.EditText>(R.id.lock_timeout_input)
                assertEquals("30", input.text.toString())
                input.setText("90")
                input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
                assertEquals(90, AppLockSettings(app).timeoutSeconds)
                input.setText("9999")
                input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
                assertEquals("out of range is not saved", 90, AppLockSettings(app).timeoutSeconds)
            }
        }
        assertNotNull(app)
    }
}
