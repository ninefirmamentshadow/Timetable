package com.sovereignops.timetable.applock

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.sovereignops.timetable.R
import com.sovereignops.timetable.databinding.ActivityLockBinding

/**
 * The lock screen: opaque, FLAG_SECURE, shows nothing of the app. It asks for the
 * device credential on its own and again on request. Back leaves the app, it does
 * not unlock. If the device no longer has a screen lock there is nothing to
 * authenticate against: the lock turns itself off rather than lock the person out.
 */
class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private lateinit var authenticator: Authenticator
    private var prompted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        authenticator = AppLock.authenticatorFactory.create(this) // registers result launchers: before STARTED
        binding.unlockButton.setOnClickListener { prompt() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
        prompted = savedInstanceState?.getBoolean(KEY_PROMPTED) == true
    }

    override fun onStart() {
        super.onStart()
        if (!authenticator.isAvailable()) {
            AppLockSettings(this).enabled = false
            AppLock.settingsChanged(this)
            Toast.makeText(this, R.string.lock_turned_off_no_screen_lock, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!prompted) {
            prompted = true
            prompt()
        }
    }

    private fun prompt() {
        authenticator.authenticate(getString(R.string.lock_prompt_title), getString(R.string.lock_prompt_subtitle)) { result ->
            if (result == AuthResult.SUCCESS) {
                AppLock.unlock(this)
                finish()
            } else {
                binding.lockStatus.setText(R.string.lock_failed)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_PROMPTED, prompted)
    }

    private companion object {
        const val KEY_PROMPTED = "prompted"
    }
}
