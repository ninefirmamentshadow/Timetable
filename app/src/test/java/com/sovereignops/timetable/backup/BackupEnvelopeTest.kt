package com.sovereignops.timetable.backup

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class BackupEnvelopeTest {

    private val envelope = BackupEnvelope("test-app")
    private val passphrase = "correct horse battery"
    private val payload = """{"hello":"wörld ✓","n":[1,2,3]}""".toByteArray(Charsets.UTF_8)

    // Low-ish (minimum allowed) iteration count keeps the suite quick.
    private fun seal(schema: Int = 1, iterations: Int = BackupEnvelope.MIN_ITERATIONS) =
        envelope.seal(payload, schema, passphrase, iterations)

    private fun tamper(text: String, change: (MutableMap<String, kotlinx.serialization.json.JsonElement>) -> Unit): String {
        val map = Json.parseToJsonElement(text).jsonObject.toMutableMap()
        change(map)
        return JsonObject(map).toString()
    }

    @Test fun roundTripRestoresPayloadAndSchema() {
        val opened = envelope.open(seal(schema = 7), passphrase)
        assertEquals(7, opened.schema)
        assertArrayEquals(payload, opened.plaintext)
    }

    @Test fun envelopeCarriesTheDocumentedFields() {
        val obj = Json.parseToJsonElement(seal(schema = 2)).jsonObject
        assertEquals(
            listOf("format", "app", "envelope", "kdf", "cipher", "iterations", "schema", "salt", "iv", "ct"),
            obj.keys.toList(),
        )
        assertEquals("ops-vault-encrypted", (obj["format"] as JsonPrimitive).content)
        assertEquals("test-app", (obj["app"] as JsonPrimitive).content)
        assertEquals("2", (obj["envelope"] as JsonPrimitive).content)
        assertEquals("PBKDF2-SHA256", (obj["kdf"] as JsonPrimitive).content)
        assertEquals("AES-256-GCM", (obj["cipher"] as JsonPrimitive).content)
        assertEquals(16, Base64.getDecoder().decode((obj["salt"] as JsonPrimitive).content).size)
        assertEquals(12, Base64.getDecoder().decode((obj["iv"] as JsonPrimitive).content).size)
    }

    @Test fun everySealUsesFreshSaltAndIv() {
        val a = Json.parseToJsonElement(seal()).jsonObject
        val b = Json.parseToJsonElement(seal()).jsonObject
        assertNotEquals(a["salt"], b["salt"])
        assertNotEquals(a["iv"], b["iv"])
        assertNotEquals(a["ct"], b["ct"])
    }

    @Test fun inspectReadsStructureWithoutAKey() {
        assertEquals(BackupEnvelope.Header(4, 100_000), envelope.inspect(seal(schema = 4)))
    }

    // Wrong passphrase: a clean error and no plaintext.
    @Test fun wrongPassphraseIsAnAuthenticationFailure() {
        assertThrows(BackupException.AuthenticationFailed::class.java) {
            envelope.open(seal(), "not the passphrase")
        }
    }

    @Test fun tamperedCiphertextIsRejected() {
        val text = seal()
        val tampered = tamper(text) { m ->
            val ct = Base64.getDecoder().decode((m["ct"] as JsonPrimitive).content)
            ct[0] = (ct[0].toInt() xor 1).toByte()
            m["ct"] = JsonPrimitive(Base64.getEncoder().encodeToString(ct))
        }
        assertThrows(BackupException.AuthenticationFailed::class.java) { envelope.open(tampered, passphrase) }
    }

    @Test fun truncatedCiphertextIsRejected() {
        val tampered = tamper(seal()) { m ->
            val ct = Base64.getDecoder().decode((m["ct"] as JsonPrimitive).content)
            m["ct"] = JsonPrimitive(Base64.getEncoder().encodeToString(ct.copyOf(ct.size - 3)))
        }
        assertThrows(BackupException.AuthenticationFailed::class.java) { envelope.open(tampered, passphrase) }
    }

    @Test fun tamperedTagIsRejected() {
        val tampered = tamper(seal()) { m ->
            val ct = Base64.getDecoder().decode((m["ct"] as JsonPrimitive).content)
            ct[ct.size - 1] = (ct[ct.size - 1].toInt() xor 0x80).toByte()
            m["ct"] = JsonPrimitive(Base64.getEncoder().encodeToString(ct))
        }
        assertThrows(BackupException.AuthenticationFailed::class.java) { envelope.open(tampered, passphrase) }
    }

    // The routing and KDF fields are bound to the tag.
    @Test fun relabelledSchemaFailsAuthentication() {
        val tampered = tamper(seal(schema = 1)) { it["schema"] = JsonPrimitive(2) }
        assertThrows(BackupException.AuthenticationFailed::class.java) { envelope.open(tampered, passphrase) }
    }

    @Test fun changedIterationCountFailsAuthentication() {
        val tampered = tamper(seal()) { it["iterations"] = JsonPrimitive(100_001) }
        assertThrows(BackupException.AuthenticationFailed::class.java) { envelope.open(tampered, passphrase) }
    }

    @Test fun swappedSaltFailsAuthentication() {
        val other = Json.parseToJsonElement(seal()).jsonObject["salt"]!!
        val tampered = tamper(seal()) { it["salt"] = other }
        assertThrows(BackupException.AuthenticationFailed::class.java) { envelope.open(tampered, passphrase) }
    }

    @Test fun anotherApplicationsBackupIsRefusedBeforeAnyKeyWork() {
        val foreign = BackupEnvelope("other-app").seal(payload, 1, passphrase, BackupEnvelope.MIN_ITERATIONS)
        assertThrows(BackupException.WrongApplication::class.java) { envelope.open(foreign, passphrase) }
        // Even with a wrong passphrase: identity is checked first.
        assertThrows(BackupException.WrongApplication::class.java) { envelope.open(foreign, "x") }
    }

    @Test fun relabelledAppFailsEvenForTheSameAppId() {
        // A record relabelled to this app (same passphrase) must fail on the tag.
        val foreign = BackupEnvelope("other-app").seal(payload, 1, passphrase, BackupEnvelope.MIN_ITERATIONS)
        val relabelled = tamper(foreign) { it["app"] = JsonPrimitive("test-app") }
        assertThrows(BackupException.AuthenticationFailed::class.java) { envelope.open(relabelled, passphrase) }
    }

    @Test fun shortPassphraseIsRejectedWhenSealingAndOpening() {
        assertThrows(BackupException.WeakPassphrase::class.java) {
            envelope.seal(payload, 1, "short", BackupEnvelope.MIN_ITERATIONS)
        }
        assertThrows(BackupException.WeakPassphrase::class.java) { envelope.open(seal(), "short") }
    }

    @Test fun loneSurrogateInPassphraseIsRejected() {
        assertThrows(BackupException.WeakPassphrase::class.java) {
            envelope.seal(payload, 1, "abcdefgh\uD800", BackupEnvelope.MIN_ITERATIONS)
        }
    }

    @Test fun nonBmpPassphraseRoundTrips() {
        val pass = "pass\uD83D\uDD11word" // a key emoji, valid surrogate pair
        val text = envelope.seal(payload, 1, pass, BackupEnvelope.MIN_ITERATIONS)
        assertArrayEquals(payload, envelope.open(text, pass).plaintext)
    }

    @Test fun notJsonAndWrongShapesAreNotBackups() {
        assertThrows(BackupException.NotABackup::class.java) { envelope.open("hello", passphrase) }
        assertThrows(BackupException.NotABackup::class.java) { envelope.open("[1,2]", passphrase) }
        assertThrows(BackupException.NotABackup::class.java) { envelope.open("{}", passphrase) }
        assertThrows(BackupException.NotABackup::class.java) {
            envelope.open("""{"format":"something-else"}""", passphrase)
        }
    }

    @Test fun structuralProblemsAreMalformedOrUnsupported() {
        val good = seal()
        assertThrows(BackupException.Malformed::class.java) {
            envelope.open(tamper(good) { it["extra"] = JsonPrimitive("x") }, passphrase)
        }
        assertThrows(BackupException.Malformed::class.java) {
            envelope.open(tamper(good) { it["iterations"] = JsonPrimitive(99_999) }, passphrase)
        }
        assertThrows(BackupException.Malformed::class.java) {
            envelope.open(tamper(good) { it["iterations"] = JsonPrimitive(2_000_001) }, passphrase)
        }
        assertThrows(BackupException.Malformed::class.java) {
            envelope.open(tamper(good) { it["salt"] = JsonPrimitive("AAAA") }, passphrase)
        }
        assertThrows(BackupException.Malformed::class.java) {
            envelope.open(tamper(good) { it["iv"] = JsonPrimitive("not base64!") }, passphrase)
        }
        assertThrows(BackupException.Malformed::class.java) {
            envelope.open(tamper(good) { it["schema"] = JsonPrimitive(0) }, passphrase)
        }
        assertThrows(BackupException.Malformed::class.java) {
            envelope.open(tamper(good) { it.remove("ct") }, passphrase)
        }
        assertThrows(BackupException.Unsupported::class.java) {
            envelope.open(tamper(good) { it["envelope"] = JsonPrimitive(3) }, passphrase)
        }
        assertThrows(BackupException.Unsupported::class.java) {
            envelope.open(tamper(good) { it["kdf"] = JsonPrimitive("scrypt") }, passphrase)
        }
        assertThrows(BackupException.Unsupported::class.java) {
            envelope.open(tamper(good) { it["cipher"] = JsonPrimitive("AES-128-GCM") }, passphrase)
        }
    }

    @Test fun nonCanonicalBase64IsRejected() {
        val good = Json.parseToJsonElement(seal()).jsonObject
        val iv = (good["iv"] as JsonPrimitive).content // 16 chars, no padding
        val padded = "$iv===="
        assertThrows(BackupException.Malformed::class.java) {
            envelope.open(tamper(seal()) { it["iv"] = JsonPrimitive(padded) }, passphrase)
        }
    }

    @Test fun additionalDataIsTheCompactFixedOrderJson() {
        assertEquals(
            """{"format":"ops-vault-encrypted","app":"a","envelope":2,"kdf":"PBKDF2-SHA256",""" +
                """"cipher":"AES-256-GCM","iterations":220000,"schema":3,"salt":"c2FsdA=="}""",
            String(BackupEnvelope.additionalData("a", 220000, 3, "c2FsdA=="), Charsets.UTF_8),
        )
    }

    @Test fun invalidAppIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupEnvelope("has space") }
        assertThrows(IllegalArgumentException::class.java) { BackupEnvelope("quote\"") }
    }

    /**
     * Interoperability: an envelope produced by an independent WebCrypto
     * implementation of the documented format (PBKDF2-SHA256 100000, AES-256-GCM,
     * AAD as above, schema 3, app "interop-check") opens here.
     */
    @Test fun opensAnEnvelopeWrittenByAnIndependentImplementation() {
        val fixture = """{"format":"ops-vault-encrypted","app":"interop-check","envelope":2,"kdf":"PBKDF2-SHA256","cipher":"AES-256-GCM","iterations":100000,"schema":3,"salt":"1PGfPlTHmcJBRMRwpk/j/g==","iv":"lAqTBev9N6Aw1YV1","ct":"/r+gtPcwrdt0QFqsrDpLLDmgRYXll+ELUsIjTYMHDHtXAADXVo6cW9zuEqiV3gXUeQMR5ZSXzSh5+rwl12hTUN7g5MDOL+hC7mlLd7yoRF1EcmtqVg/J"}"""
        val opened = BackupEnvelope("interop-check").open(fixture, "correct horse battery")
        assertEquals(3, opened.schema)
        assertEquals(
            """{"greeting":"héllo wörld ✓","numbers":[1,2,3],"nested":{"ok":true}}""",
            String(opened.plaintext, Charsets.UTF_8),
        )
    }

    @Test fun pbkdf2MatchesPublishedTestVectors() {
        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
        val pw = "password".toByteArray()
        val salt = "salt".toByteArray()
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            hex(Pbkdf2.hmacSha256(pw, salt, 1)),
        )
        assertEquals(
            "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
            hex(Pbkdf2.hmacSha256(pw, salt, 2)),
        )
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            hex(Pbkdf2.hmacSha256(pw, salt, 4096)),
        )
        // dkLen 40 spans two blocks.
        assertEquals(
            "348c89dbcbd32b2f32d814b8116e84cf2b17347ebc1800181c4e2a1fb8dd53e1c635518c7dac47e9",
            hex(
                Pbkdf2.hmacSha256(
                    "passwordPASSWORDpassword".toByteArray(),
                    "saltSALTsaltSALTsaltSALTsaltSALTsalt".toByteArray(), 4096, 40,
                ),
            ),
        )
        assertTrue(Pbkdf2.hmacSha256(pw, salt, 1).size == 32)
    }
}
