package com.sovereignops.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test fun dollarsAreCents() {
        assertEquals(14000L, Money.dollars(140).cents)
    }

    @Test fun formatsWholeDollars() {
        assertEquals("$140", Money.dollars(140).format())
    }

    @Test fun formatsCentsRemainder() {
        assertEquals("$140.50", Money(14050).format())
        assertEquals("$0.05", Money(5).format())
    }

    @Test fun addsAndMultiplies() {
        assertEquals(Money.dollars(300), Money.dollars(140) + Money.dollars(160))
        assertEquals(Money.dollars(420), Money.dollars(140) * 3)
    }

    @Test fun parsesPlainAndPrefixed() {
        assertEquals(Money.dollars(140), Money.parse("140"))
        assertEquals(Money.dollars(140), Money.parse("$140"))
        assertEquals(Money.dollars(140), Money.parse(" $140 "))
    }

    @Test fun parsesDecimalsAndThousands() {
        assertEquals(Money(14050), Money.parse("140.50"))
        assertEquals(Money(14005), Money.parse("140.05"))
        assertEquals(Money(14050), Money.parse("140.5")) // one decimal → tens
        assertEquals(Money.dollars(1200), Money.parse("$1,200"))
    }

    @Test fun rejectsGarbageInsteadOfGuessing() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("free"))
        assertNull(Money.parse("140.505"))
        assertNull(Money.parse("1.2.3"))
        assertNull(Money.parse("$"))
    }

    @Test fun ordersByValue() {
        assertEquals(Money.dollars(140), minOf(Money.dollars(300), Money.dollars(140)))
    }
}
