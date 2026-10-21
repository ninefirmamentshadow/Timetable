package com.sovereignops.timetable.ui

import android.os.Bundle
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
import com.sovereignops.timetable.databinding.ActivityMainBinding
import com.sovereignops.timetable.ui.agenda.AgendaFragment
import com.sovereignops.timetable.ui.availability.AvailabilityFragment
import com.sovereignops.timetable.ui.earnings.EarningsFragment
import com.sovereignops.timetable.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()
        observeEvents()

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
