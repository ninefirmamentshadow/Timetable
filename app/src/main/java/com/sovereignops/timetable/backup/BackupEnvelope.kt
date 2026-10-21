package com.sovereignops.timetable.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-encrypted backup envelope. Self-contained: it knows nothing about
 * what is inside, only an application id, a payload schema number and bytes.
 * The format is specified in docs/BACKUP_FORMAT.md.
 *
 * - Key: PBKDF2-HMAC-SHA-256 over the UTF-8 passphrase, 16-byte random salt,
 *   iteration count stored in the envelope (accepted range 100 000..2 000 000).
 * - Cipher: AES-256-GCM, 12-byte random IV, 128-bit tag appended to the ciphertext.
 * - The routing and KDF fields are bound to the tag as additional authenticated
 *   data, so an envelope cannot be relabelled for another app or schema, or have
 *   its iteration count changed, without failing authentication.
 *
 * Nothing here touches storage; callers decrypt first and only then change data.
 */
class BackupEnvelope(
    private val appId: String,
    private val random: SecureRandom = SecureRandom(),
) {
    init {
        require(APP_ID_PATTERN.matches(appId)) { "app id must match $APP_ID_PATTERN" }
    }

    /** The decrypted payload and the schema number it was written with. */
    class Opened(val schema: Int, val plaintext: ByteArray)

    /** Structure of an envelope that validated, read without deriving a key. */
    data class Header(val schema: Int, val iterations: Int)

    /** Encrypt [plaintext] under [passphrase]. [schema] is the payload's own version (>= 1). */
    fun seal(
        plaintext: ByteArray,
        schema: Int,
        passphrase: String,
        iterations: Int = DEFAULT_ITERATIONS,
    ): String {
        require(schema >= 1) { "schema must be at least 1" }
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "iterations out of range" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val saltText = Base64.getEncoder().encodeToString(salt)
        val aad = additionalData(appId, iterations, schema, saltText)
        val ciphertext = try {
            val key = deriveKey(passphrase, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(aad)
            cipher.doFinal(plaintext)
        } catch (e: GeneralSecurityException) {
            throw BackupException.Malformed("Encryption failed", e)
        }
        return buildJsonObject {
            put("format", FORMAT)
            put("app", appId)
            put("envelope", ENVELOPE_VERSION)
            put("kdf", KDF_NAME)
            put("cipher", CIPHER_NAME)
            put("iterations", iterations)
            put("schema", schema)
            put("salt", saltText)
            put("iv", Base64.getEncoder().encodeToString(iv))
            put("ct", Base64.getEncoder().encodeToString(ciphertext))
        }.toString()
    }

    /** Validate the structure and identity of [envelopeJson] without deriving a key. */
    fun inspect(envelopeJson: String): Header {
        val parsed = parse(envelopeJson)
        return Header(parsed.schema, parsed.iterations)
    }

    /**
     * Decrypt. Throws [BackupException]; on any failure nothing was produced, so
     * the caller's data is untouched.
     */
    fun open(envelopeJson: String, passphrase: String): Opened {
        val parsed = parse(envelopeJson)
        val plaintext = try {
            val key = deriveKey(passphrase, parsed.salt, parsed.iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, parsed.iv))
            cipher.updateAAD(parsed.additionalData)
            cipher.doFinal(parsed.ciphertext)
        } catch (e: GeneralSecurityException) {
            // AEADBadTagException and friends: wrong passphrase or modified file.
            throw BackupException.AuthenticationFailed()
        }
        return Opened(parsed.schema, plaintext)
    }

    private class Parsed(
        val schema: Int,
        val iterations: Int,
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray,
        val additionalData: ByteArray,
    )

    private fun parse(text: String): Parsed {
        if (text.length > MAX_ENVELOPE_CHARS) throw BackupException.Malformed("Backup file is too large")
        val root: JsonElement = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw BackupException.NotABackup(cause = e)
        }
        if (root !is JsonObject) throw BackupException.NotABackup()
        if (root["format"] == null) throw BackupException.NotABackup()
        val unexpected = root.keys - KEYS
        if (unexpected.isNotEmpty()) throw BackupException.Malformed("Backup has an unexpected field")
        if (stringField(root, "format") != FORMAT) throw BackupException.NotABackup("Unrecognised backup format")
        if (intField(root, "envelope") != ENVELOPE_VERSION) {
            throw BackupException.Unsupported("Unsupported backup version")
        }
        if (stringField(root, "kdf") != KDF_NAME) throw BackupException.Unsupported("Unsupported key derivation")
        if (stringField(root, "cipher") != CIPHER_NAME) throw BackupException.Unsupported("Unsupported cipher")
        if (stringField(root, "app") != appId) throw BackupException.WrongApplication()
        val schema = intField(root, "schema")
        if (schema < 1) throw BackupException.Malformed("Backup schema version is invalid")
        val iterations = intField(root, "iterations")
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw BackupException.Malformed("Backup key-derivation parameters are out of range")
        }
        val saltText = stringField(root, "salt")
        val salt = canonicalBase64(saltText, "salt")
        if (salt.size != SALT_BYTES) throw BackupException.Malformed("salt has the wrong length")
        val iv = canonicalBase64(stringField(root, "iv"), "iv")
        if (iv.size != IV_BYTES) throw BackupException.Malformed("iv has the wrong length")
        val ciphertext = canonicalBase64(stringField(root, "ct"), "ciphertext")
        if (ciphertext.size <= TAG_BITS / 8) throw BackupException.Malformed("ciphertext is too short")
        return Parsed(schema, iterations, salt, iv, ciphertext, additionalData(appId, iterations, schema, saltText))
    }

    private fun stringField(obj: JsonObject, name: String): String {
        val value = obj[name]
        if (value !is JsonPrimitive || value is JsonNull || !value.isString) {
            throw BackupException.Malformed("Backup field '$name' must be a string")
        }
        return value.contentOrNull ?: throw BackupException.Malformed("Backup field '$name' must be a string")
    }

    private fun intField(obj: JsonObject, name: String): Int {
        val value = obj[name]
        if (value !is JsonPrimitive || value is JsonNull || value.isString) {
            throw BackupException.Malformed("Backup field '$name' must be an integer")
        }
        return value.intOrNull ?: throw BackupException.Malformed("Backup field '$name' must be an integer")
    }

    /** Strict, canonical base64: decoding then re-encoding must reproduce the text exactly. */
    private fun canonicalBase64(value: String, label: String): ByteArray {
        val bytes = try {
            Base64.getDecoder().decode(value)
        } catch (e: IllegalArgumentException) {
            throw BackupException.Malformed("$label is not valid base64", e)
        }
        if (Base64.getEncoder().encodeToString(bytes) != value) {
            throw BackupException.Malformed("$label is not canonical base64")
        }
        return bytes
    }

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        if (passphrase.length < MIN_PASSPHRASE_LENGTH) {
            throw BackupException.WeakPassphrase("Passphrase must be at least $MIN_PASSPHRASE_LENGTH characters")
        }
        if (hasLoneSurrogate(passphrase)) {
            throw BackupException.WeakPassphrase("Passphrase contains characters that cannot be encoded")
        }
        val key = Pbkdf2.hmacSha256(passphrase.toByteArray(Charsets.UTF_8), salt, iterations, KEY_BYTES)
        return SecretKeySpec(key, "AES")
    }

    private fun hasLoneSurrogate(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= text.length || !Character.isLowSurrogate(text[i + 1])) return true
                i += 2
            } else {
                if (Character.isLowSurrogate(c)) return true
                i++
            }
        }
        return false
    }

    companion object {
        const val FORMAT = "ops-vault-encrypted"
        const val ENVELOPE_VERSION = 2
        const val KDF_NAME = "PBKDF2-SHA256"
        const val CIPHER_NAME = "AES-256-GCM"
        const val DEFAULT_ITERATIONS = 220_000
        const val MIN_ITERATIONS = 100_000
        const val MAX_ITERATIONS = 2_000_000
        const val MIN_PASSPHRASE_LENGTH = 8

        /** Upper bound on the envelope text a reader will parse. */
        const val MAX_ENVELOPE_CHARS = 48 * 1024 * 1024

        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val KEY_BYTES = 32
        private const val TAG_BITS = 128
        private val APP_ID_PATTERN = Regex("[A-Za-z0-9._-]{1,64}")
        private val KEYS = setOf(
            "format", "app", "envelope", "kdf", "cipher", "iterations", "schema", "salt", "iv", "ct",
        )

        /**
         * The exact bytes bound to the GCM tag: a compact JSON object with these
         * fields in this order. Built by hand (not by a serializer) so it is
         * byte-for-byte what every implementation of the format produces.
         */
        internal fun additionalData(app: String, iterations: Int, schema: Int, saltText: String): ByteArray =
            ("{\"format\":\"$FORMAT\",\"app\":\"$app\",\"envelope\":$ENVELOPE_VERSION," +
                "\"kdf\":\"$KDF_NAME\",\"cipher\":\"$CIPHER_NAME\",\"iterations\":$iterations," +
                "\"schema\":$schema,\"salt\":\"$saltText\"}").toByteArray(Charsets.UTF_8)
    }
}
