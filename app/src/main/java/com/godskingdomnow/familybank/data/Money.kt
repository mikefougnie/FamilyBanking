package com.godskingdomnow.familybank.data

import java.text.NumberFormat
import java.util.Locale

/** All money is kept in cents so it always adds up exactly. */
object Money {
    private val fmt: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)

    /** $1,234.50 (and -$3.00 for a negative amount). */
    fun format(cents: Long): String {
        val s = fmt.format(Math.abs(cents) / 100.0)
        return if (cents < 0) "-$s" else s
    }

    /** +$5.00 / −$5.00 for history rows. */
    fun signed(cents: Long): String = (if (cents < 0) "−" else "+") + format(Math.abs(cents))

    /** What someone typed ("12", "$12.5", "1,200.00") in cents, or null if it isn't a usable amount. */
    fun parseCents(text: String): Long? {
        val t = text.trim().removePrefix("$").replace(",", "").trim()
        if (!Regex("""^\d{0,7}(\.\d{0,2})?$""").matches(t) || t == "." || t.isEmpty()) return null
        val parts = t.split(".")
        val dollars = parts[0].ifEmpty { "0" }.toLong()
        val cents = if (parts.size > 1) parts[1].padEnd(2, '0').toLong() else 0L
        val total = dollars * 100 + cents
        return if (total in 1..100_000_000) total else null
    }
}
