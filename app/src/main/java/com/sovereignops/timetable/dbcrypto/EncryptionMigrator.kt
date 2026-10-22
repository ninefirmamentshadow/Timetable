package com.sovereignops.timetable.dbcrypto

import java.io.File

/** The native half of a conversion, behind an interface so the orchestration can be tested without SQLCipher. */
interface DatabaseConverter {
    /**
     * Write an encrypted copy of the plaintext database [plain] to [target] with the raw
     * 256-bit [key]: schema, every table, and the schema version (user_version). The
     * plaintext database's write-ahead log must be folded in first.
     */
    fun exportEncrypted(plain: File, target: File, key: ByteArray)

    /** Row count per table of [file]; [key] null means a plaintext file. */
    fun rowCounts(file: File, key: ByteArray?, tables: List<String>): Map<String, Long>
}

/** File operations the migrator needs; [RealFiles] is the platform implementation. */
interface FileOps {
    fun exists(file: File): Boolean
    fun rename(from: File, to: File): Boolean
    fun delete(file: File): Boolean
}

object RealFiles : FileOps {
    override fun exists(file: File) = file.exists()
    override fun rename(from: File, to: File) = from.renameTo(to)
    override fun delete(file: File) = !file.exists() || file.delete()
}

/** Where a failed conversion stopped. */
enum class MigrationStep { PRECHECK, EXPORT, VERIFY, SWAP, POST_VERIFY }

sealed interface MigrationResult {
    /** Encrypted and verified. [plaintextLeftBehind] is true when the plaintext copy could not be deleted yet. */
    data class Success(val plaintextLeftBehind: Boolean) : MigrationResult

    /** Nothing changed: the plaintext database is exactly as it was. */
    data class Failed(val step: MigrationStep, val message: String) : MigrationResult
}

/** What [EncryptionMigrator.recover] found and did after an interrupted conversion. */
enum class Recovery { NOTHING_TO_DO, REMOVED_INCOMPLETE_COPY, COMPLETED_CLEANUP, RESTORED_PLAINTEXT, UNRESOLVED }

/**
 * Converts a plaintext database to an encrypted one safely:
 *
 *  1. export to a temporary file (the plaintext file is untouched);
 *  2. verify the row count of every table, encrypted against plaintext;
 *  3. swap: plaintext moves aside, the encrypted file takes its place;
 *  4. verify again through the final path;
 *  5. only then delete the plaintext copy.
 *
 * Any failure before step 5 puts the plaintext database back exactly as it was and
 * reports the step. [recover] finishes or undoes a conversion that was cut short
 * (process killed between steps). The database must not be open while this runs.
 */
class EncryptionMigrator(
    private val converter: DatabaseConverter,
    private val tables: List<String>,
    private val files: FileOps = RealFiles,
) {
    fun migrate(db: File, key: ByteArray): MigrationResult {
        val tmp = tempFile(db)
        val bak = backupFile(db)
        if (DatabaseModes.detect(db) != DatabaseMode.PLAINTEXT) {
            return MigrationResult.Failed(MigrationStep.PRECHECK, "The database is not a plaintext database")
        }
        if (files.exists(bak) || files.exists(tmp)) {
            return MigrationResult.Failed(MigrationStep.PRECHECK, "A previous conversion has not been cleaned up")
        }

        val before = try {
            converter.exportEncrypted(db, tmp, key)
            converter.rowCounts(db, null, tables)
        } catch (e: Exception) {
            files.delete(tmp)
            return MigrationResult.Failed(MigrationStep.EXPORT, "The encrypted copy could not be written (${e.javaClass.simpleName})")
        }

        val copied = try {
            converter.rowCounts(tmp, key, tables)
        } catch (e: Exception) {
            files.delete(tmp)
            return MigrationResult.Failed(MigrationStep.VERIFY, "The encrypted copy could not be read back (${e.javaClass.simpleName})")
        }
        if (copied != before) {
            files.delete(tmp)
            return MigrationResult.Failed(MigrationStep.VERIFY, "Row counts differ between the plaintext and the encrypted copy")
        }

        // Swap. Sidecar files (-wal, -shm, -journal) travel with the plaintext file.
        if (!files.rename(db, bak)) {
            files.delete(tmp)
            return MigrationResult.Failed(MigrationStep.SWAP, "The database file could not be moved aside")
        }
        moveSidecars(db, bak)
        if (!files.rename(tmp, db)) {
            restore(db, bak)
            files.delete(tmp)
            return MigrationResult.Failed(MigrationStep.SWAP, "The encrypted file could not be put in place")
        }

        val after = try {
            converter.rowCounts(db, key, tables)
        } catch (e: Exception) {
            files.delete(db)
            restore(db, bak)
            return MigrationResult.Failed(MigrationStep.POST_VERIFY, "The encrypted database could not be opened (${e.javaClass.simpleName})")
        }
        if (after != before) {
            files.delete(db)
            restore(db, bak)
            return MigrationResult.Failed(MigrationStep.POST_VERIFY, "Row counts differ after the swap")
        }

        val removed = deleteBackup(bak)
        return MigrationResult.Success(plaintextLeftBehind = !removed)
    }

    /** Finish or undo an interrupted conversion. Call before opening the database. */
    fun recover(db: File, key: ByteArray?): Recovery {
        val tmp = tempFile(db)
        val bak = backupFile(db)
        var did = Recovery.NOTHING_TO_DO
        if (files.exists(tmp)) {
            files.delete(tmp)
            did = Recovery.REMOVED_INCOMPLETE_COPY
        }
        if (!files.exists(bak)) return did
        return when (DatabaseModes.detect(db)) {
            DatabaseMode.ABSENT -> { // cut off between moving the plaintext aside and moving the encrypted file in
                restore(db, bak)
                Recovery.RESTORED_PLAINTEXT
            }
            DatabaseMode.ENCRYPTED -> {
                val verified = key != null && try {
                    converter.rowCounts(db, key, tables).keys == tables.toSet()
                } catch (e: Exception) {
                    false
                }
                if (verified) {
                    deleteBackup(bak)
                    Recovery.COMPLETED_CLEANUP
                } else {
                    // The encrypted file cannot be proven good: put the plaintext back.
                    files.delete(db)
                    restore(db, bak)
                    Recovery.RESTORED_PLAINTEXT
                }
            }
            DatabaseMode.PLAINTEXT -> Recovery.UNRESOLVED // both exist; leave them for a person to look at
        }
    }

    private fun restore(db: File, bak: File) {
        files.rename(bak, db)
        for (suffix in SIDECARS) {
            val moved = File(bak.path + suffix)
            if (files.exists(moved)) files.rename(moved, File(db.path + suffix))
        }
    }

    private fun moveSidecars(db: File, bak: File) {
        for (suffix in SIDECARS) {
            val sidecar = File(db.path + suffix)
            if (files.exists(sidecar)) files.rename(sidecar, File(bak.path + suffix))
        }
    }

    private fun deleteBackup(bak: File): Boolean {
        var ok = files.delete(bak)
        for (suffix in SIDECARS) ok = files.delete(File(bak.path + suffix)) && ok
        return ok
    }

    companion object {
        private val SIDECARS = listOf("-wal", "-shm", "-journal")
        fun tempFile(db: File) = File(db.path + ".enc-tmp")
        fun backupFile(db: File) = File(db.path + ".plain-bak")
    }
}
