package com.sovereignops.timetable.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sovereignops.timetable.data.entity.BookingDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookingDraftDao {

    @Query("SELECT * FROM booking_drafts ORDER BY updatedAtEpochSecond DESC, bookingId ASC")
    fun observeAll(): Flow<List<BookingDraftEntity>>

    @Query("SELECT * FROM booking_drafts WHERE bookingId = :bookingId")
    suspend fun get(bookingId: Long): BookingDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookingDraftEntity)

    @Query("DELETE FROM booking_drafts WHERE bookingId = :bookingId")
    suspend fun delete(bookingId: Long)
}
