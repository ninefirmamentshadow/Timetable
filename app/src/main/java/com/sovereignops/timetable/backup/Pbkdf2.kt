package com.sovereignops.timetable.backup

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * PBKDF2 with HMAC-SHA-256 (RFC 8018), written out over [Mac] so the password
 * bytes are exactly the UTF-8 encoding the caller passes in, on every Android
 * version and on the JVM. Verified against the published test vectors.
 */
object Pbkdf2 {

    fun hmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, keyLength: Int = 32): ByteArray {
        require(password.isNotEmpty()) { "password must not be empty" }
        require(iterations >= 1) { "iterations must be positive" }
        require(keyLength in 1..Int.MAX_VALUE / 2) { "invalid key length" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val hashLength = mac.macLength
        val blocks = (keyLength + hashLength - 1) / hashLength
        val out = ByteArray(keyLength)
        for (block in 1..blocks) {
            mac.update(salt)
            mac.update(ByteBuffer.allocate(4).putInt(block).array())
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 2..iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val offset = (block - 1) * hashLength
            System.arraycopy(t, 0, out, offset, minOf(hashLength, keyLength - offset))
        }
        return out
    }
}
