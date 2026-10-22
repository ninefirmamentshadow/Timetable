package com.sovereignops.timetable.dbcrypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The orchestration, with fake files standing in for the databases. A "plaintext
 * database" is the SQLite header followed by `table=count` lines; an "encrypted
 * one" is junk bytes, the key, then the same lines. The native SQLCipher path is
 * NOT exercised here (it cannot run on the JVM).
 */
class EncryptionMigratorTest {

    @get:Rule val tmp = TemporaryFolder()

    private val tables = listOf("bookings", "availability", "room_master_table")
    private val header = "SQLite format 3\u0000"
    private val key = ByteArray(32) { (it * 7).toByte() }
    private lateinit var db: File
    private val log = mutableListOf<String>()

    private fun counts(bookings: Int = 5, availability: Int = 2) =
        "bookings=$bookings\navailability=$availability\nroom_master_table=1\n"

    private fun writePlain(f: File, content: String = counts()) = f.writeBytes((header + "\n" + content).toByteArray(Charsets.ISO_8859_1))

    @Before fun setUp() {
        db = File(tmp.root, "timetable.db")
        writePlain(db)
    }

    /** Fake native side with failure switches. */
    private inner class FakeConverter : DatabaseConverter {
        var failExport = false
        var failReadTemp = false
        var tempCountsOffByOne = false
        var failReadFinal = false
        var finalCountsOffByOne = false

        override fun exportEncrypted(plain: File, target: File, key: ByteArray) {
            log += "export"
            if (failExport) {
                target.writeBytes(byteArrayOf(1, 2, 3)) // a half-written file
                throw IllegalStateException("disk full")
            }
            var body = plain.readText(Charsets.ISO_8859_1).removePrefix(header + "\n")
            if (tempCountsOffByOne) body = body.replace("bookings=5", "bookings=4")
            target.writeBytes(ByteArray(32) { 0x7f } + "KEY:${key.toList()}\n$body".toByteArray(Charsets.ISO_8859_1))
        }

        override fun rowCounts(file: File, key: ByteArray?, tables: List<String>): Map<String, Long> {
            val isTemp = file.name.endsWith(".enc-tmp")
            log += if (key == null) "counts(plain)" else if (isTemp) "counts(temp)" else "counts(final)"
            if (isTemp && failReadTemp) throw IllegalStateException("cannot open")
            if (!isTemp && key != null && failReadFinal) throw IllegalStateException("cannot open")
            val text = file.readBytes().toString(Charsets.ISO_8859_1)
            if (key == null) check(text.startsWith(header)) { "not plaintext" } else {
                check(!text.startsWith(header)) { "not encrypted" }
                check(text.contains("KEY:${key.toList()}")) { "wrong key" }
            }
            val map = text.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=').toLong() }
            val adjusted = if (!isTemp && key != null && finalCountsOffByOne) map + ("bookings" to map.getValue("bookings") - 1) else map
            return tables.associateWith { adjusted.getValue(it) }
        }
    }

    /** Real files, but each operation can be made to fail and every call is logged. */
    private inner class FlakyFiles(var failRenameFrom: Set<String> = emptySet(), var failDelete: Set<String> = emptySet()) : FileOps {
        override fun exists(file: File) = file.exists()
        override fun rename(from: File, to: File): Boolean {
            log += "rename ${from.name} -> ${to.name}"
            return if (from.name in failRenameFrom && !failedOnce(from.name)) false else from.renameTo(to)
        }
        private val failed = mutableSetOf<String>()
        private fun failedOnce(name: String): Boolean = !failed.add(name) // fail only the first attempt per name
        override fun delete(file: File): Boolean {
            log += "delete ${file.name}"
            return if (file.name in failDelete) false else (!file.exists() || file.delete())
        }
    }

    private fun migrator(converter: DatabaseConverter = FakeConverter(), files: FileOps = FlakyFiles()) =
        EncryptionMigrator(converter, tables, files)

    private fun plainBytes() = db.readBytes()
    private fun assertNoLeftovers() {
        assertFalse(File(db.path + ".enc-tmp").exists())
        assertFalse(File(db.path + ".plain-bak").exists())
    }

