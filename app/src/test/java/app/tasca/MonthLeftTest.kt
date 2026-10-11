package app.tasca

import app.tasca.data.Recurring
import app.tasca.data.TxnKind
import app.tasca.domain.Frequency
import app.tasca.domain.MonthLeft
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MonthLeftTest {
    private val today = LocalDate.of(2026, 10, 11)

    private fun payment(
        cents: Long, start: LocalDate, kind: TxnKind = TxnKind.EXPENSE, frequency: Frequency = Frequency.MONTHLY,
        posted: Int = 0, active: Boolean = true, end: LocalDate? = null,
    ) = Recurring(
        name = "p", kind = kind, amountCents = cents, accountId = 1,
        toAccountId = if (kind == TxnKind.TRANSFER) 2 else null, categoryId = if (kind == TxnKind.TRANSFER) null else 1,
        frequency = frequency, startDate = start, endDate = end, posted = posted, active = active,
    )

    private fun left(vararg r: Recurring) = MonthLeft.of(today, 200_000, 50_000, r.toList())

    @Test fun `rent due later this month counts once`() {
        val m = left(payment(80_000, LocalDate.of(2026, 1, 25), posted = 9))
        assertEquals(MonthLeft(70_000, 200_000, 50_000, 80_000, 0), m)
    }

    @Test fun `occurrence already posted this month is not counted again`() {
        assertEquals(0, left(payment(80_000, LocalDate.of(2026, 1, 5), posted = 10)).dueCents)
    }

    @Test fun `paused payment is ignored`() {
        assertEquals(0, left(payment(80_000, LocalDate.of(2026, 1, 25), posted = 9, active = false)).dueCents)
    }

    @Test fun `weekly payment counts each remaining week`() {
        // 4 Oct posted, then 11 (today, not yet posted), 18, 25.
        val m = left(payment(1_000, LocalDate.of(2026, 10, 4), frequency = Frequency.WEEKLY, posted = 1))
        assertEquals(3_000, m.dueCents)
    }

    @Test fun `end date stops it`() {
        val m = left(payment(1_000, LocalDate.of(2026, 10, 4), frequency = Frequency.WEEKLY, posted = 1, end = LocalDate.of(2026, 10, 20)))
        assertEquals(2_000, m.dueCents)
    }

    @Test fun `salary still due adds to what is left`() {
        val m = left(payment(150_000, LocalDate.of(2026, 1, 27), kind = TxnKind.INCOME, posted = 9))
        assertEquals(MonthLeft(300_000, 200_000, 50_000, 0, 150_000), m)
    }

    @Test fun `transfers are ignored`() {
        assertEquals(MonthLeft(150_000, 200_000, 50_000, 0, 0), left(payment(30_000, LocalDate.of(2026, 1, 20), kind = TxnKind.TRANSFER, posted = 9)))
    }

    @Test fun `payment starting next month is not due`() {
        assertEquals(0, left(payment(80_000, LocalDate.of(2026, 11, 1))).dueCents)
    }
}
