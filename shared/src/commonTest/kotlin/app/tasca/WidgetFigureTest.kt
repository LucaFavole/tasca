package app.tasca

import app.tasca.data.Account
import app.tasca.data.Holding
import app.tasca.data.Quote
import app.tasca.data.WidgetFigure
import app.tasca.data.figureOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WidgetFigureTest {
    private val ledger = inMemoryLedger()

    @AfterTest fun close() = ledger.close()

    @Test fun figuresCountDeletedAccountsInTheTotalAndAddTheInvestmentGain() = runTest {
        val main = ledger.saveAccount(Account(name = "Main", color = 0, initialCents = 10_000))
        val closed = ledger.saveAccount(Account(name = "Old", color = 0, initialCents = 2_500, deleted = true))
        ledger.dao.upsertHolding(Holding(accountId = main, symbol = "VWRA.L", name = "All-World", quantity = 10.0, investedCents = 150_000))
        ledger.dao.upsertQuotes(listOf(Quote("VWRA.L", 193.5, 192.5, "USD", 0.9, 0)))
        val accounts = ledger.dao.balances().first()

        assertEquals("Main" to 10_000L, ledger.figureOf(WidgetFigure.Account(main), accounts))
        assertNull(ledger.figureOf(WidgetFigure.Account(closed), accounts), "a deleted account is not shown")
        assertEquals("Total" to 12_500L, ledger.figureOf(WidgetFigure.Total, accounts))
        assertEquals("Net worth" to 12_500L + 24_150, ledger.figureOf(WidgetFigure.NetWorth, accounts))
    }

    @Test fun storedCodesRoundTrip() {
        for (f in listOf(WidgetFigure.Account(7), WidgetFigure.Total, WidgetFigure.NetWorth)) assertEquals(f, WidgetFigure.decode(f.encode()))
        assertEquals(listOf(-10L, -11L), listOf(WidgetFigure.Total.encode(), WidgetFigure.NetWorth.encode()))
        assertNull(WidgetFigure.decode(0), "no widget choice stored")
    }
}