    // --- success -------------------------------------------------------------

    @Test fun successLeavesAnEncryptedDatabaseAndNoPlaintext() {
        val result = migrator().migrate(db, key)
        assertEquals(MigrationResult.Success(plaintextLeftBehind = false), result)
        assertEquals(DatabaseMode.ENCRYPTED, DatabaseModes.detect(db))
        assertNoLeftovers()
        assertTrue(db.readText(Charsets.ISO_8859_1).endsWith(counts())) // contents carried over
    }

    @Test fun thePlaintextIsDeletedOnlyAfterEveryVerification() {
        migrator().migrate(db, key)
        val deleteBackup = log.indexOfFirst { it == "delete timetable.db.plain-bak" }
        assertTrue(deleteBackup > 0)
        assertEquals("counts(plain)", log[log.indexOf("export") + 1])
        assertTrue("temp verified before the swap", log.indexOf("counts(temp)") < log.indexOf("rename timetable.db -> timetable.db.plain-bak"))
        assertTrue("final path verified before the plaintext is deleted", log.indexOf("counts(final)") < deleteBackup)
        assertTrue(log.indexOf("rename timetable.db.enc-tmp -> timetable.db") < log.indexOf("counts(final)"))
    }

    @Test fun sidecarFilesGoWithThePlaintext() {
        File(db.path + "-wal").writeText("wal")
        File(db.path + "-shm").writeText("shm")
        migrator().migrate(db, key)
        assertFalse(File(db.path + "-wal").exists())
        assertFalse(File(db.path + "-shm").exists())
        assertNoLeftovers()
    }

    // --- failures keep the plaintext exactly as it was ------------------------

    private fun assertUntouchedAfter(result: MigrationResult, step: MigrationStep, before: ByteArray) {
        assertTrue("expected failure at $step but got $result", result is MigrationResult.Failed && result.step == step)
        assertArrayEquals("plaintext is byte-for-byte the same", before, plainBytes())
        assertEquals(DatabaseMode.PLAINTEXT, DatabaseModes.detect(db))
        assertNoLeftovers()
    }

    @Test fun exportFailureLeavesThePlaintextUntouchedAndRemovesTheHalfWrittenCopy() {
        val before = plainBytes()
        assertUntouchedAfter(migrator(FakeConverter().apply { failExport = true }).migrate(db, key), MigrationStep.EXPORT, before)
    }

    @Test fun anUnreadableCopyFailsVerification() {
        val before = plainBytes()
        assertUntouchedAfter(migrator(FakeConverter().apply { failReadTemp = true }).migrate(db, key), MigrationStep.VERIFY, before)
    }

    @Test fun aRowCountMismatchFailsVerificationBeforeAnythingIsMoved() {
        val before = plainBytes()
        val result = migrator(FakeConverter().apply { tempCountsOffByOne = true }).migrate(db, key)
        assertUntouchedAfter(result, MigrationStep.VERIFY, before)
        assertFalse("nothing was renamed", log.any { it.startsWith("rename") })
    }

    @Test fun ifThePlaintextCannotBeMovedAsideNothingChanges() {
        val before = plainBytes()
        val result = migrator(files = FlakyFiles(failRenameFrom = setOf("timetable.db"))).migrate(db, key)
        assertUntouchedAfter(result, MigrationStep.SWAP, before)
    }

    @Test fun ifTheEncryptedFileCannotBePutInPlaceThePlaintextIsRestored() {
        val before = plainBytes()
        File(db.path + "-wal").writeText("wal-bytes")
        val result = migrator(files = FlakyFiles(failRenameFrom = setOf("timetable.db.enc-tmp"))).migrate(db, key)
        assertUntouchedAfter(result, MigrationStep.SWAP, before)
        assertEquals("the sidecar came back too", "wal-bytes", File(db.path + "-wal").readText())
    }

    @Test fun ifTheSwappedDatabaseCannotBeOpenedThePlaintextIsRestored() {
        val before = plainBytes()
        val result = migrator(FakeConverter().apply { failReadFinal = true }).migrate(db, key)
        assertUntouchedAfter(result, MigrationStep.POST_VERIFY, before)
    }

