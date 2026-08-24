package com.sovereignops.timetable.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

    private fun show(fragment: Fragment) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.container, fragment)
        }
    }
}
