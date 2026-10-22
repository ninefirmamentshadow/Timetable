package com.sovereignops.timetable.applock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Process-wide lock state and the hooks a host activity calls. The lock screen
 * is a separate full-screen activity ([LockActivity]), so it also covers any
 * dialog the host had open. The host additionally hides its own content while
 * locked. FLAG_SECURE is the host's job (see the ui Privacy hook): it stays on
 * while the lock is enabled.
 */
object AppLock {

    /** Monotonic milliseconds; replaced in tests. */
    @Volatile
    var clock: () -> Long = { SystemClock.elapsedRealtime() }

    /** Builds the authenticator used by [LockActivity]; replaced in tests. */
    @Volatile
    var authenticatorFactory: AuthenticatorFactory = AuthenticatorFactory { SystemAuthenticator(it) }

    private var policy: LockPolicy? = null

    @Synchronized
    private fun policy(context: Context): LockPolicy {
        val settings = AppLockSettings(context)
        return policy ?: LockPolicy(settings.enabled, settings.timeoutMillis).also { policy = it }
    }

    /** True when the content must stay hidden right now. */
    @Synchronized
    fun isLocked(context: Context): Boolean {
        val p = policy(context)
        val settings = AppLockSettings(context)
        // Settings may have changed in another component; keep the policy in step.
        if (p.enabled != settings.enabled || p.timeoutMillis != settings.timeoutMillis) {
            p.configure(settings.enabled, settings.timeoutMillis)
        }
        return p.enabled && p.locked
    }

    /** The host came to the foreground. Starts [LockActivity] when the app must be locked. */
    @Synchronized
    fun onHostStarted(activity: Activity) {
        val p = policy(activity)
        isLocked(activity) // refresh from settings
        p.onReturned(clock())
        if (p.enabled && p.locked) {
            activity.startActivity(Intent(activity, LockActivity::class.java))
        }
    }

    /** The host left the foreground. [changingConfigurations] (rotation) is not leaving the app. */
    @Synchronized
    fun onHostStopped(activity: Activity, changingConfigurations: Boolean) {
        if (changingConfigurations) return
        policy(activity).onLeft(clock())
    }

    @Synchronized
    fun unlock(context: Context) {
        policy(context).unlock()
    }

    /** Lock settings changed in Settings; the person is in the app, so it stays unlocked. */
    @Synchronized
    fun settingsChanged(context: Context) {
        val s = AppLockSettings(context)
        policy(context).configure(s.enabled, s.timeoutMillis)
    }

    /** For tests: forget the process state, as a fresh process would. */
    @Synchronized
    fun resetForTesting() {
        policy = null
    }
}
