package com.sovereignops.timetable.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sovereignops.timetable.data.entity.AvailabilityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AvailabilityDao {

    @Query("SELECT * FROM availability ORDER BY dayOfWeek ASC, startMinuteOfDay ASC")
    fun observeAll(): Flow<List<AvailabilityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AvailabilityEntity): Long

    @Delete
    suspend fun delete(entity: AvailabilityEntity)

    @Query("DELETE FROM availability WHERE id = :id")
    suspend fun deleteById(id: Long)
}
