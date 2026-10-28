package com.sovereignops.timetable.data

import android.app.Application
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sovereignops.timetable.dbcrypto.DatabaseConverter
import com.sovereignops.timetable.dbcrypto.DatabaseKeyStore
import com.sovereignops.timetable.dbcrypto.DatabaseMode
import com.sovereignops.timetable.dbcrypto.DatabaseModes
import com.sovereignops.timetable.dbcrypto.KeyWrapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.security.GeneralSecurityException

/**
 * How the app decides between plaintext and encrypted on open, with the native
 * conversion and the Keystore replaced by fakes. NOT covered here (JVM cannot load
 * SQLCipher or the Android Keystore): the real sqlcipher_export and key wrapping.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DatabaseEncryptionTest {

    private lateinit var app: Application
    private lateinit var dbFile: File

    private companion object {
        const val HEADER = "SQLite format 3\u0000\n"
    }

    private class FakeWrapper(var broken: Boolean = false) : KeyWrapper {
        override fun wrap(plain: ByteArray) = byteArrayOf(1) + plain.reversedArray()
        override fun unwrap(blob: ByteArray): ByteArray {
            if (broken) throw GeneralSecurityException("keystore key gone")
            return blob.copyOfRange(1, blob.size).reversedArray()
        }
    }

    private class FakeConverter : DatabaseConverter {
        var failExport = false
        override fun exportEncrypted(plain: File, target: File, key: ByteArray) {
            if (failExport) throw IllegalStateException("disk full")
            target.writeBytes(ByteArray(32) { 0x7f } + "KEY:${key.toList()}\n".toByteArray() + plain.readBytes().drop(HEADER.length).toByteArray())
        }

        override fun rowCounts(file: File, key: ByteArray?, tables: List<String>) = tables.associateWith { 1L }
    }

    private lateinit var wrapper: FakeWrapper
    private lateinit var converter: FakeConverter
    private lateinit var keyFile: File
    private var factoryRequests = 0

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        dbFile = app.getDatabasePath(AppDatabase.NAME).also { it.parentFile?.mkdirs() }
        keyFile = File(app.noBackupFilesDir, "db.key")
        wrapper = FakeWrapper()
        converter = FakeConverter()
        factoryRequests = 0
        DatabaseEncryption.testHooks = object : DatabaseEncryption.Hooks {
            override val converter: DatabaseConverter get() = this@DatabaseEncryptionTest.converter
            override val keyStore: DatabaseKeyStore get() = DatabaseKeyStore(keyFile, wrapper)
            override fun factoryFor(key: ByteArray): SupportSQLiteOpenHelper.Factory {
                factoryRequests++
                return FrameworkSQLiteOpenHelperFactory()
            }
        }
        DatabaseEncryption.resetEverything(app)
    }

    @After
    fun tearDown() {
        DatabaseEncryption.resetEverything(app)
        DatabaseEncryption.testHooks = null
    }

    private fun writePlaintext() = dbFile.writeBytes((HEADER + "data").toByteArray(Charsets.ISO_8859_1))

    @Test fun aNewInstallIsEncryptedFromTheStart() {
        assertEquals(DatabaseMode.ABSENT, DatabaseModes.detect(dbFile))
        val factory = DatabaseEncryption.prepare(app)
        assertNotNull("new install: encrypted", factory)
        assertTrue("a key was created and stored", keyFile.exists())
        assertEquals(1, factoryRequests)
    }

    @Test fun anExistingPlaintextDatabaseStaysPlaintextUntilAskedAndCreatesNoKey() {
        writePlaintext()
        assertNull(DatabaseEncryption.prepare(app))
        assertFalse("no key is created for a plaintext database", keyFile.exists())
        assertEquals(DatabaseMode.PLAINTEXT, DatabaseModes.detect(dbFile))
        assertEquals(0, factoryRequests)
    }

    @Test fun aRequestedConversionRunsAtTheNextStartAndTheResultIsRemembered() {
        writePlaintext()
        DatabaseEncryption.requestEncryption(app)
        assertTrue(DatabaseEncryption.status(app).requestPending)

        val factory = DatabaseEncryption.prepare(app)

        assertNotNull(factory)
        assertEquals(DatabaseMode.ENCRYPTED, DatabaseModes.detect(dbFile))
        val status = DatabaseEncryption.status(app)
        assertFalse("the request is consumed", status.requestPending)
        assertEquals("encrypted", status.lastResult)
        assertFalse(File(dbFile.path + ".plain-bak").exists())
    }

    @Test fun aFailedConversionKeepsThePlaintextAndSaysWhy() {
        writePlaintext()
        val before = dbFile.readBytes()
        converter.failExport = true
        DatabaseEncryption.requestEncryption(app)

        val factory = DatabaseEncryption.prepare(app)

        assertNull("still plaintext", factory)
        assertTrue(before.contentEquals(dbFile.readBytes()))
        val status = DatabaseEncryption.status(app)
        assertFalse(status.requestPending)
        assertTrue(status.lastResult!!.startsWith("failed at EXPORT"))
        assertTrue(status.lastResult!!.endsWith("your data was not changed"))
    }

    @Test fun aRequestWithNoPlaintextDatabaseIsJustCleared() {
        DatabaseEncryption.requestEncryption(app)
        DatabaseEncryption.prepare(app)
        assertFalse(DatabaseEncryption.status(app).requestPending)
    }

    @Test fun anEncryptedDatabaseWithALostKeyIsReportedNotRegenerated() {
        writePlaintext()
        DatabaseEncryption.requestEncryption(app)
        DatabaseEncryption.prepare(app) // now encrypted, key stored
        val keyBlob = keyFile.readBytes()

        wrapper.broken = true // the Keystore key is gone
        assertThrows(DatabaseKeyUnavailable::class.java) { DatabaseEncryption.prepare(app) }
        assertTrue("the key file is not replaced", keyBlob.contentEquals(keyFile.readBytes()))
    }

    @Test fun resettingRemovesTheDatabaseAndItsKeySoTheAppCanStartFresh() {
        writePlaintext()
        DatabaseEncryption.requestEncryption(app)
        DatabaseEncryption.prepare(app)
        wrapper.broken = true

        DatabaseEncryption.resetEverything(app)

        assertEquals(DatabaseMode.ABSENT, DatabaseModes.detect(dbFile))
        assertFalse(keyFile.exists())
        wrapper.broken = false
        assertNotNull("a fresh encrypted database can be created", DatabaseEncryption.prepare(app))
    }

    @Test fun statusReportsTheMode() {
        assertEquals(DatabaseMode.ABSENT, DatabaseEncryption.status(app).mode)
        writePlaintext()
        assertEquals(DatabaseMode.PLAINTEXT, DatabaseEncryption.status(app).mode)
        DatabaseEncryption.requestEncryption(app)
        DatabaseEncryption.prepare(app)
        assertEquals(DatabaseMode.ENCRYPTED, DatabaseEncryption.status(app).mode)
    }
}
