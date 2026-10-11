package app.tasca.domain

import java.time.LocalDate

enum class Frequency(val label: String, val unit: String) {
    DAILY("Daily", "day"), WEEKLY("Weekly", "week"), MONTHLY("Monthly", "month"), YEARLY("Yearly", "year");

    /** Occurrence n counted from start, so a payment on the 31st lands on the 30th/28th without drifting afterwards. */
    fun occurrence(start: LocalDate, every: Int, n: Int): LocalDate {
        val steps = every.toLong() * n
        return when (this) {
            DAILY -> start.plusDays(steps)
            WEEKLY -> start.plusWeeks(steps)
            MONTHLY -> start.plusMonths(steps)
            YEARLY -> start.plusYears(steps)
        }
    }

    fun describe(every: Int): String = if (every == 1) label else "Every $every ${unit}s"
}
