package app.tasca.ui

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfEven
import platform.Foundation.NSNumberFormatterRoundHalfUp
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneForSecondsFromGMT

// To verify on a Mac: the four outputs match Android's for the same values in Italian.

internal actual fun formatDate(time: LocalDateTime, pattern: String): String {
    val formatter = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
        dateFormat = pattern
    }
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(time.toInstant(TimeZone.UTC).epochSeconds.toDouble()))
}

internal actual fun formatQuantity(value: Double): String = number(value, 0, 4, grouping = true, NSNumberFormatterRoundHalfEven, NSLocale.currentLocale)

internal actual fun formatTwoDecimals(value: Double): String = number(value, 2, 2, grouping = false, NSNumberFormatterRoundHalfUp, NSLocale.currentLocale)

internal actual fun plainDecimal(value: Double, maxDecimals: Int): String =
    number(value, 0, maxDecimals, grouping = false, NSNumberFormatterRoundHalfUp, NSLocale(localeIdentifier = "en_US_POSIX")).replace('.', ',')

private fun number(value: Double, minDecimals: Int, maxDecimals: Int, grouping: Boolean, rounding: ULong, locale: NSLocale): String {
    val formatter = NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterDecimalStyle
        this.locale = locale
        minimumFractionDigits = minDecimals.toULong()
        maximumFractionDigits = maxDecimals.toULong()
        usesGroupingSeparator = grouping
        roundingMode = rounding
    }
    return formatter.stringFromNumber(NSNumber(double = value)) ?: value.toString()
}
