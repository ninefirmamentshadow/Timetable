package com.sovereignops.timetable.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sovereignops.timetable.data.entity.AvailabilityEntity
import com.sovereignops.timetable.data.entity.BookingDraftEntity
import com.sovereignops.timetable.data.entity.BookingEntity

/**
 * Whole-table operations for backup and restore. [replaceAll] is one database
 * transaction: if anything throws (a duplicate id, a full disk), every change in
 * it is rolled back and the previous contents are still there.
 */
@Dao
abstract class BackupDao {

    @Query("SELECT * FROM bookings ORDER BY startEpochSecond ASC, id ASC")
    abstract suspend fun allBookings(): List<BookingEntity>

    @Query("SELECT * FROM availability ORDER BY dayOfWeek ASC, startMinuteOfDay ASC, id ASC")
    abstract suspend fun allAvailability(): List<AvailabilityEntity>

    @Query("SELECT * FROM booking_drafts ORDER BY bookingId ASC")
    abstract suspend fun allDrafts(): List<BookingDraftEntity>

    @Query("DELETE FROM bookings")
    protected abstract suspend fun clearBookings()

    @Query("DELETE FROM booking_drafts")
    protected abstract suspend fun clearDrafts()

    @Query("DELETE FROM availability")
    protected abstract suspend fun clearAvailability()

    // ABORT, not REPLACE: a duplicate id must fail the transaction, never silently overwrite.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertBookings(rows: List<BookingEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAvailability(rows: List<AvailabilityEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertDrafts(rows: List<BookingDraftEntity>)

    /**
     * Replace bookings, availability and editor drafts together. Drafts go with the
     * bookings they edit, so a kept draft can never end up pointing at a different
     * booking that happens to reuse its id; the pre-restore safety copy keeps the
     * replaced ones.
     */
    @Transaction
    open suspend fun replaceAll(
        bookings: List<BookingEntity>,
        availability: List<AvailabilityEntity>,
        drafts: List<BookingDraftEntity> = emptyList(),
    ) {
        clearBookings()
        clearAvailability()
        clearDrafts()
        insertBookings(bookings)
        insertAvailability(availability)
        insertDrafts(drafts)
    }
}
