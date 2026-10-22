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
                    "locationLabel, screened, depositReceived, notes) VALUES (1, 1792000000, 60, 'Alias A', 30000, " +
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
            assertEquals(30000, booking.rateCents)
            assertEquals(1440, kotlinx.coroutines.runBlocking { db.availabilityDao().getById(1) }!!.endMinuteOfDay)
        } finally {
            db.close()
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
        const val CURRENT_VERSION = 1
    }
}
