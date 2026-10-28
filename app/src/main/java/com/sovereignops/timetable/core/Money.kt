package com.sovereignops.timetable.core

/**
 * Money in whole cents. Rates in this workflow are whole-dollar figures
 * (e.g. 50 / 75 / 125), but storing cents keeps sums exact and lets a
 * non-round figure survive a round trip instead of being silently rounded.
 */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {

    operator fun plus(other: Money) = Money(cents + other.cents)

    operator fun times(n: Int) = Money(cents * n)

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    /** "$50", or "$50.50" when there is a cents remainder; "-$0.50" below zero. */
    fun format(): String {
        val sign = if (cents < 0) "-" else ""
        val abs = if (cents < 0) -cents else cents
        val rem = abs % 100
        return if (rem == 0L) "$sign$${abs / 100}" else "$sign$${abs / 100}.${rem.toString().padStart(2, '0')}"
    }

    companion object {
        val ZERO = Money(0)

        fun dollars(amount: Long) = Money(amount * 100)

        private val AMOUNT = Regex("\\d+(\\.\\d{1,2})?|\\.\\d{1,2}")

        /**
         * Parse "$50", "50", "50.50", "$1,200" into Money, and "-0.50", "-$5" or
         * "$-5" into a negative amount (callers that need a non-negative one
         * reject it). Returns null on anything that is not a clean amount rather
         * than guessing — an unparseable rate is a data problem the caller must
         * see, not absorb.
         */
        fun parse(raw: String): Money? {
            var text = raw.trim()
            var negative = text.startsWith("-")
            if (negative) text = text.substring(1).trimStart()
            text = text.removePrefix("$").trimStart()
            if (!negative && text.startsWith("-")) {
                negative = true
                text = text.substring(1).trimStart()
            }
            val cleaned = text.replace(",", "")
            if (!AMOUNT.matches(cleaned)) return null
            val whole = cleaned.substringBefore('.').ifEmpty { "0" }.toLongOrNull() ?: return null
            val frac = cleaned.substringAfter('.', "").padEnd(2, '0').toLong()
            val cents = whole * 100 + frac
            return Money(if (negative) -cents else cents)
        }
    }
}
