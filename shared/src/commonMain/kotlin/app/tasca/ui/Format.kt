package app.tasca.ui

import kotlinx.datetime.LocalDateTime

/** [time] written with a date pattern such as "EEE d MMM", in the phone's language. */
internal expect fun formatDate(time: LocalDateTime, pattern: String): String

/** A number of shares with grouping and up to four decimals, in the phone's language: "1.234,5679". */
internal expect fun formatQuantity(value: Double): String

/** Two decimals, rounded half up, in the phone's language: "12,35". */
internal expect fun formatTwoDecimals(value: Double): String

/** [value] rounded half up to at most [maxDecimals] decimals, without trailing zeros, with a decimal comma: "12,345678". */
internal expect fun plainDecimal(value: Double, maxDecimals: Int = 6): String
