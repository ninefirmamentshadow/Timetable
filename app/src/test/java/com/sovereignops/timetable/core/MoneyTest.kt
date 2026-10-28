package com.sovereignops.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test fun dollarsAreCents() {
        assertEquals(5000L, Money.dollars(50).cents)
    }

    @Test fun formatsWholeDollars() {
        assertEquals("$50", Money.dollars(50).format())
    }

    @Test fun formatsCentsRemainder() {
        assertEquals("$50.50", Money(5050).format())
        assertEquals("$0.05", Money(5).format())
    }

    @Test fun addsAndMultiplies() {
        assertEquals(Money.dollars(125), Money.dollars(50) + Money.dollars(75))
        assertEquals(Money.dollars(75), Money.dollars(25) * 3)
    }

    @Test fun parsesPlainAndPrefixed() {
        assertEquals(Money.dollars(50), Money.parse("50"))
        assertEquals(Money.dollars(50), Money.parse("$50"))
        assertEquals(Money.dollars(50), Money.parse(" $50 "))
    }

    @Test fun parsesDecimalsAndThousands() {
        assertEquals(Money(5050), Money.parse("50.50"))
        assertEquals(Money(5005), Money.parse("50.05"))
        assertEquals(Money(5050), Money.parse("50.5")) // one decimal → tens
        assertEquals(Money.dollars(1200), Money.parse("$1,200"))
    }

    @Test fun rejectsGarbageInsteadOfGuessing() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("free"))
        assertNull(Money.parse("50.505"))
        assertNull(Money.parse("1.2.3"))
        assertNull(Money.parse("$"))
    }

    @Test fun aMinusSignIsKeptInsteadOfDropped() {
        // "-0.50" used to parse as +$0.50, so a negative amount passed the "not negative" checks.
        assertEquals(Money(-50), Money.parse("-0.50"))
        assertEquals(Money(-50), Money.parse("-$0.50"))
        assertEquals(Money(-50), Money.parse("$-0.50"))
        assertEquals(Money(-500), Money.parse("-5"))
        assertEquals(Money(-5050), Money.parse("-50.50"))
        assertNull(Money.parse("--5"))
        assertNull(Money.parse("-"))
        assertNull(Money.parse("+5"))
        assertNull(Money.parse("5-"))
        assertNull(Money.parse("1.-5"))
    }

    @Test fun aNegativeAmountFormatsWithItsSign() {
        assertEquals("-$0.50", Money(-50).format())
        assertEquals("-$1.50", Money(-150).format())
        assertEquals("-$5", Money(-500).format())
    }

    @Test fun ordersByValue() {
        assertEquals(Money.dollars(50), minOf(Money.dollars(125), Money.dollars(50)))
    }
}
