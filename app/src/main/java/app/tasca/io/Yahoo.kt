package app.tasca.io

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SymbolHit(val symbol: String, val name: String, val exchange: String, val type: String)

/** A price in the instrument's own currency, already corrected for quotes in pence. */
data class Price(val symbol: String, val price: Double, val previousClose: Double?, val currency: String)

/** Yahoo Finance's public endpoints: symbol search (by ISIN, ticker or name) and last price. */
object Yahoo {
    private const val SEARCH = "https://query2.finance.yahoo.com/v1/finance/search?quotesCount=10&newsCount=0&q="
    private const val CHART = "https://query1.finance.yahoo.com/v8/finance/chart/%s?range=1d&interval=1d"

    private val ISIN = Regex("^[A-Z]{2}[A-Z0-9]{9}[0-9]$")

    /**
     * An ISIN search often returns only a foreign listing, so the fund's name is searched too and the
     * listings are merged with euro exchanges first.
     */
    fun search(query: String): List<SymbolHit> {
        val q = query.trim()
        val direct = parseSearch(get(SEARCH + enc(q)))
        if (!ISIN.matches(q.uppercase()) || direct.isEmpty()) return direct
        val byName = runCatching { parseSearch(get(SEARCH + enc(direct.first().name))) }.getOrDefault(emptyList())
        return rank(direct + byName)
    }

    private val exchangeOrder = listOf("XETRA", "Milan", "Paris", "Amsterdam", "Brussels", "Vienna", "Swiss", "London")

    fun rank(hits: List<SymbolHit>): List<SymbolHit> = hits
        .distinctBy { it.symbol }
        .filterNot { it.name.contains("iNAV", ignoreCase = true) }
        .sortedBy { h -> exchangeOrder.indexOf(h.exchange).let { if (it < 0) exchangeOrder.size else it } }

    fun price(symbol: String): Price = parseChart(get(CHART.format(enc(symbol))))

    /** How many units of [to] one unit of [from] is worth. */
    fun rate(from: String, to: String): Double = if (from == to) 1.0 else price("$from$to=X").price

    fun parseSearch(json: String): List<SymbolHit> {
        val quotes = JSONObject(json).optJSONArray("quotes") ?: return emptyList()
        return (0 until quotes.length()).map(quotes::getJSONObject)
            .filter { it.has("symbol") && it.optBoolean("isYahooFinance", true) }
            .map {
                SymbolHit(
                    symbol = it.getString("symbol"),
                    name = it.optString("longname").ifEmpty { it.optString("shortname") }.ifEmpty { it.getString("symbol") },
                    exchange = it.optString("exchDisp").ifEmpty { it.optString("exchange") },
                    type = it.optString("typeDisp"),
                )
            }
    }

    fun parseChart(json: String): Price {
        val chart = JSONObject(json).getJSONObject("chart")
        val result = chart.optJSONArray("result")?.optJSONObject(0)
            ?: error(chart.optJSONObject("error")?.optString("description") ?: "No price found")
        val meta = result.getJSONObject("meta")
        val raw = meta.getDouble("regularMarketPrice")
        val prev = if (meta.has("chartPreviousClose")) meta.getDouble("chartPreviousClose") else null
        val currency = meta.optString("currency", "EUR")
        val pence = currency == "GBp" || currency == "GBX"
        val div = if (pence) 100.0 else 1.0
        return Price(meta.optString("symbol"), raw / div, prev?.div(div), if (pence) "GBP" else currency)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        return try {
            val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream ?: error("HTTP ${c.responseCode}")
            stream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
