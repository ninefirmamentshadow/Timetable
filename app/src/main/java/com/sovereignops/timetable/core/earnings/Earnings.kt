package com.sovereignops.timetable.core.earnings

import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.Booking
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Earned vs. projected money over a booking list. Earned is real (COMPLETED);
 * projected is open commitments (INQUIRY / CONFIRMED) that have not ended yet.
 * They are never summed together into one misleading total — a confirmed
 * booking is not money in hand.
 *
 * An open booking whose time has already passed is neither: nobody recorded how
 * it ended, so it is counted in [needsReviewCount] and contributes no money to
 * either figure until the operator marks it completed, no-show or cancelled.
 */
data class EarningsSummary(
    val earned: Money,
    val projected: Money,
    val completedCount: Int,
    val projectedCount: Int,
    val needsReviewCount: Int = 0,
)

object Earnings {

    /** Summary over the whole list as of [now]. */
    fun summarize(bookings: List<Booking>, now: LocalDateTime): EarningsSummary {
        var earned = Money.ZERO
        var projected = Money.ZERO
        var earnedN = 0
        var projectedN = 0
        var reviewN = 0
        for (b in bookings) {
            when {
                b.status.isEarned -> {
                    earned += b.rate; earnedN++
                }
                b.status.isProjected -> {
                    if (b.needsReview(now)) {
                        reviewN++
                    } else {
                        projected += b.rate; projectedN++
                    }
                }
            }
        }
        return EarningsSummary(earned, projected, earnedN, projectedN, reviewN)
    }

    /** Summary over bookings whose start date falls in [from]..[to] inclusive. */
    fun summarizeRange(
        bookings: List<Booking>,
        from: LocalDate,
        to: LocalDate,
        now: LocalDateTime,
    ): EarningsSummary =
        summarize(
            bookings.filter { !it.start.toLocalDate().isBefore(from) && !it.start.toLocalDate().isAfter(to) },
            now,
        )

    /** Earned money grouped by calendar day, ascending. */
    fun earnedByDay(bookings: List<Booking>): Map<LocalDate, Money> =
        bookings.asSequence()
            .filter { it.status.isEarned }
            .groupBy { it.start.toLocalDate() }
            .mapValues { (_, list) -> list.fold(Money.ZERO) { acc, b -> acc + b.rate } }
            .toSortedMap()

    /**
     * Earned money grouped by ISO week key "YYYY-Www". Uses ISO week fields so a
     * week is Monday-based and consistent regardless of device locale.
     */
    fun earnedByWeek(bookings: List<Booking>): Map<String, Money> {
        val weekFields = WeekFields.ISO
        return bookings.asSequence()
            .filter { it.status.isEarned }
            .groupBy {
                val d = it.start.toLocalDate()
                val week = d.get(weekFields.weekOfWeekBasedYear())
                val year = d.get(weekFields.weekBasedYear())
                String.format(Locale.US, "%04d-W%02d", year, week)
            }
            .mapValues { (_, list) -> list.fold(Money.ZERO) { acc, b -> acc + b.rate } }
            .toSortedMap()
    }
}
