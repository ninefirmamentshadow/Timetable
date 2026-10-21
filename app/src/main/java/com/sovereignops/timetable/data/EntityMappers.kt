package com.sovereignops.timetable.data

import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.data.entity.AvailabilityEntity
import com.sovereignops.timetable.data.entity.BookingEntity
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Mapping between flat storage rows and the domain model. The wall-clock time is
 * encoded against a fixed UTC reference so the round trip is exact and the app
 * never has to pick a time zone; it is a storage key, not an instant.
 *
 * Reading never throws: a row that cannot be turned into a valid model (an
 * unknown status, a non-positive duration, an impossible date, a day or minute
 * out of range) comes back as a failed [Result]. The caller skips and counts it;
 * the row itself is never altered or deleted.
 */

/** Why a stored row could not be read. */
class UnreadableRow(message: String, cause: Throwable? = null) : Exception(message, cause)

fun BookingEntity.toModelResult(): Result<Booking> = runCatching {
    val bookingStatus = BookingStatus.entries.firstOrNull { it.name == status }
        ?: throw UnreadableRow("unknown status")
    if (durationMinutes <= 0) throw UnreadableRow("duration must be positive")
    if (rateCents < 0) throw UnreadableRow("rate must not be negative")
    Booking(
        id = id,
        start = LocalDateTime.ofEpochSecond(startEpochSecond, 0, ZoneOffset.UTC),
        durationMinutes = durationMinutes,
        clientAlias = clientAlias,
        rate = Money(rateCents),
        status = bookingStatus,
        locationLabel = locationLabel,
        screened = screened,
        depositReceived = depositReceived,
        notes = notes,
    )
}.recoverCatching { throw if (it is UnreadableRow) it else UnreadableRow("unreadable booking row", it) }

/** For rows known to be good (tests, freshly written data); throws on a bad row. */
fun BookingEntity.toModel(): Booking = toModelResult().getOrThrow()

fun Booking.toEntity(): BookingEntity = BookingEntity(
    id = id,
    startEpochSecond = start.toEpochSecond(ZoneOffset.UTC),
    durationMinutes = durationMinutes,
    clientAlias = clientAlias,
    rateCents = rate.cents,
    status = status.name,
    locationLabel = locationLabel,
    screened = screened,
    depositReceived = depositReceived,
    notes = notes,
)

fun AvailabilityEntity.toModelResult(): Result<AvailabilityWindow> = runCatching {
    if (dayOfWeek !in 1..7) throw UnreadableRow("day of week out of range")
    AvailabilityWindow(
        id = id,
        dayOfWeek = DayOfWeek.of(dayOfWeek),
        startMinute = startMinuteOfDay,
        endMinute = endMinuteOfDay,
    )
}.recoverCatching { throw if (it is UnreadableRow) it else UnreadableRow("unreadable availability row", it) }

fun AvailabilityEntity.toModel(): AvailabilityWindow = toModelResult().getOrThrow()

fun AvailabilityWindow.toEntity(): AvailabilityEntity = AvailabilityEntity(
    id = id,
    dayOfWeek = dayOfWeek.value,
    startMinuteOfDay = startMinute,
    endMinuteOfDay = endMinute,
)

/** The readable items of a table plus how many rows were skipped as unreadable. */
data class Loaded<T>(val items: List<T>, val unreadable: Int) {
    companion object {
        fun <R, T> from(rows: List<R>, read: (R) -> Result<T>): Loaded<T> {
            val items = ArrayList<T>(rows.size)
            var bad = 0
            for (row in rows) read(row).onSuccess { items += it }.onFailure { bad++ }
            return Loaded(items, bad)
        }
    }
}
