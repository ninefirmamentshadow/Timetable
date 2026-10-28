package com.sovereignops.timetable.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sovereignops.timetable.data.dao.AvailabilityDao
import com.sovereignops.timetable.data.dao.BackupDao
import com.sovereignops.timetable.data.dao.BookingDao
import com.sovereignops.timetable.data.dao.BookingDraftDao
import com.sovereignops.timetable.data.entity.AvailabilityEntity
import com.sovereignops.timetable.data.entity.BookingDraftEntity
import com.sovereignops.timetable.data.entity.BookingEntity

@Database(
    entities = [BookingEntity::class, AvailabilityEntity::class, BookingDraftEntity::class],
    version = 3,
    exportSchema = true,
    // Both steps are purely additive; Room generates them from the committed schemas.
    // 1 -> 2 adds the booking_drafts table; 2 -> 3 adds the nullable amount-received
    // columns (bookings.receivedCents, booking_drafts.receivedText).
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookingDao(): BookingDao
    abstract fun availabilityDao(): AvailabilityDao
    abstract fun backupDao(): BackupDao
    abstract fun draftDao(): BookingDraftDao

    companion object {
        const val NAME = "timetable.db"

        /**
         * Manual migrations, append-only and additive: a new entry is added when the schema
         * version is raised, an existing one is never edited. Each needs the new
         * schema JSON committed under app/schemas and a case in MigrationTest. Purely
         * additive steps (1 -> 2) are AutoMigrations declared on @Database instead.
         */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf()

        @Volatile private var instance: AppDatabase? = null

        /** Test hook: use [db] (or the real database again when null) for [get]. */
        @androidx.annotation.VisibleForTesting
        fun setInstanceForTesting(db: AppDatabase?) {
            synchronized(this) { instance = db }
        }

        /**
         * The one database. A new install is created encrypted; an existing plaintext
         * database stays plaintext until the operator asks for encryption (see
         * DatabaseEncryption). Throws DatabaseKeyUnavailable if an encrypted
         * database's key is gone.
         */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase {
            val factory = DatabaseEncryption.prepare(context)
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .apply { if (factory != null) openHelperFactory(factory) }
                .addMigrations(*MIGRATIONS)
                .build()
        }
    }
}
