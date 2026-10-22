package com.sovereignops.timetable.dbcrypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom

class DatabaseModesAndKeysTest {

    @get:Rule val tmp = TemporaryFolder()

    private val header = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)

    // --- mode detection ------------------------------------------------------

    @Test fun missingAndEmptyFilesAreAbsent() {
        assertEquals(DatabaseMode.ABSENT, DatabaseModes.detect(File(tmp.root, "nope.db")))
        assertEquals(DatabaseMode.ABSENT, DatabaseModes.detect(tmp.newFile("empty.db")))
    }

    @Test fun aSqliteHeaderMeansPlaintext() {
        val f = tmp.newFile("p.db").apply { writeBytes(header + ByteArray(100)) }
        assertEquals(DatabaseMode.PLAINTEXT, DatabaseModes.detect(f))
    }

    @Test fun anythingElseIsEncrypted() {
        val random = ByteArray(4096).also(SecureRandom()::nextBytes)
        assertEquals(DatabaseMode.ENCRYPTED, DatabaseModes.detect(tmp.newFile("e.db").apply { writeBytes(random) }))
        // A file too short to hold a header is not readable as plaintext either.
        assertEquals(DatabaseMode.ENCRYPTED, DatabaseModes.detect(tmp.newFile("short.db").apply { writeBytes(ByteArray(5) { 7 }) }))
    }

    // --- key store -----------------------------------------------------------

    /** Reversible stand-in for the Keystore wrapper: XOR with a fixed pad, so a wrong "key" fails to unwrap. */
    private class FakeWrapper(private val pad: Byte = 0x5a, var failWrap: Boolean = false, var failUnwrap: Boolean = false) : KeyWrapper {
        override fun wrap(plain: ByteArray): ByteArray {
            if (failWrap) throw GeneralSecurityException("no keystore")
            return byteArrayOf(1) + ByteArray(plain.size) { (plain[it].toInt() xor pad.toInt()).toByte() }
        }

        override fun unwrap(blob: ByteArray): ByteArray {
            if (failUnwrap || blob.isEmpty() || blob[0] != 1.toByte()) throw GeneralSecurityException("cannot unwrap")
            return ByteArray(blob.size - 1) { (blob[it + 1].toInt() xor pad.toInt()).toByte() }
        }
    }

    private fun store(wrapper: KeyWrapper = FakeWrapper(), name: String = "db.key") =
        DatabaseKeyStore(File(tmp.root, name), wrapper)

    @Test fun theKeyIsCreatedOnceAndReadBackUnchanged() {
        val s = store()
        assertFalse(s.exists())
        val first = s.loadOrCreate()
        assertEquals(32, first.size)
        assertTrue(s.exists())
        assertArrayEquals(first, store().loadOrCreate()) // a new instance reads the same key
        assertArrayEquals(first, s.loadOrCreate())
    }

    @Test fun theStoredFileDoesNotContainTheRawKey() {
        val s = store()
        val key = s.loadOrCreate()
        val onDisk = File(tmp.root, "db.key").readBytes()
        assertFalse(onDisk.toList().windowed(key.size).any { it.toByteArray().contentEquals(key) })
    }

    @Test fun twoStoresGetDifferentKeys() {
        assertFalse(store(name = "a.key").loadOrCreate().contentEquals(store(name = "b.key").loadOrCreate()))
    }

    @Test fun noTemporaryFileIsLeftBehind() {
        store().loadOrCreate()
        assertFalse(File(tmp.root, "db.key.tmp").exists())
    }

    // A lost wrapping key must be reported, never answered with a fresh (useless) key.
    @Test fun anUnreadableKeyIsReportedAndNotRegenerated() {
        val s = store()
        s.loadOrCreate()
        val before = File(tmp.root, "db.key").readBytes()
        val broken = store(FakeWrapper(failUnwrap = true))
        assertThrows(KeyUnavailableException::class.java) { broken.loadOrCreate() }
        assertArrayEquals("the stored blob is untouched", before, File(tmp.root, "db.key").readBytes())
    }

    @Test fun aDamagedKeyFileIsReported() {
        val s = store()
        s.loadOrCreate()
        File(tmp.root, "db.key").writeBytes(byteArrayOf(9, 9, 9))
        assertThrows(KeyUnavailableException::class.java) { store().loadOrCreate() }
    }

    @Test fun aKeyOfTheWrongLengthIsRejected() {
        File(tmp.root, "db.key").writeBytes(FakeWrapper().wrap(ByteArray(16)))
        assertThrows(KeyUnavailableException::class.java) { store().loadOrCreate() }
    }

    @Test fun aWrapperThatCannotProtectTheKeyStoresNothing() {
        assertThrows(KeyUnavailableException::class.java) { store(FakeWrapper(failWrap = true)).loadOrCreate() }
        assertFalse(File(tmp.root, "db.key").exists())
    }

    @Test fun deleteRemovesTheKey() {
        val s = store()
        s.loadOrCreate()
        s.delete()
        assertFalse(s.exists())
    }

    @Test fun rawKeyPassphraseIsTheHexFormSqlcipherTakesAsTheKeyItself() {
        val key = ByteArray(32) { it.toByte() }
        assertEquals(
            "x'000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f'",
            String(DatabaseKeyStore.rawKeyPassphrase(key), Charsets.US_ASCII),
        )
        assertThrows(IllegalArgumentException::class.java) { DatabaseKeyStore.rawKeyPassphrase(ByteArray(31)) }
    }
}
