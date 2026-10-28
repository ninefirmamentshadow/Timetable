package com.sovereignops.timetable.data

import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingDraft
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.core.model.SavedDraft
import com.sovereignops.timetable.data.entity.AvailabilityEntity
import com.sovereignops.timetable.data.entity.BookingDraftEntity
import com.sovereignops.timetable.data.entity.BookingEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
    if (receivedCents != null && receivedCents < 0) throw UnreadableRow("amount received must not be negative")
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
        amountReceived = receivedCents?.let { Money(it) },
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
    receivedCents = amountReceived?.cents,
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

fun SavedDraft.toEntity(): BookingDraftEntity = BookingDraftEntity(
    bookingId = bookingId,
    alias = draft.alias,
    dateEpochDay = draft.date.toEpochDay(),
    minuteOfDay = draft.time.hour * 60 + draft.time.minute,
    durationText = draft.durationText,
    rateText = draft.rateText,
    status = draft.status.name,
    locationLabel = draft.locationLabel,
    notes = draft.notes,
    screened = draft.screened,
    depositReceived = draft.depositReceived,
    updatedAtEpochSecond = updatedAt.toEpochSecond(ZoneOffset.UTC),
    receivedText = draft.receivedText,
)

/**
 * A draft row always reads back: it holds text the operator typed, so a damaged
 * field falls back to a safe value (status INQUIRY, a clamped date or time) rather
 * than losing the whole draft.
 */
fun BookingDraftEntity.toSavedDraft(): SavedDraft {
    val minute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    return SavedDraft(
        bookingId = bookingId,
        draft = BookingDraft(
            alias = alias,
            date = LocalDate.ofEpochDay(dateEpochDay.coerceIn(MIN_EPOCH_DAY, MAX_EPOCH_DAY)),
            time = LocalTime.of(minute / 60, minute % 60),
            durationText = durationText,
            rateText = rateText,
            status = BookingStatus.entries.firstOrNull { it.name == status } ?: BookingStatus.INQUIRY,
            locationLabel = locationLabel,
            notes = notes,
            screened = screened,
            depositReceived = depositReceived,
            receivedText = receivedText,
        ),
        updatedAt = LocalDateTime.ofEpochSecond(
            updatedAtEpochSecond.coerceIn(MIN_EPOCH_SECOND, MAX_EPOCH_SECOND), 0, ZoneOffset.UTC,
        ),
    )
}

// Years 1..9999: wide enough for any real date, small enough that every java.time call stays in range.
private val MIN_EPOCH_DAY = LocalDate.of(1, 1, 1).toEpochDay()
private val MAX_EPOCH_DAY = LocalDate.of(9999, 12, 31).toEpochDay()
private val MIN_EPOCH_SECOND = MIN_EPOCH_DAY * 86_400
private val MAX_EPOCH_SECOND = MAX_EPOCH_DAY * 86_400

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
