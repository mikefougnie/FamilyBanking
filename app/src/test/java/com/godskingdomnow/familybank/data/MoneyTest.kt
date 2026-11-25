package com.godskingdomnow.familybank.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun parsesWhatPeopleType() {
        assertEquals(1200L, Money.parseCents("12"))
        assertEquals(1250L, Money.parseCents("$12.5"))
        assertEquals(120000L, Money.parseCents("1,200.00"))
        assertEquals(5L, Money.parseCents(".05"))
        assertEquals(99L, Money.parseCents(" 0.99 "))
    }

    @Test fun refusesNonsense() {
        assertNull(Money.parseCents(""))
        assertNull(Money.parseCents("."))
        assertNull(Money.parseCents("0"))
        assertNull(Money.parseCents("-5"))
        assertNull(Money.parseCents("1.234"))
        assertNull(Money.parseCents("abc"))
        assertNull(Money.parseCents("99999999"))
    }

    @Test fun formats() {
        assertEquals("$1,234.50", Money.format(123450))
        assertEquals("-$3.00", Money.format(-300))
        assertEquals("+$0.05", Money.signed(5))
        assertEquals("−$2.00", Money.signed(-200))
    }
}
