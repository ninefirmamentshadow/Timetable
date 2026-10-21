package com.sovereignops.timetable.data.backup

/**
 * Remembers, for this process only, that an encrypted backup was written. The
 * "Encrypt database" action is offered only after one, so there is always a
 * copy outside the database before it is converted. Deliberately not persisted:
 * after the app restarts, make a fresh backup first.
 */
object BackupSession {
    @Volatile
    var backedUpThisSession: Boolean = false
        private set

    fun markBackedUp() {
        backedUpThisSession = true
    }

    /** For tests. */
    fun reset() {
        backedUpThisSession = false
    }
}
