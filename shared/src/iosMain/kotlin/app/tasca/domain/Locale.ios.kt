package app.tasca.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSLocale
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSNumberFormatterRoundHalfEven
import platform.Foundation.NSTimeZone
import platform.Foundation.ISOCurrencyCodes
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneForSecondsFromGMT

internal actual fun formatAmount(cents: Long, currency: String, fractionDigits: Int): String {
    val formatter = NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterCurrencyStyle
        locale = NSLocale(localeIdentifier = "it_IT")
        currencyCode = currency
        minimumFractionDigits = fractionDigits.toULong()
        maximumFractionDigits = fractionDigits.toULong()
        roundingMode = NSNumberFormatterRoundHalfEven
    }
    val magnitude = if (cents < 0) (-cents).toULong() else cents.toULong()
    val amount = NSDecimalNumber(mantissa = magnitude, exponent = -2, isNegative = cents < 0)
    return formatter.stringFromNumber(amount) ?: error("Cannot format $cents $currency")
}

// To verify on a Mac: Settings accepts "usd" as USD and refuses "XYZ", as on Android.
internal actual fun isoCurrency(code: String): String? = code.uppercase().takeIf { it in NSLocale.ISOCurrencyCodes }

internal actual fun monthName(month: Month): String =
    NSDateFormatter().apply { locale = NSLocale.currentLocale }.monthSymbols[month.ordinal] as String

internal actual fun dayAndMonth(date: LocalDate): String {
    val formatter = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
        dateFormat = "d MMM"
    }
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(date.toEpochDays() * 86_400.0))
}
