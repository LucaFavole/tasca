package app.tasca

import app.tasca.data.Recurring
import app.tasca.data.TxnKind
import app.tasca.domain.Calc
import app.tasca.domain.Frequency
import app.tasca.domain.alreadyPosted
import app.tasca.io.LedgerSheets
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DomainTest {
    @Test fun calculatorRespectsPrecedenceAndCommas() {
        assertEquals(1850L, Calc.evaluate("12,50+3×2"))
        assertEquals(333L, Calc.evaluate("10÷3"))
        assertEquals(-500L, Calc.evaluate("5-10"))
        assertEquals(705L, Calc.evaluate("7,05"))
    }

    @Test fun calculatorRejectsIncompleteInput() {
        assertNull(Calc.evaluate(""))
        assertNull(Calc.evaluate("5+"))
        assertNull(Calc.evaluate("5÷0"))
        assertNull(Calc.evaluate("1,2,3"))
    }

    @Test fun calculatorRoundsTheExactValue() {
        assertEquals(20_123_450L, Calc.evaluate("9503,4÷348×7368,9"))
        assertEquals(1L, Calc.evaluate("1÷3×0,015"))
        assertEquals(-1L, Calc.evaluate("0-1÷3×0,015"))
    }

    @Test fun calculatorGivesNoAmountRatherThanAWrongOne() {
        assertEquals(100_000_000_000_000_000L, Calc.evaluate("1000000000×1000000"))
        assertNull(Calc.evaluate("43507,07×71493,447×13434,53"))
        assertNull(Calc.evaluate("99999999999999999999"))
    }

    @Test fun monthlyOnThe31stDoesNotDrift() {
        val start = LocalDate(2026, 1, 31)
        val dates = (0..3).map { Frequency.MONTHLY.occurrence(start, 1, it) }
        assertEquals(
            listOf(LocalDate(2026, 1, 31), LocalDate(2026, 2, 28), LocalDate(2026, 3, 31), LocalDate(2026, 4, 30)),
            dates,
        )
    }

    @Test fun scheduleEditNeverRepostsThePast() {
        val old = Recurring(
            name = "Rent", kind = TxnKind.EXPENSE, amountCents = 70000, accountId = 1, categoryId = 1,
            frequency = Frequency.MONTHLY, startDate = LocalDate(2026, 1, 1), posted = 10,
        )
        val moved = old.copy(startDate = LocalDate(2026, 1, 5))
        val n = alreadyPosted(old, moved)
        assertEquals(LocalDate(2026, 11, 5), moved.copy(posted = n).nextDate)
        assertEquals(0, alreadyPosted(null, moved))
        assertEquals(10, alreadyPosted(old, old))
    }

    @Test fun resumingSkipsWhatWasMissedWhilePaused() {
        val paused = Recurring(
            name = "Gym", kind = TxnKind.EXPENSE, amountCents = 3000, accountId = 1, categoryId = 1,
            frequency = Frequency.MONTHLY, startDate = LocalDate(2026, 1, 10), posted = 3, active = false,
        )
        val resumed = paused.copy(active = true)
        assertEquals(LocalDate(2026, 10, 10), resumed.copy(posted = alreadyPosted(paused, resumed, LocalDate(2026, 10, 10))).nextDate)
        assertEquals(LocalDate(2026, 11, 10), resumed.copy(posted = alreadyPosted(paused, resumed, LocalDate(2026, 10, 11))).nextDate)
        assertEquals(3, alreadyPosted(paused, paused.copy(amountCents = 3500), LocalDate(2026, 10, 11)))
    }

    @Test fun amountsParseInEitherLocale() {
        assertEquals(123456L, LedgerSheets.parseAmount("1.234,56"))
        assertEquals(123456L, LedgerSheets.parseAmount("1,234.56"))
        assertEquals(1250L, LedgerSheets.parseAmount("€ 12,5"))
        assertEquals(-700L, LedgerSheets.parseAmount("-7"))
    }
}
