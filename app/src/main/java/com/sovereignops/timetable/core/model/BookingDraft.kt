package com.sovereignops.timetable.core.model

import com.sovereignops.timetable.core.Money
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Why a draft cannot become a [Booking]. */
enum class DraftError { ALIAS_REQUIRED, DURATION_INVALID, RATE_INVALID }

sealed interface DraftResult {
    data class Ok(val booking: Booking) : DraftResult
    data class Invalid(val errors: Set<DraftError>) : DraftResult
}

/**
 * The editor's in-progress form, as plain values: text exactly as typed, the
 * chosen date, time and status, and the two flags. Serializable so the editor
 * can keep it in saved state and survive process death. Validation is pure and
 * lives here, not in the UI.
 */
data class BookingDraft(
    val alias: String = "",
    val date: LocalDate,
    val time: LocalTime = LocalTime.of(19, 0),
    val durationText: String = "",
    val rateText: String = "",
    val status: BookingStatus = BookingStatus.INQUIRY,
    val locationLabel: String = "",
    val notes: String = "",
    val screened: Boolean = false,
    val depositReceived: Boolean = false,
) : Serializable {

    /** Validate and build the booking with [id] (0 inserts a new one). */
    fun toBooking(id: Long): DraftResult {
        val errors = linkedSetOf<DraftError>()
        val cleanAlias = alias.trim()
        if (cleanAlias.isEmpty()) errors += DraftError.ALIAS_REQUIRED
        val duration = durationText.trim().toIntOrNull()
        if (duration == null || duration <= 0) errors += DraftError.DURATION_INVALID
        val rate = Money.parse(rateText)
        if (rate == null || rate.cents < 0) errors += DraftError.RATE_INVALID
        if (errors.isNotEmpty()) return DraftResult.Invalid(errors)
        return DraftResult.Ok(
            Booking(
                id = id,
                start = LocalDateTime.of(date, time),
                durationMinutes = duration!!,
                clientAlias = cleanAlias,
                rate = rate!!,
                status = status,
                locationLabel = locationLabel.trim(),
                screened = screened,
                depositReceived = depositReceived,
                notes = notes.trim(),
            ),
        )
    }

    companion object {
        private const val serialVersionUID = 1L

        fun from(booking: Booking) = BookingDraft(
            alias = booking.clientAlias,
            date = booking.start.toLocalDate(),
            time = booking.start.toLocalTime(),
            durationText = booking.durationMinutes.toString(),
            rateText = booking.rate.format(),
            status = booking.status,
            locationLabel = booking.locationLabel,
            notes = booking.notes,
            screened = booking.screened,
            depositReceived = booking.depositReceived,
        )

        /** A blank draft for a new booking. */
        fun blank(today: LocalDate, defaultDurationMinutes: Int) =
            BookingDraft(date = today, durationText = defaultDurationMinutes.toString())
    }
}
