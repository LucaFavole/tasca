package app.tasca.domain

import app.tasca.data.Recurring
import app.tasca.data.TxnKind
import java.time.LocalDate
import java.time.YearMonth

/** What will be left at the end of the month: money in so far and still due, minus money out so far and still due. */
data class MonthLeft(val leftCents: Long, val incomeCents: Long, val spentCents: Long, val dueCents: Long, val dueIncomeCents: Long) {
    companion object {
        /** A daily payment fills a month in 31 steps; the cap only guards against a runaway loop. */
        private const val MAX_STEPS = 1000

        /** [incomeCents] and [spentCents] are this month's totals so far; [recurring] the regular payments, paused ones included. */
        fun of(today: LocalDate, incomeCents: Long, spentCents: Long, recurring: List<Recurring>): MonthLeft {
            val month = YearMonth.from(today)
            var due = 0L
            var dueIncome = 0L
            for (r in recurring) {
                if (!r.active || r.kind == TxnKind.TRANSFER) continue
                val upcoming = (r.posted until r.posted + MAX_STEPS).asSequence()
                    .map { r.frequency.occurrence(r.startDate, r.every, it) }
                    .takeWhile { !it.isAfter(month.atEndOfMonth()) && (r.endDate == null || !it.isAfter(r.endDate)) }
                    // An occurrence left over from an earlier month is posted with its own date, so it doesn't touch this month.
                    .count { !it.isBefore(month.atDay(1)) }
                if (r.kind == TxnKind.INCOME) dueIncome += upcoming * r.amountCents else due += upcoming * r.amountCents
            }
            return MonthLeft(incomeCents + dueIncome - spentCents - due, incomeCents, spentCents, due, dueIncome)
        }
    }
}
