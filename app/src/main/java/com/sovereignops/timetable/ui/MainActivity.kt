package com.sovereignops.timetable.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.sovereignops.timetable.R
import com.sovereignops.timetable.databinding.ActivityMainBinding
import com.sovereignops.timetable.ui.agenda.AgendaFragment
import com.sovereignops.timetable.ui.availability.AvailabilityFragment
import com.sovereignops.timetable.ui.earnings.EarningsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()

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

    private fun show(fragment: Fragment) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.container, fragment)
        }
    }
}
