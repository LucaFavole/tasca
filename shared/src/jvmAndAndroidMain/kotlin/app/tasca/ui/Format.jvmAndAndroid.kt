package app.tasca.ui

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter

internal actual fun formatDate(time: LocalDateTime, pattern: String): String =
    DateTimeFormatter.ofPattern(pattern).format(time.toJavaLocalDateTime())

internal actual fun formatQuantity(value: Double): String = DecimalFormat("#,##0.####").format(value)

internal actual fun formatTwoDecimals(value: Double): String = "%.2f".format(value)

internal actual fun plainDecimal(value: Double, maxDecimals: Int): String =
    BigDecimal(value).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')
