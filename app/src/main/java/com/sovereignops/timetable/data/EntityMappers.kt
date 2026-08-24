package com.sovereignops.timetable.data

import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.data.entity.AvailabilityEntity
import com.sovereignops.timetable.data.entity.BookingEntity
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Mapping between flat storage rows and the domain model. The wall-clock time is
 * encoded against a fixed UTC reference so the round trip is exact and the app
 * never has to pick a time zone; it is a storage key, not an instant.
 */

fun BookingEntity.toModel(): Booking = Booking(
    id = id,
    start = LocalDateTime.ofEpochSecond(startEpochSecond, 0, ZoneOffset.UTC),
    durationMinutes = durationMinutes,
    clientAlias = clientAlias,
    rate = Money(rateCents),
    status = runCatching { BookingStatus.valueOf(status) }.getOrDefault(BookingStatus.INQUIRY),
    locationLabel = locationLabel,
    screened = screened,
    depositReceived = depositReceived,
    notes = notes,
)

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

fun AvailabilityEntity.toModel(): AvailabilityWindow = AvailabilityWindow(
    id = id,
    dayOfWeek = DayOfWeek.of(dayOfWeek),
    start = LocalTime.ofSecondOfDay(startMinuteOfDay.toLong() * 60),
    end = LocalTime.ofSecondOfDay(endMinuteOfDay.toLong() * 60),
)

fun AvailabilityWindow.toEntity(): AvailabilityEntity = AvailabilityEntity(
    id = id,
    dayOfWeek = dayOfWeek.value,
    startMinuteOfDay = start.toSecondOfDay() / 60,
    endMinuteOfDay = end.toSecondOfDay() / 60,
)