    @Test fun ifTheSwappedDatabaseHasDifferentCountsThePlaintextIsRestored() {
        val before = plainBytes()
        val result = migrator(FakeConverter().apply { finalCountsOffByOne = true }).migrate(db, key)
        assertUntouchedAfter(result, MigrationStep.POST_VERIFY, before)
    }

    @Test fun onlyAPlaintextDatabaseCanBeConverted() {
        db.writeBytes(ByteArray(64) { 9 })
        val r = migrator().migrate(db, key) as MigrationResult.Failed
        assertEquals(MigrationStep.PRECHECK, r.step)
        db.delete()
        assertEquals(MigrationStep.PRECHECK, (migrator().migrate(db, key) as MigrationResult.Failed).step)
    }

    @Test fun aLeftoverFromAPreviousAttemptBlocksANewOne() {
        File(db.path + ".plain-bak").writeText("old")
        assertEquals(MigrationStep.PRECHECK, (migrator().migrate(db, key) as MigrationResult.Failed).step)
        assertTrue("and it is not deleted", File(db.path + ".plain-bak").exists())
    }

    // --- cleanup that fails, and recovery --------------------------------------

    @Test fun ifThePlaintextCopyCannotBeDeletedItIsReportedAndRecoveryFinishesTheJob() {
        val converter = FakeConverter()
        val files = FlakyFiles(failDelete = setOf("timetable.db.plain-bak"))
        val result = migrator(converter, files).migrate(db, key)
        assertEquals(MigrationResult.Success(plaintextLeftBehind = true), result)
        assertTrue(File(db.path + ".plain-bak").exists())

        // Next start: the plaintext is removed once the encrypted file is proven good.
        val recovery = EncryptionMigrator(converter, tables).recover(db, key)
        assertEquals(Recovery.COMPLETED_CLEANUP, recovery)
        assertNoLeftovers()
        assertEquals(DatabaseMode.ENCRYPTED, DatabaseModes.detect(db))
    }

    @Test fun recoveryRemovesAnIncompleteTemporaryCopy() {
        File(db.path + ".enc-tmp").writeText("partial")
        assertEquals(Recovery.REMOVED_INCOMPLETE_COPY, migrator().recover(db, key))
        assertNoLeftovers()
        assertEquals(DatabaseMode.PLAINTEXT, DatabaseModes.detect(db))
    }

    @Test fun recoveryRestoresPlaintextIfTheProcessDiedBetweenTheTwoRenames() {
        val before = plainBytes()
        db.renameTo(File(db.path + ".plain-bak")) // plaintext moved aside, encrypted not yet in place
        assertEquals(Recovery.RESTORED_PLAINTEXT, migrator().recover(db, key))
        assertArrayEquals(before, plainBytes())
        assertNoLeftovers()
    }

    @Test fun recoveryRestoresPlaintextIfTheEncryptedFileCannotBeProvenGood() {
        val before = plainBytes()
        val converter = FakeConverter()
        migrator(converter, FlakyFiles(failDelete = setOf("timetable.db.plain-bak"))).migrate(db, key)
        // The key is gone or wrong: do not trust the encrypted file, bring the plaintext back.
        val recovery = EncryptionMigrator(converter, tables).recover(db, key = null)
        assertEquals(Recovery.RESTORED_PLAINTEXT, recovery)
        assertArrayEquals(before, plainBytes())
        assertNoLeftovers()
    }

    @Test fun recoveryWithBothPlaintextFilesLeavesThemForAPerson() {
        File(db.path + ".plain-bak").writeBytes((header + "\n" + counts()).toByteArray(Charsets.ISO_8859_1))
        assertEquals(Recovery.UNRESOLVED, migrator().recover(db, key))
        assertTrue(File(db.path + ".plain-bak").exists())
        assertEquals(DatabaseMode.PLAINTEXT, DatabaseModes.detect(db))
    }

    @Test fun recoveryWithNothingPendingDoesNothing() {
        assertEquals(Recovery.NOTHING_TO_DO, migrator().recover(db, key))
        assertEquals(Recovery.NOTHING_TO_DO, migrator().recover(File(tmp.root, "other.db"), key))
    }
}
