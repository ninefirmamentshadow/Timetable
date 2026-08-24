package com.sovereignops.timetable.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sovereignops.timetable.data.entity.BookingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookingDao {

    @Query("SELECT * FROM bookings ORDER BY startEpochSecond ASC")
    fun observeAll(): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE id = :id")
    suspend fun byId(id: Long): BookingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BookingEntity): Long

    @Update
    suspend fun update(entity: BookingEntity)

    @Delete
    suspend fun delete(entity: BookingEntity)

    @Query("DELETE FROM bookings WHERE id = :id")
    suspend fun deleteById(id: Long)
}
