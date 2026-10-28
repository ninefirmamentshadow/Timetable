package com.sovereignops.timetable.dbcrypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts a small secret (the database key) so it can be stored in an ordinary file. */
interface KeyWrapper {
    fun wrap(plain: ByteArray): ByteArray

    /** Throws [GeneralSecurityException] when the blob cannot be unwrapped (key gone, data damaged). */
    fun unwrap(blob: ByteArray): ByteArray
}

/**
 * Wraps with an AES-256-GCM key that lives in the Android Keystore and never
 * leaves it. The key is deliberately NOT tied to user authentication: the
 * reminder and boot receivers must be able to open the database while the phone
 * is locked. The app lock is a screen gate, not part of the key.
 *
 * Blob layout: 1 byte version, 12 byte IV, ciphertext with the 16 byte tag.
 */
class AndroidKeystoreWrapper(private val alias: String) : KeyWrapper {

    override fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(AAD)
        val ct = cipher.doFinal(plain)
        return byteArrayOf(VERSION) + cipher.iv + ct
    }

    override fun unwrap(blob: ByteArray): ByteArray {
        if (blob.size < 1 + IV_BYTES + TAG_BYTES || blob[0] != VERSION) {
            throw GeneralSecurityException("unrecognised key blob")
        }
        val iv = blob.copyOfRange(1, 1 + IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, existingKey(), GCMParameterSpec(TAG_BYTES * 8, iv))
        cipher.updateAAD(AAD)
        return cipher.doFinal(blob, 1 + IV_BYTES, blob.size - 1 - IV_BYTES)
    }

    /** Removes the Keystore key (used only when resetting an unrecoverable database). */
    fun deleteKey() {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    private fun existingKey(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        return store.getKey(alias, null) as? SecretKey ?: throw GeneralSecurityException("keystore key is missing")
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
        val AAD = "database-key-v1".toByteArray(Charsets.UTF_8)
    }
}
