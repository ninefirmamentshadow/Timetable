package com.sovereignops.timetable.applock

/**
 * When the app is locked. Pure state machine: it never reads a clock, the caller
 * passes a monotonic millisecond reading.
 *
 * - A cold start is locked when the lock is enabled.
 * - Leaving the app while unlocked starts a timer; coming back after at least
 *   [timeoutMillis] locks it. A timeout of 0 locks whenever the app was left.
 * - While locked nothing is timed: the lock screen coming and going must not
 *   re-lock a successful unlock, however long the person took.
 * - A configuration change (rotation) is not leaving the app; the host skips it.
 */
class LockPolicy(var enabled: Boolean, var timeoutMillis: Long) {

    var locked: Boolean = enabled
        private set

    private var leftAt: Long? = null

    /** The app moved out of the foreground at [nowMillis]. */
    fun onLeft(nowMillis: Long) {
        if (!enabled || locked) return
        leftAt = nowMillis
    }

    /** The app came back to the foreground at [nowMillis]. Returns whether it is now locked. */
    fun onReturned(nowMillis: Long): Boolean {
        val left = leftAt
        leftAt = null
        if (enabled && !locked && left != null && nowMillis - left >= timeoutMillis) locked = true
        return locked
    }

    /** The person authenticated. */
    fun unlock() {
        locked = false
        leftAt = null
    }

    /** The lock was switched on or off in Settings (the person is in the app, so unlocked). */
    fun configure(enabled: Boolean, timeoutMillis: Long) {
        this.enabled = enabled
        this.timeoutMillis = timeoutMillis
        locked = false
        leftAt = null
    }
}
