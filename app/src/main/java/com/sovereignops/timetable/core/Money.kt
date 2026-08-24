package com.sovereignops.timetable.core

/**
 * Money in whole cents. Rates in this workflow are whole-dollar figures
 * (QV 140 / half 200 / hour 300), but storing cents keeps sums exact and lets a
 * non-round figure survive a round trip instead of being silently rounded.
 */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {

    operator fun plus(other: Money) = Money(cents + other.cents)

    operator fun times(n: Int) = Money(cents * n)

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    /** "$140", or "$140.50" when there is a cents remainder. */
    fun format(): String {
        val whole = cents / 100
        val rem = (cents % 100).let { if (it < 0) -it else it }
        return if (rem == 0L) "$$whole" else "$$whole.${rem.toString().padStart(2, '0')}"
    }

    companion object {
        val ZERO = Money(0)

        fun dollars(amount: Long) = Money(amount * 100)

        /**
         * Parse "$140", "140", "140.50", "$1,200" into Money. Returns null on
         * anything that is not a clean amount rather than guessing — an
         * unparseable rate is a data problem the caller must see, not absorb.
         */
        fun parse(raw: String): Money? {
            val cleaned = raw.trim().removePrefix("$").replace(",", "").trim()
            if (cleaned.isEmpty()) return null
            val parts = cleaned.split(".")
            return when (parts.size) {
                1 -> parts[0].toLongOrNull()?.let { Money(it * 100) }
                2 -> {
                    val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
                    val fracRaw = parts[1]
                    if (fracRaw.length !in 1..2 || fracRaw.any { !it.isDigit() }) return null
                    val frac = fracRaw.padEnd(2, '0').toLong()
                    Money(whole * 100 + frac)
                }
                else -> null
            }
        }
    }
}
