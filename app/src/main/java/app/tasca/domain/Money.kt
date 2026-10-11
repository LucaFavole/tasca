package app.tasca.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
    var currency: Currency = Currency.getInstance("EUR")

    private fun formatter(decimals: Boolean): NumberFormat =
        NumberFormat.getCurrencyInstance(Locale.ITALY).apply {
            currency = this@Money.currency
            minimumFractionDigits = if (decimals) 2 else 0
            maximumFractionDigits = if (decimals) 2 else 0
        }

    fun format(cents: Long, compact: Boolean = false): String {
        val whole = cents % 100 == 0L
        return formatter(!(compact && whole) && !(compact && kotlin.math.abs(cents) >= 100_000))
            .format(BigDecimal.valueOf(cents, 2))
    }

    fun signed(cents: Long): String = (if (cents > 0) "+" else "") + format(cents)

    fun fromDouble(value: Double): Long =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).unscaledValue().toLong()

    fun toDouble(cents: Long): Double = BigDecimal.valueOf(cents, 2).toDouble()

    fun plain(cents: Long): String = BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString()
}
