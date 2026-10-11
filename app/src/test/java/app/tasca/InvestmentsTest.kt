package app.tasca

import app.tasca.data.Holding
import app.tasca.data.Portfolio
import app.tasca.data.Quote
import app.tasca.domain.Trade
import app.tasca.domain.TradeSide
import app.tasca.io.SymbolHit
import app.tasca.io.Yahoo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentsTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun isinSearchFindsTheListings() {
        val hits = Yahoo.parseSearch(fixture("yahoo_search.json"))
        assertTrue(hits.any { it.symbol == "VWRA.L" && it.name.contains("All-World") && it.exchange == "London" })
    }

    @Test fun chartGivesPriceAndCurrency() {
        val p = Yahoo.parseChart(fixture("yahoo_chart_usd.json"))
        assertEquals("USD", p.currency)
        assertEquals(193.5, p.price, 1e-9)
        assertEquals(192.5, p.previousClose!!, 1e-9)
    }

    @Test fun pencePricesBecomePounds() {
        val json = """{"chart":{"result":[{"meta":{"symbol":"X.L","currency":"GBp","regularMarketPrice":1234.0}}]}}"""
        val p = Yahoo.parseChart(json)
        assertEquals("GBP", p.currency)
        assertEquals(12.34, p.price, 1e-9)
    }

    @Test(expected = IllegalStateException::class) fun unknownSymbolFails() {
        Yahoo.parseChart("""{"chart":{"result":null,"error":{"code":"Not Found","description":"No data found"}}}""")
    }

    @Test fun gainIsValueInAppCurrencyMinusCost() {
        val holdings = listOf(
            Holding(1, 1, "VWRA.L", "All-World", 10.0, 150_000),
            Holding(2, 1, "NEW", "No price yet", 1.0, 5_000),
        )
        val quotes = mapOf("VWRA.L" to Quote("VWRA.L", 193.5, 192.5, "USD", 0.9, 0))
        val p = Portfolio.of(holdings, quotes)
        assertEquals(174_150 + 5_000, p.valueCents)
        assertEquals(155_000, p.investedCents)
        assertEquals(24_150, p.gainCents)
        assertEquals(1, p.unpriced)
    }

    @Test fun buyAddsSharesAndCost() {
        val p = Trade.apply(TradeSide.BUY, 10.0, 150_000, 2.5, 160.0)
        assertEquals(12.5, p.quantity, 1e-9)
        assertEquals(190_000, p.investedCents)
        assertEquals(0, p.realizedCents)
    }

    @Test fun partialSaleRealisesAgainstAverageCost() {
        val p = Trade.apply(TradeSide.SELL, 10.0, 150_000, 4.0, 180.0)
        assertEquals(6.0, p.quantity, 1e-9)
        assertEquals(90_000, p.investedCents)
        assertEquals(72_000 - 60_000, p.realizedCents)
    }

    @Test fun sellingEverythingLeavesNoDust() {
        val p = Trade.apply(TradeSide.SELL, 41.678225, 1_106_640, 41.678225, 250.0)
        assertEquals(0.0, p.quantity, 0.0)
        assertEquals(0, p.investedCents)
        assertEquals(Trade.cents(41.678225, 250.0) - 1_106_640, p.realizedCents)
    }

    @Test(expected = IllegalArgumentException::class) fun cannotSellMoreThanHeld() {
        Trade.apply(TradeSide.SELL, 1.0, 100, 2.0, 1.0)
    }

    @Test fun euroListingsComeFirstAndINavIsDropped() {
        val ranked = Yahoo.rank(listOf(
            SymbolHit("ACWD.L", "SPDR ACWI", "London", "ETF"),
            SymbolHit("DE000SL0H6U1.SG", "HSBC EM EUR iNAV", "Stuttgart", ""),
            SymbolHit("SPYY.DE", "SPDR ACWI", "XETRA", "ETF"),
            SymbolHit("ACWD.L", "SPDR ACWI", "London", "ETF"),
        ))
        assertEquals(listOf("SPYY.DE", "ACWD.L"), ranked.map { it.symbol })
    }
}
