package com.sovereignops.timetable.data.backup

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sovereignops.timetable.backup.BackupEnvelope
import com.sovereignops.timetable.backup.BackupException
import com.sovereignops.timetable.core.model.BookingDraft
import com.sovereignops.timetable.core.model.SavedDraft
import com.sovereignops.timetable.data.AppDatabase
import com.sovereignops.timetable.data.toEntity
import com.sovereignops.timetable.data.Settings
import com.sovereignops.timetable.data.entity.AvailabilityEntity
import com.sovereignops.timetable.data.entity.BookingEntity
import com.sovereignops.timetable.ui.TestSupport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupServiceTest {

    private lateinit var db: AppDatabase
    private lateinit var settings: Settings
    private lateinit var service: BackupService
    private val passphrase = "correct horse battery"

    @Before
    fun setUp() {
        db = TestSupport.installInMemoryDatabase()
        settings = Settings(ApplicationProvider.getApplicationContext())
        settings.travelBufferMinutes = 30
        settings.defaultDurationMinutes = 90
        service = BackupService(db, settings, { LocalDateTime.of(2026, 10, 21, 9, 30) }, BackupEnvelope.MIN_ITERATIONS)
        TestSupport.seed(db)
    }

    @After
    fun tearDown() = TestSupport.uninstallDatabase(db)

    /** JUnit needs a void test method: runBlocking would return the last expression. */
    private fun blocking(block: suspend () -> Unit) { runBlocking { block() } }

    private fun snapshot() = runBlocking { db.backupDao().allBookings() to db.backupDao().allAvailability() }

    @Test fun roundTripRestoresEveryRowAndTheSettings() = blocking {
        val before = snapshot()
        val created = service.create(passphrase)
        assertEquals(5, created.bookings)
        assertEquals(2, created.windows)
        assertEquals(0, created.unreadableSkipped)

        // Wipe and change settings, then restore.
        db.backupDao().replaceAll(emptyList(), emptyList())
        settings.travelBufferMinutes = 5
        settings.defaultDurationMinutes = 15
        assertEquals(emptyList<BookingEntity>() to emptyList<AvailabilityEntity>(), snapshot())

        service.apply(service.prepareRestore(created.text, passphrase))

        assertEquals(before, snapshot())
        assertEquals(30, settings.travelBufferMinutes)
        assertEquals(90, settings.defaultDurationMinutes)
    }

    @Test fun theBackupFileHasNoReadablePlaintext() = blocking {
        val text = service.create(passphrase).text
        assertTrue(!text.contains("Alias A") && !text.contains("CONFIRMED") && !text.contains("bookings"))
        assertEquals("timetable", Json.parseToJsonElement(text).jsonObject["app"].toString().trim('"'))
    }

    // Wrong passphrase: clean error, no data change.
    @Test fun wrongPassphraseFailsCleanlyAndChangesNothing() = blocking {
        val text = service.create(passphrase).text
        val before = snapshot()
        assertThrows(BackupException.AuthenticationFailed::class.java) {
            runBlocking { service.prepareRestore(text, "the wrong passphrase") }
        }
        assertEquals(before, snapshot())
        assertEquals(30, settings.travelBufferMinutes)
    }

    @Test fun tamperedCiphertextIsRejectedAndChangesNothing() = blocking {
        val text = service.create(passphrase).text
        val obj = Json.parseToJsonElement(text).jsonObject.toMutableMap()
        val ct = Base64.getDecoder().decode((obj["ct"] as JsonPrimitive).content)
        ct[ct.size / 2] = (ct[ct.size / 2].toInt() xor 1).toByte()
        obj["ct"] = JsonPrimitive(Base64.getEncoder().encodeToString(ct))
        val before = snapshot()
        assertThrows(BackupException.AuthenticationFailed::class.java) {
            runBlocking { service.prepareRestore(JsonObject(obj).toString(), passphrase) }
        }
        assertEquals(before, snapshot())
    }

    @Test fun anotherAppsBackupIsRefused() = blocking {
        val foreign = BackupEnvelope("not-timetable").seal("{}".toByteArray(), 1, passphrase, BackupEnvelope.MIN_ITERATIONS)
        val before = snapshot()
        assertThrows(BackupException.WrongApplication::class.java) {
            runBlocking { service.prepareRestore(foreign, passphrase) }
        }
        assertEquals(before, snapshot())
    }

    @Test fun aNewerPayloadSchemaIsRefused() = blocking {
        val newer = BackupEnvelope(BackupService.APP_ID).seal("{}".toByteArray(), BackupPayloadCodec.SCHEMA + 1, passphrase, BackupEnvelope.MIN_ITERATIONS)
        assertThrows(BackupException.Unsupported::class.java) { runBlocking { service.prepareRestore(newer, passphrase) } }
    }

    private fun sealed(payloadJson: String, schema: Int = 1) =
        BackupEnvelope(BackupService.APP_ID).seal(payloadJson.toByteArray(), schema, passphrase, BackupEnvelope.MIN_ITERATIONS)

    private fun payload(bookings: String = "[]", windows: String = "[]", settings: String = """{"travelBufferMinutes":45,"defaultDurationMinutes":60}""") =
        """{"exportedAt":"2026-10-21T09:30","settings":$settings,"bookings":$bookings,"availability":$windows}"""

    private fun bookingJson(id: Int = 1, status: String = "CONFIRMED", start: String = "2026-10-21T19:00", duration: Int = 60) =
        """{"id":$id,"start":"$start","durationMinutes":$duration,"alias":"A","rateCents":100,"status":"$status"}"""

    @Test fun anInvalidRowRejectsTheWholeBackupAndChangesNothing() {
        val before = snapshot()
        val bad = listOf(
            payload(bookings = "[${bookingJson(1)},${bookingJson(2, status = "BOGUS")}]"),
            payload(bookings = "[${bookingJson(1, duration = 0)}]"),
            payload(bookings = "[${bookingJson(1, start = "yesterday")}]"),
            payload(bookings = "[${bookingJson(1)},${bookingJson(1)}]"), // repeated id
            payload(bookings = "[${bookingJson(0)}]"), // id must be positive
            payload(windows = """[{"id":1,"day":9,"startMinute":60,"endMinute":120}]"""),
            payload(windows = """[{"id":1,"day":5,"startMinute":120,"endMinute":60}]"""),
            payload(settings = """{"travelBufferMinutes":9999,"defaultDurationMinutes":60}"""),
            payload(settings = """{"travelBufferMinutes":10,"defaultDurationMinutes":5}"""),
            """{"not":"a payload"}""",
            "garbage",
            payload().replace("\"bookings\"", "\"extra\":1,\"bookings\""), // unknown field
        )
        for (json in bad) {
            assertThrows("should reject: $json", InvalidBackup::class.java) {
                runBlocking { service.prepareRestore(sealed(json), passphrase) }
            }
        }
        assertEquals(before, snapshot())
        assertEquals(30, settings.travelBufferMinutes)
    }

    @Test fun aGoodHandWrittenPayloadRestores() = blocking {
        val plan = service.prepareRestore(
            sealed(payload(bookings = "[${bookingJson(7)}]", windows = """[{"id":3,"day":5,"startMinute":1080,"endMinute":1440}]""")),
            passphrase,
        )
        service.apply(plan)
        val (bookings, windows) = snapshot()
        assertEquals(listOf(7L), bookings.map { it.id })
        assertEquals(listOf(AvailabilityEntity(3, 5, 1080, 1440)), windows)
        assertEquals(45, settings.travelBufferMinutes)
        assertEquals(60, settings.defaultDurationMinutes)
    }

    // Transaction: a failure part-way through the replace rolls everything back.
    @Test fun aFailureDuringReplaceRollsBackToThePreviousData() = blocking {
        val before = snapshot()
        val dup = BookingEntity(99, 1_792_000_000, 60, "Dup", 100, "CONFIRMED", "", false, false, "")
        assertThrows(Exception::class.java) {
            runBlocking { db.backupDao().replaceAll(listOf(dup, dup), emptyList()) } // second insert hits the same id
        }
        assertEquals("old data is still there", before, snapshot())
    }

    @Test fun unreadableRowsAreLeftOutAndCounted() = blocking {
        db.bookingDao().insert(BookingEntity(0, 1_792_000_000, 0, "Bad", 100, "CONFIRMED", "", false, false, ""))
        db.availabilityDao().insert(AvailabilityEntity(0, 9, 60, 120))
        val created = service.create(passphrase)
        assertEquals(5, created.bookings)
        assertEquals(2, created.windows)
        assertEquals(2, created.unreadableSkipped)
        // The backup it produced restores cleanly (the bad rows were never in it).
        service.apply(service.prepareRestore(created.text, passphrase))
        assertEquals(5, snapshot().first.size)
    }

    @Test fun inspectNeedsNoPassphraseAndRejectsNonBackups() = blocking {
        val text = service.create(passphrase).text
        assertEquals(BackupEnvelope.Header(BackupPayloadCodec.SCHEMA, BackupEnvelope.MIN_ITERATIONS), service.inspect(text))
        assertThrows(BackupException.NotABackup::class.java) { service.inspect("hello") }
    }

    // ------------------------------------------------------ drafts (schema 2)

    private suspend fun keepDraft(id: Long, alias: String) {
        db.draftDao().upsert(
            SavedDraft(id, BookingDraft.blank(LocalDate.of(2026, 10, 22), 45).copy(alias = alias, rateText = "abc", notes = "typed"), LocalDateTime.of(2026, 10, 21, 9, 0)).toEntity(),
        )
    }

    @Test fun unsavedDraftsAreInTheBackupAndRestoreExactlyAsTyped() = blocking {
        keepDraft(0, "New one")
        keepDraft(3, "Edit of 3")
        val drafts = db.backupDao().allDrafts()
        val created = service.create(passphrase)
        assertEquals(2, created.drafts)
        assertEquals(BackupEnvelope.Header(2, BackupEnvelope.MIN_ITERATIONS), service.inspect(created.text))
        db.backupDao().replaceAll(emptyList(), emptyList())
        assertTrue(db.backupDao().allDrafts().isEmpty())
        service.apply(service.prepareRestore(created.text, passphrase))
        assertEquals(drafts, db.backupDao().allDrafts())
    }

    // A schema 1 file (made before drafts were backed up) still restores; it replaces the drafts by none.
    @Test fun aSchemaOneBackupStillRestoresAndLeavesNoStaleDrafts() = blocking {
        keepDraft(5, "Points at an old booking 5")
        service.apply(service.prepareRestore(sealed(payload(bookings = "[${bookingJson(5)}]")), passphrase))
        assertEquals(listOf(5L), snapshot().first.map { it.id })
        assertTrue("a draft of the replaced booking 5 must not attach to the restored one", db.backupDao().allDrafts().isEmpty())
    }

    private fun draftJson(id: Int = 0, status: String = "INQUIRY", date: String = "2026-10-22", time: String = "19:00") =
        """{"bookingId":$id,"date":"$date","time":"$time","status":"$status","updatedAt":"2026-10-21T09:00"}"""

    @Test fun anInvalidDraftRejectsTheWholeBackup() {
        val before = snapshot()
        for (drafts in listOf("[${draftJson(status = "BOGUS")}]", "[${draftJson(date = "soon")}]", "[${draftJson(time = "25:00")}]",
            "[${draftJson(1)},${draftJson(1)}]", "[${draftJson(-1)}]")) {
            val json = payload().dropLast(1) + ",\"bookingDrafts\":$drafts}"
            assertThrows("should reject: $drafts", InvalidBackup::class.java) {
                runBlocking { service.prepareRestore(sealed(json, schema = 2), passphrase) }
            }
        }
        assertEquals(before, snapshot())
    }
}
