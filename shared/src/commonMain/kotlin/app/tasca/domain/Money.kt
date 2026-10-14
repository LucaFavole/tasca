package app.tasca.domain

import kotlin.math.abs

object Money {
    /** ISO 4217 code of the app currency, e.g. "EUR". */
    var currency: String = "EUR"

    fun format(cents: Long, compact: Boolean = false): String {
        val whole = cents % 100 == 0L
        val decimals = !(compact && whole) && !(compact && abs(cents) >= 100_000)
        return formatAmount(cents, currency, if (decimals) 2 else 0)
    }

    /** "+7,00 €" or "−7,00 €" with a true minus sign, as the transaction rows show amounts. */
    fun signed(cents: Long): String = when {
        cents > 0 -> "+" + format(cents)
        cents < 0 -> "−" + format(-cents)
        else -> format(0)
    }

    fun fromDouble(value: Double): Long {
        val (negative, digits) = roundHalfUp(value, 2)
        return if (negative) -digits.toLong() else digits.toLong()
    }

    fun toDouble(cents: Long): Double = cents / 100.0

    fun plain(cents: Long): String {
        val digits = cents.toString().removePrefix("-").padStart(3, '0')
        val fraction = digits.takeLast(2).trimEnd('0')
        return (if (cents < 0) "-" else "") + digits.dropLast(2) + (if (fraction.isEmpty()) "" else ".$fraction")
    }
}

/** [cents] of [currency] in the Italian format with [fractionDigits] decimals, rounding half even: "1.234,56 €". */
internal expect fun formatAmount(cents: Long, currency: String, fractionDigits: Int): String
