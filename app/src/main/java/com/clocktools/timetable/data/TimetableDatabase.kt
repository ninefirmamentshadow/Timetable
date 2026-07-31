package com.clocktools.timetable.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

@Database(entities = [Zone::class, AppConfig::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class TimetableDatabase : RoomDatabase() {

    abstract fun zoneDao(): ZoneDao
    abstract fun appConfigDao(): AppConfigDao

    companion object {
        @Volatile
        private var instance: TimetableDatabase? = null

        // Additive-only migration scaffolding: schema changes ship as new MIGRATION_x_y objects
        // appended here (new tables/columns only), never as a destructive fallback.
        private val MIGRATIONS: Array<Migration> = arrayOf()

        fun getInstance(context: Context): TimetableDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TimetableDatabase::class.java,
                    "timetable.db"
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
    }
}
