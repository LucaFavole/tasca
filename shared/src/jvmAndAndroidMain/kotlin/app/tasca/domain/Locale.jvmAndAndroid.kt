package app.tasca.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaMonth
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale

internal actual fun formatAmount(cents: Long, currency: String, fractionDigits: Int): String =
    NumberFormat.getCurrencyInstance(Locale.ITALY).apply {
        this.currency = Currency.getInstance(currency)
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }.format(BigDecimal.valueOf(cents, 2))

internal actual fun isoCurrency(code: String): String? = runCatching { Currency.getInstance(code.uppercase()).currencyCode }.getOrNull()

internal actual fun monthName(month: Month): String = month.toJavaMonth().getDisplayName(TextStyle.FULL, Locale.getDefault())

private val SHORT = DateTimeFormatter.ofPattern("d MMM")

internal actual fun dayAndMonth(date: LocalDate): String = date.toJavaLocalDate().format(SHORT)
