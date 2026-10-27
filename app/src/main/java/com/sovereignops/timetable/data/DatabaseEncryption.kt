package com.sovereignops.timetable.data

import android.content.Context
import com.sovereignops.timetable.dbcrypto.AndroidKeystoreWrapper
import com.sovereignops.timetable.dbcrypto.DatabaseKeyStore
import com.sovereignops.timetable.dbcrypto.DatabaseMode
import com.sovereignops.timetable.dbcrypto.DatabaseModes
import com.sovereignops.timetable.dbcrypto.EncryptionMigrator
import com.sovereignops.timetable.dbcrypto.KeyUnavailableException
import com.sovereignops.timetable.dbcrypto.MigrationResult
import com.sovereignops.timetable.dbcrypto.SqlCipherSupport
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.io.File

/** The database key cannot be recovered; the encrypted database cannot be opened. */
class DatabaseKeyUnavailable(cause: Throwable) : Exception("The database key is unavailable", cause)

/**
 * Timetable's use of the dbcrypto package. New installs are encrypted from the
 * start. An existing plaintext database stays plaintext until the operator asks,
 * and then is converted at the NEXT app start, before the database is opened
 * (nothing else is using it then, so there is no live connection to quiesce):
 * [requestEncryption] records the request, [prepare] performs it.
 *
 * The key is 32 random bytes wrapped by an Android Keystore key, stored in
 * noBackupFilesDir, not tied to user authentication (the reminder and boot
 * receivers open the database while the phone is locked).
 */
object DatabaseEncryption {

    private const val KEY_FILE = "db.key"
    private const val REQUEST_FILE = "db.encrypt-request"
    private const val RESULT_FILE = "db.encrypt-result"
    private const val KEYSTORE_ALIAS = "timetable_database_key"
    // Tables whose row counts must match before the encrypted copy replaces the original.
    // sqlcipher_export copies every table; booking_drafts (schema v2) is left out of the
    // check because a database still at v1 when it is converted does not have it yet.
    private val TABLES = listOf("bookings", "availability", "room_master_table")

    /** For tests: replaces the native conversion and key handling. Null in production. */
    @Volatile
    var testHooks: Hooks? = null

    interface Hooks {
        val converter: com.sovereignops.timetable.dbcrypto.DatabaseConverter
        val keyStore: DatabaseKeyStore
        fun factoryFor(key: ByteArray): SupportSQLiteOpenHelper.Factory
    }

    data class Status(val mode: DatabaseMode, val requestPending: Boolean, val lastResult: String?)

    fun status(context: Context): Status = Status(
        mode = DatabaseModes.detect(dbFile(context)),
        requestPending = requestFile(context).exists(),
        lastResult = resultFile(context).takeIf { it.exists() }?.readText(),
    )

    /** Ask for the plaintext database to be encrypted at the next start. */
    fun requestEncryption(context: Context) {
        resultFile(context).delete()
        requestFile(context).writeText("encrypt")
    }

    fun cancelRequest(context: Context) {
        requestFile(context).delete()
    }

    fun clearResult(context: Context) {
        resultFile(context).delete()
    }

    /**
     * Called from AppDatabase.get() before Room opens the file. Finishes or undoes an
     * interrupted conversion, performs a requested one, and returns the open-helper
     * factory to use: null for a plaintext database, SQLCipher's otherwise.
     * Throws [DatabaseKeyUnavailable] when an encrypted database's key is gone.
     */
    @Synchronized
    fun prepare(context: Context): SupportSQLiteOpenHelper.Factory? {
        val db = dbFile(context)
        val keys = keyStore(context)
        val migrator = EncryptionMigrator(converter(), TABLES)

        migrator.recover(db, if (keys.exists()) keysOrNull(keys) else null)

        if (requestFile(context).exists()) {
            if (DatabaseModes.detect(db) == DatabaseMode.PLAINTEXT) {
                val message = try {
                    when (val result = migrator.migrate(db, keys.loadOrCreate())) {
                        is MigrationResult.Success ->
                            if (result.plaintextLeftBehind) "encrypted; the old plaintext copy could not be deleted yet" else "encrypted"
                        is MigrationResult.Failed -> "failed at ${result.step}: ${result.message}; your data was not changed"
                    }
                } catch (e: KeyUnavailableException) {
                    "failed: the encryption key could not be created; your data was not changed"
                } catch (e: Exception) {
                    "failed (${e.javaClass.simpleName}); your data was not changed"
                }
                resultFile(context).writeText(message)
            }
            requestFile(context).delete()
        }

        return when (DatabaseModes.detect(db)) {
            DatabaseMode.PLAINTEXT -> null
            DatabaseMode.ABSENT, DatabaseMode.ENCRYPTED -> {
                val key = try {
                    keys.loadOrCreate()
                } catch (e: KeyUnavailableException) {
                    throw DatabaseKeyUnavailable(e)
                }
                factoryFor(key)
            }
        }
    }

    /** Delete the database and its key so the app can start fresh (restore from a backup afterwards). */
    @Synchronized
    fun resetEverything(context: Context) {
        val db = dbFile(context)
        for (suffix in listOf("", "-wal", "-shm", "-journal", ".enc-tmp", ".plain-bak")) File(db.path + suffix).delete()
        keyStore(context).delete()
        requestFile(context).delete()
        resultFile(context).delete()
        runCatching { AndroidKeystoreWrapper(KEYSTORE_ALIAS).deleteKey() }
    }

    private fun keysOrNull(keys: DatabaseKeyStore): ByteArray? = try {
        keys.loadOrCreate()
    } catch (e: KeyUnavailableException) {
        null
    }

    private fun factoryFor(key: ByteArray): SupportSQLiteOpenHelper.Factory =
        testHooks?.factoryFor(key) ?: SqlCipherSupport.openHelperFactory(key)

    private fun converter() = testHooks?.converter ?: SqlCipherSupport.Converter

    private fun keyStore(context: Context): DatabaseKeyStore =
        testHooks?.keyStore ?: DatabaseKeyStore(File(context.noBackupFilesDir, KEY_FILE), AndroidKeystoreWrapper(KEYSTORE_ALIAS))

    private fun dbFile(context: Context) = context.getDatabasePath(AppDatabase.NAME)
    private fun requestFile(context: Context) = File(context.noBackupFilesDir, REQUEST_FILE)
    private fun resultFile(context: Context) = File(context.noBackupFilesDir, RESULT_FILE)
}
