package com.sovereignops.timetable.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sovereignops.timetable.data.dao.AvailabilityDao
import com.sovereignops.timetable.data.dao.BackupDao
import com.sovereignops.timetable.data.dao.BookingDao
import com.sovereignops.timetable.data.entity.AvailabilityEntity
import com.sovereignops.timetable.data.entity.BookingEntity

@Database(
    entities = [BookingEntity::class, AvailabilityEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookingDao(): BookingDao
    abstract fun availabilityDao(): AvailabilityDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "timetable.db"

        /**
         * Migrations, append-only and additive: a new entry is added when the schema
         * version is raised, an existing one is never edited. Each needs the new
         * schema JSON committed under app/schemas and a case in MigrationTest.
         */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf()

        @Volatile private var instance: AppDatabase? = null

        /** Test hook: use [db] (or the real database again when null) for [get]. */
        @androidx.annotation.VisibleForTesting
        fun setInstanceForTesting(db: AppDatabase?) {
            synchronized(this) { instance = db }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME,
                ).addMigrations(*MIGRATIONS).build().also { instance = it }
            }
    }
}
