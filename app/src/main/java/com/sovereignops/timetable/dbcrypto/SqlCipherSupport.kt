package com.sovereignops.timetable.dbcrypto

import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

/**
 * The SQLCipher side: loads the native library, builds the Room open-helper
 * factory, and implements [DatabaseConverter] with sqlcipher_export. This needs
 * the native library, so it cannot run on the JVM: it compiles in CI but is only
 * exercised on a device.
 */
object SqlCipherSupport {

    @Volatile
    private var loaded = false

    @Synchronized
    fun loadLibrary() {
        if (!loaded) {
            System.loadLibrary("sqlcipher")
            loaded = true
        }
    }

    /** For Room's openHelperFactory: opens the database with the raw 256-bit [key]. */
    fun openHelperFactory(key: ByteArray): SupportOpenHelperFactory {
        loadLibrary()
        return SupportOpenHelperFactory(DatabaseKeyStore.rawKeyPassphrase(key))
    }

    object Converter : DatabaseConverter {

        override fun exportEncrypted(plain: File, target: File, key: ByteArray) {
            loadLibrary()
            target.delete()
            val db = SQLiteDatabase.openDatabase(plain.path, ByteArray(0), null, SQLiteDatabase.OPEN_READWRITE, null, null)
            try {
                // Fold the write-ahead log into the main file so the export sees everything.
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", arrayOf<String>()).use { it.moveToFirst() }
                val version = db.rawQuery("PRAGMA user_version", arrayOf<String>()).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
                val hex = String(DatabaseKeyStore.rawKeyPassphrase(key), Charsets.US_ASCII)
                db.execSQL("ATTACH DATABASE '${target.path.replace("'", "''")}' AS encrypted KEY \"$hex\"")
                db.rawQuery("SELECT sqlcipher_export('encrypted')", arrayOf<String>()).use { it.moveToFirst() }
                // sqlcipher_export does not copy the schema version Room checks on open.
                db.execSQL("PRAGMA encrypted.user_version = $version")
                db.execSQL("DETACH DATABASE encrypted")
            } finally {
                db.close()
            }
        }

        override fun rowCounts(file: File, key: ByteArray?, tables: List<String>): Map<String, Long> {
            loadLibrary()
            val password = if (key == null) ByteArray(0) else DatabaseKeyStore.rawKeyPassphrase(key)
            val db = SQLiteDatabase.openDatabase(file.path, password, null, SQLiteDatabase.OPEN_READONLY, null, null)
            try {
                return tables.associateWith { table ->
                    db.rawQuery("SELECT COUNT(*) FROM \"$table\"", arrayOf<String>()).use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }
                }
            } finally {
                db.close()
            }
        }
    }
}
