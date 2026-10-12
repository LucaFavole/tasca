package app.tasca.domain

import app.tasca.data.Recurring
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class Frequency(val label: String, val unit: String, private val step: DateTimeUnit.DateBased) {
    DAILY("Daily", "day", DateTimeUnit.DAY),
    WEEKLY("Weekly", "week", DateTimeUnit.WEEK),
    MONTHLY("Monthly", "month", DateTimeUnit.MONTH),
    YEARLY("Yearly", "year", DateTimeUnit.YEAR);

    /** Occurrence n counted from start, so a payment on the 31st lands on the 30th/28th without drifting afterwards. */
    fun occurrence(start: LocalDate, every: Int, n: Int): LocalDate = start.plus(every.toLong() * n, step)

    fun describe(every: Int): String = if (every == 1) label else "Every $every ${unit}s"
}

/**
 * After a schedule edit, new occurrences before the old next due date count as already posted,
 * so moving rent from the 1st to the 5th never records the current month twice.
 * Resuming a paused payment also skips the occurrences missed while it was paused.
 */
fun alreadyPosted(original: Recurring?, updated: Recurring, today: LocalDate = today()): Int {
    if (original == null) return 0
    fun date(n: Int) = updated.frequency.occurrence(updated.startDate, updated.every, n)
    var n = 0
    if (original.posted > 0) while (date(n) < original.nextDate) n++
    if (!original.active && updated.active) while (date(n) < today) n++
    return n
}
