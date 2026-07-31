package com.clocktools.timetable.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones ORDER BY name ASC")
    fun observeAll(): Flow<List<Zone>>

    @Query("SELECT * FROM zones WHERE id = :id")
    suspend fun getById(id: Long): Zone?

    @Insert
    suspend fun insert(zone: Zone): Long

    @Update
    suspend fun update(zone: Zone)

    @Delete
    suspend fun delete(zone: Zone)
}
