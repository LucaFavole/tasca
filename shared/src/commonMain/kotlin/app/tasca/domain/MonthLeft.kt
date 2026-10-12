package app.tasca.domain

import app.tasca.data.Recurring
import app.tasca.data.TxnKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

/** What will be left in one account at the end of the month: its balance now, plus money still due in, minus money still due out. */
data class MonthLeft(val leftCents: Long, val balanceCents: Long, val dueCents: Long, val dueIncomeCents: Long) {
    companion object {
        /** A daily payment fills a month in 31 steps; the cap only guards against a runaway loop. */
        private const val MAX_STEPS = 1000

        /** [balanceCents] is [accountId]'s balance today; [recurring] the regular payments, paused ones included. */
        fun of(today: LocalDate, accountId: Long, balanceCents: Long, recurring: List<Recurring>): MonthLeft {
            val end = today.yearMonth.lastDay
            var due = 0L
            var dueIncome = 0L
            for (r in recurring) {
                val incoming = when {
                    !r.active -> continue
                    r.kind == TxnKind.TRANSFER && r.toAccountId == accountId -> true
                    r.accountId != accountId -> continue
                    else -> r.kind == TxnKind.INCOME
                }
                val upcoming = (r.posted until r.posted + MAX_STEPS).asSequence()
                    .map { r.frequency.occurrence(r.startDate, r.every, it) }
                    .takeWhile { it <= end && (r.endDate == null || it <= r.endDate) }
                    .count()
                if (incoming) dueIncome += upcoming * r.amountCents else due += upcoming * r.amountCents
            }
            return MonthLeft(balanceCents + dueIncome - due, balanceCents, due, dueIncome)
        }
    }
}
