package com.sovereignops.timetable.data.backup

import com.sovereignops.timetable.data.entity.AvailabilityEntity
import com.sovereignops.timetable.data.entity.BookingEntity
import com.sovereignops.timetable.data.toModelResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The decrypted contents of a Timetable backup, schema 1. See docs/BACKUP_FORMAT.md. */
@Serializable
data class BackupPayload(
    val exportedAt: String,
    val settings: BackupSettings,
    val bookings: List<BackupBooking>,
    val availability: List<BackupWindow>,
)

@Serializable
data class BackupSettings(val travelBufferMinutes: Int, val defaultDurationMinutes: Int)

@Serializable
data class BackupBooking(
    val id: Long,
    /** Local date-time, ISO-8601 without zone, e.g. 2026-10-21T19:00. */
    val start: String,
    val durationMinutes: Int,
    val alias: String,
    val rateCents: Long,
    val status: String,
    val location: String = "",
    val screened: Boolean = false,
    val depositReceived: Boolean = false,
    val notes: String = "",
)

@Serializable
data class BackupWindow(
    val id: Long,
    /** ISO weekday, 1 = Monday .. 7 = Sunday. */
    @SerialName("day") val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
)

/** A backup that decrypted but is not acceptable data. Nothing was changed. */
class InvalidBackup(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Rows ready to be written, already validated. */
class RestorePlan(
    val bookings: List<BookingEntity>,
    val availability: List<AvailabilityEntity>,
    val travelBufferMinutes: Int,
    val defaultDurationMinutes: Int,
    val exportedAt: String,
)

/** Encode and strictly validate the payload; the only place payload rules live. */
object BackupPayloadCodec {

    const val SCHEMA = 1
    const val MAX_BOOKINGS = 200_000
    const val MAX_WINDOWS = 5_000
    const val MAX_TEXT = 10_000

    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        isLenient = false
    }

    fun encode(payload: BackupPayload): ByteArray =
        json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8)

    fun toPayload(
        bookings: List<BookingEntity>,
        availability: List<AvailabilityEntity>,
        travelBufferMinutes: Int,
        defaultDurationMinutes: Int,
        exportedAt: LocalDateTime,
    ) = BackupPayload(
        exportedAt = exportedAt.toString(),
        settings = BackupSettings(travelBufferMinutes, defaultDurationMinutes),
        bookings = bookings.map {
            BackupBooking(
                id = it.id,
                start = LocalDateTime.ofEpochSecond(it.startEpochSecond, 0, ZoneOffset.UTC).toString(),
                durationMinutes = it.durationMinutes,
                alias = it.clientAlias,
                rateCents = it.rateCents,
                status = it.status,
                location = it.locationLabel,
                screened = it.screened,
                depositReceived = it.depositReceived,
                notes = it.notes,
            )
        },
        availability = availability.map { BackupWindow(it.id, it.dayOfWeek, it.startMinuteOfDay, it.endMinuteOfDay) },
    )

    /**
     * Parse and validate. Every row must be a booking or window the app can read,
     * ids must be positive and unique, sizes bounded. One bad row rejects the
     * whole backup: a restore is all or nothing.
     */
    fun decode(bytes: ByteArray): RestorePlan {
        val payload = try {
            json.decodeFromString(BackupPayload.serializer(), String(bytes, Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw InvalidBackup("The backup contents are not valid", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidBackup("The backup contents are not valid", e)
        }
        if (payload.bookings.size > MAX_BOOKINGS) throw InvalidBackup("The backup has too many bookings")
        if (payload.availability.size > MAX_WINDOWS) throw InvalidBackup("The backup has too many availability windows")
        if (payload.settings.travelBufferMinutes !in 0..480) throw InvalidBackup("The travel buffer is out of range")
        if (payload.settings.defaultDurationMinutes !in 15..1440) throw InvalidBackup("The default duration is out of range")

        val bookingIds = HashSet<Long>()
        val bookings = payload.bookings.mapIndexed { index, b ->
            if (b.id < 1 || !bookingIds.add(b.id)) throw InvalidBackup("Booking ${index + 1} has a missing or repeated id")
            if (b.alias.length > MAX_TEXT || b.location.length > MAX_TEXT || b.notes.length > MAX_TEXT) {
                throw InvalidBackup("Booking ${index + 1} has text that is too long")
            }
            val start = try {
                LocalDateTime.parse(b.start)
            } catch (e: DateTimeException) {
                throw InvalidBackup("Booking ${index + 1} has an invalid start time", e)
            }
            val row = BookingEntity(
                id = b.id,
                startEpochSecond = start.toEpochSecond(ZoneOffset.UTC),
                durationMinutes = b.durationMinutes,
                clientAlias = b.alias,
                rateCents = b.rateCents,
                status = b.status,
                locationLabel = b.location,
                screened = b.screened,
                depositReceived = b.depositReceived,
                notes = b.notes,
            )
            row.toModelResult().getOrElse { throw InvalidBackup("Booking ${index + 1} is not valid", it) }
            row
        }

        val windowIds = HashSet<Long>()
        val windows = payload.availability.mapIndexed { index, w ->
            if (w.id < 1 || !windowIds.add(w.id)) throw InvalidBackup("Window ${index + 1} has a missing or repeated id")
            val row = AvailabilityEntity(w.id, w.dayOfWeek, w.startMinute, w.endMinute)
            row.toModelResult().getOrElse { throw InvalidBackup("Window ${index + 1} is not valid", it) }
            row
        }
        return RestorePlan(
            bookings, windows, payload.settings.travelBufferMinutes, payload.settings.defaultDurationMinutes,
            payload.exportedAt,
        )
    }
}
