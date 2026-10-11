package app.tasca

import app.tasca.data.Recurring
import app.tasca.data.TxnKind
import app.tasca.domain.Calc
import app.tasca.domain.Frequency
import app.tasca.io.LedgerSheets
import app.tasca.ui.screens.alreadyPosted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

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

    @Test fun monthlyOnThe31stDoesNotDrift() {
        val start = LocalDate.of(2026, 1, 31)
        val dates = (0..3).map { Frequency.MONTHLY.occurrence(start, 1, it) }
        assertEquals(
            listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)),
            dates,
        )
    }

    @Test fun scheduleEditNeverRepostsThePast() {
        val old = Recurring(
            name = "Rent", kind = TxnKind.EXPENSE, amountCents = 70000, accountId = 1, categoryId = 1,
            frequency = Frequency.MONTHLY, startDate = LocalDate.of(2026, 1, 1), posted = 10,
        )
        val moved = old.copy(startDate = LocalDate.of(2026, 1, 5))
        val n = alreadyPosted(old, moved)
        assertEquals(LocalDate.of(2026, 11, 5), moved.copy(posted = n).nextDate)
        assertEquals(0, alreadyPosted(null, moved))
        assertEquals(10, alreadyPosted(old, old))
    }

    @Test fun amountsParseInEitherLocale() {
        assertEquals(123456L, LedgerSheets.parseAmount("1.234,56"))
        assertEquals(123456L, LedgerSheets.parseAmount("1,234.56"))
        assertEquals(1250L, LedgerSheets.parseAmount("€ 12,5"))
        assertEquals(-700L, LedgerSheets.parseAmount("-7"))
    }
}
