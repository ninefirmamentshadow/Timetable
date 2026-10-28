package com.sovereignops.timetable.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Migration scaffolding. The schema JSON for every released version is committed
 * under app/schemas and visible here as test assets. When the schema version is
 * raised: commit the new schema JSON, add an additive Migration to
 * AppDatabase.MIGRATIONS (never edit an old one), and add a case below that
 * creates the old version, inserts rows, migrates, and checks they survived.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val dbName = "migration-test.db"

    /** The schema JSON is bundled in the debug variant's assets only; the release variant skips these tests. */
    @Before
    fun requireSchemaAssets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val present = context.assets.list(AppDatabase::class.java.name).orEmpty().contains("1.json")
        Assume.assumeTrue("schema assets are only bundled in the debug variant", present)
    }

    private fun committedVersions(): List<Int> {
        val dir = File("schemas/${AppDatabase::class.java.name}")
        return dir.listFiles { f -> f.extension == "json" }.orEmpty().map { it.nameWithoutExtension.toInt() }.sorted()
    }

    @Test fun schemaFilesAreCommittedAndContiguousUpToTheCurrentVersion() {
        val versions = committedVersions()
        assertTrue("app/schemas is empty; build once to export the schema", versions.isNotEmpty())
        assertEquals((1..versions.last()).toList(), versions)
        assertEquals("the newest committed schema must be the database's version", CURRENT_VERSION, versions.last())
    }

    @Test fun versionOneSchemaOpensWithRowsAndTheCurrentCodeReadsThem() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO bookings (id, startEpochSecond, durationMinutes, clientAlias, rateCents, status, " +
                    "locationLabel, screened, depositReceived, notes) VALUES (1, 1792000000, 60, 'Alias A', 10000, " +
                    "'CONFIRMED', 'Place 1', 1, 0, 'n')",
            )
            execSQL("INSERT INTO availability (id, dayOfWeek, startMinuteOfDay, endMinuteOfDay) VALUES (1, 5, 1080, 1440)")
            close()
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val booking = db.bookingDao().let { dao -> kotlinx.coroutines.runBlocking { dao.getById(1) } }!!
            assertEquals("Alias A", booking.clientAlias)
            assertEquals(10000, booking.rateCents)
            assertEquals(1440, kotlinx.coroutines.runBlocking { db.availabilityDao().getById(1) }!!.endMinuteOfDay)
        } finally {
            db.close()
        }
    }

    // 1 -> 2 adds the booking_drafts table and must leave every booking and window as it was.
    @Test fun versionOneMigratesToTwoKeepingEveryRowAndAddsAnEmptyDraftTable() {
        helper.createDatabase("$dbName-1to2", 1).apply {
            execSQL(
                "INSERT INTO bookings (id, startEpochSecond, durationMinutes, clientAlias, rateCents, status, " +
                    "locationLabel, screened, depositReceived, notes) VALUES (7, 1792000000, 45, 'Alias B', 5000, " +
                    "'INQUIRY', '', 0, 1, '')",
            )
            execSQL("INSERT INTO availability (id, dayOfWeek, startMinuteOfDay, endMinuteOfDay) VALUES (3, 6, 1080, 1380)")
            close()
        }
        helper.runMigrationsAndValidate("$dbName-1to2", 2, true, *AppDatabase.MIGRATIONS).use { db ->
            db.query("SELECT clientAlias, rateCents, depositReceived FROM bookings WHERE id = 7").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Alias B", c.getString(0))
                assertEquals(5000, c.getLong(1))
                assertEquals(1, c.getInt(2))
            }
            db.query("SELECT endMinuteOfDay FROM availability WHERE id = 3").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1380, c.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM booking_drafts").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        }
    }

    // 2 -> 3 adds the nullable amount-received columns; existing bookings and drafts read as "not recorded".
    @Test fun versionTwoMigratesToThreeWithAmountsNotRecorded() {
        helper.createDatabase("$dbName-2to3", 2).apply {
            execSQL(
                "INSERT INTO bookings (id, startEpochSecond, durationMinutes, clientAlias, rateCents, status, " +
                    "locationLabel, screened, depositReceived, notes) VALUES (4, 1792000000, 60, 'Alias C', 12500, " +
                    "'COMPLETED', '', 1, 0, 'n')",
            )
            execSQL(
                "INSERT INTO booking_drafts (bookingId, alias, dateEpochDay, minuteOfDay, durationText, rateText, status, " +
                    "locationLabel, notes, screened, depositReceived, updatedAtEpochSecond) VALUES (0, 'Alias D', 20750, 1140, " +
                    "'60', '50', 'INQUIRY', '', '', 0, 0, 1792000000)",
            )
            close()
        }
        helper.runMigrationsAndValidate("$dbName-2to3", 3, true, *AppDatabase.MIGRATIONS).use { db ->
            db.query("SELECT clientAlias, rateCents, status, receivedCents FROM bookings WHERE id = 4").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Alias C", c.getString(0))
                assertEquals(12500, c.getLong(1))
                assertEquals("COMPLETED", c.getString(2))
                assertTrue("a completed booking is not silently marked paid", c.isNull(3))
            }
            db.query("SELECT alias, receivedText FROM booking_drafts WHERE bookingId = 0").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Alias D", c.getString(0))
                assertTrue(c.isNull(1))
            }
        }
    }

    /** Every older committed version must migrate to the current one without losing its tables. */
    @Test fun everyCommittedVersionMigratesToTheCurrentOne() {
        for (from in committedVersions().filter { it < CURRENT_VERSION }) {
            helper.createDatabase("$dbName-$from", from).close()
            helper.runMigrationsAndValidate("$dbName-$from", CURRENT_VERSION, true, *AppDatabase.MIGRATIONS).close()
        }
    }

    private companion object {
        const val CURRENT_VERSION = 3
    }
}
