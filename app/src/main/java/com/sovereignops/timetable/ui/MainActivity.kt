package com.sovereignops.timetable.ui

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import com.sovereignops.timetable.R
import com.sovereignops.timetable.applock.AppLock
import com.sovereignops.timetable.applock.Authenticator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sovereignops.timetable.data.AppDatabase
import com.sovereignops.timetable.data.DatabaseEncryption
import com.sovereignops.timetable.data.DatabaseKeyUnavailable
import com.sovereignops.timetable.databinding.ActivityMainBinding
import com.sovereignops.timetable.ui.agenda.AgendaFragment
import com.sovereignops.timetable.ui.availability.AvailabilityFragment
import com.sovereignops.timetable.ui.calendar.CalendarFragment
import com.sovereignops.timetable.ui.earnings.EarningsFragment
import com.sovereignops.timetable.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Used by Settings to confirm the device credential before the app lock is switched on. */
    lateinit var authenticator: Authenticator
        private set
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (!databaseOpens()) return
        applySecureWindow()
        authenticator = AppLock.authenticatorFactory.create(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()
        observeEvents()
        if (AppLock.isLocked(this)) binding.root.visibility = View.INVISIBLE // never flash content before the lock

        if (savedInstanceState == null) {
            show(AgendaFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_agenda -> AgendaFragment()
                R.id.nav_availability -> AvailabilityFragment()
                R.id.nav_earnings -> EarningsFragment()
                else -> return@setOnItemSelectedListener false
            }
            show(fragment)
            true
        }
    }

    /**
     * Opens the database up front. If it is encrypted and its key is gone, nothing can be read:
     * say so plainly and offer a reset (then restore from a backup) instead of crashing.
     */
    private fun databaseOpens(): Boolean = try {
        AppDatabase.get(this)
        true
    } catch (e: DatabaseKeyUnavailable) {
        setTheme(R.style.Theme_Timetable)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.db_key_lost_title)
            .setMessage(R.string.db_key_lost_message)
            .setCancelable(false)
            .setPositiveButton(R.string.db_key_lost_reset) { _, _ ->
                DatabaseEncryption.resetEverything(this)
                finishAffinity()
            }
            .setNegativeButton(R.string.db_key_lost_close) { _, _ -> finishAffinity() }
            .showSecure()
        false
    }

    /** Re-applies FLAG_SECURE from the current settings (also called when the setting changes). */
    fun applySecureWindow() = Privacy.apply(window, this)

    /** A delete shows a snackbar whose Undo puts the item back. */
    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is UiEvent.Deleted -> {
                            val message = when (event.kind) {
                                UiEvent.Kind.BOOKING -> getString(R.string.booking_deleted, event.label)
                                UiEvent.Kind.AVAILABILITY_WINDOW -> getString(R.string.window_deleted, event.label)
                            }
                            Snackbar.make(binding.container, message, Snackbar.LENGTH_LONG)
                                .setAnchorView(binding.bottomNav)
                                .setAction(R.string.undo) { viewModel.undo(event.undoToken) }
                                .show()
                        }
                    }
                }
            }
        }
    }

    /**
     * Edge-to-edge: the status bar pads the content area from the top and the
     * navigation bar pads the bottom navigation, so nothing is drawn under them.
     * Display cutouts pad the sides.
     */
    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            binding.container.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.bottomNav.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun onStart() {
        super.onStart()
        if (!::binding.isInitialized) return // the database could not be opened; the dialog is showing
        AppLock.onHostStarted(this)
        updateLockedContent()
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return
        updateLockedContent()
    }

    override fun onStop() {
        if (::binding.isInitialized) AppLock.onHostStopped(this, isChangingConfigurations)
        super.onStop()
    }

    /** Content stays hidden (and out of accessibility) while the app is locked. */
    private fun updateLockedContent() {
        val locked = AppLock.isLocked(this)
        binding.root.visibility = if (locked) View.INVISIBLE else View.VISIBLE
        binding.root.importantForAccessibility =
            if (locked) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    }

    /** The month overview sits on top of the agenda; back returns to it. */
    fun openCalendar() {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.container, CalendarFragment())
            addToBackStack("calendar")
        }
    }

    /** Settings sits on top of the current tab; back returns to it. */
    fun openSettings() {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.container, SettingsFragment())
            addToBackStack("settings")
        }
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.container, fragment)
        }
    }
}
