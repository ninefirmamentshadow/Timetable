package com.sovereignops.timetable.applock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {

    private val timeout = 30_000L

    @Test fun aColdStartIsLockedOnlyWhenEnabled() {
        assertTrue(LockPolicy(enabled = true, timeoutMillis = timeout).locked)
        assertFalse(LockPolicy(enabled = false, timeoutMillis = timeout).locked)
    }

    @Test fun aDisabledLockNeverLocks() {
        val p = LockPolicy(enabled = false, timeoutMillis = timeout)
        p.onLeft(0)
        assertFalse(p.onReturned(10 * 60_000))
    }

    @Test fun backgroundShorterThanTheTimeoutStaysUnlocked() {
        val p = LockPolicy(true, timeout).apply { unlock() }
        p.onLeft(1_000)
        assertFalse(p.onReturned(1_000 + timeout - 1))
    }

    @Test fun backgroundForTheTimeoutOrLongerLocks() {
        val p = LockPolicy(true, timeout).apply { unlock() }
        p.onLeft(1_000)
        assertTrue("exactly the timeout locks", p.onReturned(1_000 + timeout))
        val q = LockPolicy(true, timeout).apply { unlock() }
        q.onLeft(0)
        assertTrue(q.onReturned(5 * 60_000))
    }

    @Test fun zeroTimeoutLocksWheneverTheAppWasLeft() {
        val p = LockPolicy(true, 0).apply { unlock() }
        p.onLeft(5_000)
        assertTrue(p.onReturned(5_000))
    }

    // Regression guard for a lock loop: the lock screen covering the app is itself a "leave".
    @Test fun leavingWhileLockedDoesNotTimeAndAnUnlockIsNeverRelockedByHowLongItTook() {
        val p = LockPolicy(true, timeout) // cold start, locked
        p.onLeft(0) // the lock screen covers the host
        assertTrue(p.onReturned(120_000)) // still locked, no timer involved
        p.unlock() // the person took two minutes to authenticate
        assertFalse(p.onReturned(121_000)) // the host returns after the lock screen finishes
    }

    @Test fun onlyTheLastLeaveCounts() {
        val p = LockPolicy(true, timeout).apply { unlock() }
        p.onLeft(0)
        assertFalse(p.onReturned(10_000))
        p.onLeft(100_000)
        assertFalse(p.onReturned(100_000 + 29_999))
    }

    @Test fun returningWithoutLeavingChangesNothing() {
        val p = LockPolicy(true, timeout).apply { unlock() }
        assertFalse(p.onReturned(999_999))
    }

    @Test fun switchingTheLockOnInSettingsLeavesTheAppUnlockedUntilItIsLeft() {
        val p = LockPolicy(false, timeout)
        p.configure(true, timeout)
        assertFalse(p.locked)
        p.onLeft(0)
        assertTrue(p.onReturned(timeout))
    }

    @Test fun switchingItOffUnlocks() {
        val p = LockPolicy(true, timeout)
        assertTrue(p.locked)
        p.configure(false, timeout)
        assertFalse(p.locked)
    }
}
