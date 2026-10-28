package com.sovereignops.timetable.data.backup

import com.sovereignops.timetable.backup.BackupEnvelope
import com.sovereignops.timetable.backup.BackupException
import com.sovereignops.timetable.data.AppDatabase
import com.sovereignops.timetable.data.Settings
import com.sovereignops.timetable.data.toModelResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Create and restore encrypted Timetable backups. Restore is two steps so the
 * caller can ask before anything changes: [prepareRestore] decrypts and
 * validates and writes nothing; [apply] writes in a single transaction.
 */
class BackupService(
    private val db: AppDatabase,
    private val settings: Settings,
    private val now: () -> LocalDateTime,
    private val iterations: Int = BackupEnvelope.DEFAULT_ITERATIONS,
) {
    private val envelope = BackupEnvelope(APP_ID)

    class Created(val text: String, val bookings: Int, val windows: Int, val unreadableSkipped: Int, val drafts: Int = 0)

    /** Build the encrypted text for the current data. Reads only. */
    suspend fun create(passphrase: String): Created {
        val bookingRows = db.backupDao().allBookings()
        val windowRows = db.backupDao().allAvailability()
        val drafts = db.backupDao().allDrafts()
        val goodBookings = bookingRows.filter { it.toModelResult().isSuccess }
        val goodWindows = windowRows.filter { it.toModelResult().isSuccess }
        val payload = BackupPayloadCodec.toPayload(
            goodBookings, goodWindows, settings.travelBufferMinutes, settings.defaultDurationMinutes, now(), drafts,
        )
        val text = withContext(Dispatchers.Default) {
            envelope.seal(BackupPayloadCodec.encode(payload), BackupPayloadCodec.SCHEMA, passphrase, iterations)
        }
        val skipped = (bookingRows.size - goodBookings.size) + (windowRows.size - goodWindows.size)
        return Created(text, goodBookings.size, goodWindows.size, skipped, drafts.size)
    }

    /** Check the file's identity without a passphrase (cheap, no key derivation). */
    fun inspect(text: String): BackupEnvelope.Header = envelope.inspect(text)

    /**
     * Decrypt and validate. Throws [BackupException] (wrong passphrase, tampered
     * file, wrong app, not a backup) or [InvalidBackup]; in every case nothing was written.
     */
    suspend fun prepareRestore(text: String, passphrase: String): RestorePlan {
        val opened = withContext(Dispatchers.Default) { envelope.open(text, passphrase) }
        if (opened.schema !in 1..BackupPayloadCodec.SCHEMA) {
            throw BackupException.Unsupported("This backup was made by a newer version of the app")
        }
        return BackupPayloadCodec.decode(opened.plaintext)
    }

    /**
     * Replace everything (bookings, availability, drafts) with [plan] in one
     * transaction, then apply the two settings. A schema 1 backup has no drafts, so
     * the drafts on the device are replaced by none; the safety copy keeps them.
     */
    suspend fun apply(plan: RestorePlan) {
        db.backupDao().replaceAll(plan.bookings, plan.availability, plan.drafts)
        settings.travelBufferMinutes = plan.travelBufferMinutes
        settings.defaultDurationMinutes = plan.defaultDurationMinutes
    }

    companion object {
        const val APP_ID = "timetable"
    }
}
