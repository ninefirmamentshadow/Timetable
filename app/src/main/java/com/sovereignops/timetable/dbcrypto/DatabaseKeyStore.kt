package com.sovereignops.timetable.dbcrypto

import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom

/** The stored database key cannot be read (wrapping key gone, file damaged). The database cannot be opened. */
class KeyUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Holds the database key: 32 random bytes, wrapped by a [KeyWrapper] and kept in
 * [file] (put it in noBackupFilesDir so it is never backed up or transferred).
 * Created on first use; afterwards always read back, never regenerated, so an
 * unreadable key is reported instead of silently replaced.
 */
class DatabaseKeyStore(
    private val file: File,
    private val wrapper: KeyWrapper,
    private val random: SecureRandom = SecureRandom(),
) {
    @Synchronized
    fun exists(): Boolean = file.exists()

    /** The raw 32-byte key, creating and storing it if there is none yet. */
    @Synchronized
    fun loadOrCreate(): ByteArray {
        if (file.exists()) return read()
        val key = ByteArray(KEY_BYTES).also(random::nextBytes)
        val blob = try {
            wrapper.wrap(key)
        } catch (e: GeneralSecurityException) {
            throw KeyUnavailableException("The database key could not be protected", e)
        }
        writeAtomically(blob)
        return key
    }

    /** Remove the stored key (only when resetting an unrecoverable database). */
    @Synchronized
    fun delete() {
        file.delete()
    }

    private fun read(): ByteArray {
        val blob = try {
            file.readBytes()
        } catch (e: IOException) {
            throw KeyUnavailableException("The database key file cannot be read", e)
        }
        val key = try {
            wrapper.unwrap(blob)
        } catch (e: GeneralSecurityException) {
            throw KeyUnavailableException("The database key can no longer be unwrapped", e)
        } catch (e: RuntimeException) {
            throw KeyUnavailableException("The database key can no longer be unwrapped", e)
        }
        if (key.size != KEY_BYTES) throw KeyUnavailableException("The database key has the wrong length")
        return key
    }

    private fun writeAtomically(blob: ByteArray) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            tmp.outputStream().use { out ->
                out.write(blob)
                out.fd.sync()
            }
            if (!tmp.renameTo(file)) throw IOException("could not move the key file into place")
        } catch (e: IOException) {
            tmp.delete()
            throw KeyUnavailableException("The database key could not be stored", e)
        }
    }

    companion object {
        const val KEY_BYTES = 32

        /**
         * The SQLCipher passphrase for a raw 256-bit key: the text x'HEX' (64 hex digits),
         * which SQLCipher takes as the key itself, with no key-derivation step.
         */
        fun rawKeyPassphrase(key: ByteArray): ByteArray {
            require(key.size == KEY_BYTES) { "key must be 32 bytes" }
            val hex = key.joinToString("") { "%02x".format(it) }
            return "x'$hex'".toByteArray(Charsets.US_ASCII)
        }
    }
}
