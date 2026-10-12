package app.tasca

import app.tasca.data.Holding
import app.tasca.data.Portfolio
import app.tasca.data.Quote
import app.tasca.domain.Trade
import app.tasca.domain.TradeSide
import app.tasca.io.SymbolHit
import app.tasca.io.Yahoo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SEARCH_JSON = """{"explains":[],"count":2,"quotes":[{"exchange":"LSE","shortname":"VANGUARD FUNDS PLC VANGUARD FTS","quoteType":"ETF","symbol":"VWRA.L","index":"quotes","score":20984.0,"typeDisp":"ETF","longname":"Vanguard FTSE All-World UCITS ETF USD Accumulation","exchDisp":"London","isYahooFinance":true},{"exchange":"STU","shortname":"Vanguard FTSE All-World UCITS E","quoteType":"MUTUALFUND","symbol":"IE00BK5BQT80.SG","index":"quotes","score":20026.0,"typeDisp":"Fund","exchDisp":"Stuttgart","isYahooFinance":true}],"news":[],"nav":[],"lists":[],"researchReports":[],"screenerFieldResults":[],"totalTime":17,"timeTakenForQuotes":411,"timeTakenForNews":0,"timeTakenForAlgowatchlist":400,"timeTakenForPredefinedScreener":400,"timeTakenForCrunchbase":0,"timeTakenForNav":400,"timeTakenForResearchReports":0,"timeTakenForQuestions":0,"timeTakenForScreenerField":0,"timeTakenForCulturalAssets":0,"timeTakenForSearchLists":0}"""

private const val CHART_USD_JSON = """{"chart":{"result":[{"meta":{"currency":"USD","symbol":"VWRA.L","exchangeName":"LSE","fullExchangeName":"LSE","instrumentType":"ETF","firstTradeDate":1563865200,"regularMarketTime":1791564292,"hasPrePostMarketData":false,"gmtoffset":3600,"timezone":"BST","exchangeTimezoneName":"Europe/London","regularMarketPrice":193.5,"regularMarketChangePercent":0.519,"fulldayPrice":193.5,"fulldayChange":1.0,"fulldayChangePercent":0.519,"fiftyTwoWeekHigh":202.3,"fiftyTwoWeekLow":140.22,"regularMarketDayHigh":193.84,"regularMarketDayLow":193.22,"regularMarketVolume":84288,"longName":"Vanguard FTSE All-World UCITS ETF USD Accumulation","shortName":"VANGUARD FUNDS PLC VANGUARD FTS","chartPreviousClose":192.5,"priceHint":2,"currentTradingPeriod":{"pre":{"timezone":"BST","end":1791529200,"start":1791526500,"gmtoffset":3600},"regular":{"timezone":"BST","end":1791559800,"start":1791529200,"gmtoffset":3600},"post":{"timezone":"BST","end":1791562500,"start":1791559800,"gmtoffset":3600}},"dataGranularity":"1d","range":"1d","validRanges":["1d","5d","1mo","3mo","6mo","1y","2y","5y","10y","ytd","max"]},"timestamp":[1791529200],"indicators":{"quote":[{"low":[193.22000122070312],"high":[193.83999633789062],"open":[193.4600067138672],"volume":[95772],"close":[193.5]}],"adjclose":[{"adjclose":[193.5]}]}}],"error":null}}"""

class InvestmentsTest {
    @Test fun isinSearchFindsTheListings() {
        val hits = Yahoo.parseSearch(SEARCH_JSON)
        assertTrue(hits.any { it.symbol == "VWRA.L" && it.name.contains("All-World") && it.exchange == "London" })
    }

    @Test fun searchFallsBackToShortNamesAndSkipsOtherSources() {
        val hits = Yahoo.parseSearch(
            """{"quotes":[{"symbol":"A","shortname":"Short A","exchange":"MIL"},{"symbol":"B","isYahooFinance":false},""" +
                """{"symbol":"C","isYahooFinance":"FALSE"},{"name":"no symbol"},{"symbol":"D","longname":null}]}""",
        )
        assertEquals(listOf(SymbolHit("A", "Short A", "MIL", ""), SymbolHit("D", "null", "", "")), hits)
        assertEquals(emptyList(), Yahoo.parseSearch("""{"count":0}"""))
    }

    @Test fun chartGivesPriceAndCurrency() {
        val p = Yahoo.parseChart(CHART_USD_JSON)
        assertEquals("USD", p.currency)
        assertEquals(193.5, p.price, 1e-9)
        assertEquals(192.5, p.previousClose!!, 1e-9)
    }

    @Test fun chartWithoutCurrencyOrPreviousCloseIsInEuro() {
        val p = Yahoo.parseChart("""{"chart":{"result":[{"meta":{"symbol":"X.MI","regularMarketPrice":"12.5"}}]}}""")
        assertEquals("EUR", p.currency)
        assertEquals(12.5, p.price, 0.0)
        assertNull(p.previousClose)
    }

    @Test fun pencePricesBecomePounds() {
        val json = """{"chart":{"result":[{"meta":{"symbol":"X.L","currency":"GBp","regularMarketPrice":1234.0}}]}}"""
        val p = Yahoo.parseChart(json)
        assertEquals("GBP", p.currency)
        assertEquals(12.34, p.price, 1e-9)
    }

    @Test fun unknownSymbolFails() {
        val e = assertFailsWith<IllegalStateException> {
            Yahoo.parseChart("""{"chart":{"result":null,"error":{"code":"Not Found","description":"No data found"}}}""")
        }
        assertEquals("No data found", e.message)
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

    @Test fun cannotSellMoreThanHeld() {
        val e = assertFailsWith<IllegalArgumentException> { Trade.apply(TradeSide.SELL, 1.5, 100, 2.0, 1.0) }
        assertEquals("You only have 1,5000 shares", e.message)
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
