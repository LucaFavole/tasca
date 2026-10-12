package app.tasca

import app.tasca.data.Recurring
import app.tasca.data.TxnKind
import app.tasca.domain.Frequency
import app.tasca.domain.MonthLeft
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlinx.datetime.LocalDate

class MonthLeftTest {
    private val today = LocalDate(2026, 10, 11)

    private fun payment(
        cents: Long, start: LocalDate, kind: TxnKind = TxnKind.EXPENSE, frequency: Frequency = Frequency.MONTHLY,
        posted: Int = 0, active: Boolean = true, end: LocalDate? = null, from: Long = 1, to: Long = 2,
    ) = Recurring(
        name = "p", kind = kind, amountCents = cents, accountId = from,
        toAccountId = if (kind == TxnKind.TRANSFER) to else null, categoryId = if (kind == TxnKind.TRANSFER) null else 1,
        frequency = frequency, startDate = start, endDate = end, posted = posted, active = active,
    )

    private fun left(vararg r: Recurring) = MonthLeft.of(today, 1, 150_000, r.toList())

    @Test fun `rent due later this month comes off the balance once`() {
        assertEquals(MonthLeft(70_000, 150_000, 80_000, 0), left(payment(80_000, LocalDate(2026, 1, 25), posted = 9)))
    }

    @Test fun `occurrence already posted this month is not counted again`() {
        assertEquals(0, left(payment(80_000, LocalDate(2026, 1, 5), posted = 10)).dueCents)
    }

    @Test fun `missed occurrence from last month is still due`() {
        assertEquals(160_000, left(payment(80_000, LocalDate(2026, 1, 25), posted = 8)).dueCents)
    }

    @Test fun `paused payment is ignored`() {
        assertEquals(0, left(payment(80_000, LocalDate(2026, 1, 25), posted = 9, active = false)).dueCents)
    }

    @Test fun `weekly payment counts each remaining week`() {
        // 4 Oct posted, then 11 (today, not yet posted), 18, 25.
        assertEquals(3_000, left(payment(1_000, LocalDate(2026, 10, 4), frequency = Frequency.WEEKLY, posted = 1)).dueCents)
    }

    @Test fun `end date stops it`() {
        val m = left(payment(1_000, LocalDate(2026, 10, 4), frequency = Frequency.WEEKLY, posted = 1, end = LocalDate(2026, 10, 20)))
        assertEquals(2_000, m.dueCents)
    }

    @Test fun `salary still due adds to what is left`() {
        val m = left(payment(150_000, LocalDate(2026, 1, 27), kind = TxnKind.INCOME, posted = 9))
        assertEquals(MonthLeft(300_000, 150_000, 0, 150_000), m)
    }

    @Test fun `transfers count by direction`() {
        val out = payment(30_000, LocalDate(2026, 1, 20), kind = TxnKind.TRANSFER, posted = 9)
        val inn = payment(10_000, LocalDate(2026, 1, 20), kind = TxnKind.TRANSFER, posted = 9, from = 2, to = 1)
        assertEquals(MonthLeft(130_000, 150_000, 30_000, 10_000), left(out, inn))
    }

    @Test fun `payments of other accounts are ignored`() {
        assertEquals(MonthLeft(150_000, 150_000, 0, 0), left(payment(80_000, LocalDate(2026, 1, 25), posted = 9, from = 3)))
    }

    @Test fun `payment starting next month is not due`() {
        assertEquals(0, left(payment(80_000, LocalDate(2026, 11, 1))).dueCents)
    }
}
