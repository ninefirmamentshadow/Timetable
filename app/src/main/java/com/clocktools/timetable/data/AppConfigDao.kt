package com.clocktools.timetable.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppConfigDao {
    @Query("SELECT * FROM app_config WHERE id = 0 LIMIT 1")
    fun observe(): Flow<AppConfig?>

    @Query("SELECT * FROM app_config WHERE id = 0 LIMIT 1")
    suspend fun getOnce(): AppConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: AppConfig)
}
