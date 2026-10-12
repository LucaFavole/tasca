package app.tasca.io

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

data class SymbolHit(val symbol: String, val name: String, val exchange: String, val type: String)

/** A price in the instrument's own currency, already corrected for quotes in pence. */
data class Price(val symbol: String, val price: Double, val previousClose: Double?, val currency: String)

/** Yahoo Finance's public endpoints: symbol search (by ISIN, ticker or name) and last price. */
object Yahoo {
    private const val SEARCH = "https://query2.finance.yahoo.com/v1/finance/search?quotesCount=10&newsCount=0&q="

    private val ISIN = Regex("^[A-Z]{2}[A-Z0-9]{9}[0-9]$")

    /**
     * An ISIN search often returns only a foreign listing, so the fund's name is searched too and the
     * listings are merged with euro exchanges first.
     */
    fun search(query: String): List<SymbolHit> {
        val q = query.trim()
        val direct = parseSearch(httpGet(SEARCH + enc(q)))
        if (!ISIN.matches(q.uppercase()) || direct.isEmpty()) return direct
        val byName = runCatching { parseSearch(httpGet(SEARCH + enc(direct.first().name))) }.getOrDefault(emptyList())
        return rank(direct + byName)
    }

    private val exchangeOrder = listOf("XETRA", "Milan", "Paris", "Amsterdam", "Brussels", "Vienna", "Swiss", "London")

    fun rank(hits: List<SymbolHit>): List<SymbolHit> = hits
        .distinctBy { it.symbol }
        .filterNot { it.name.contains("iNAV", ignoreCase = true) }
        .sortedBy { h -> exchangeOrder.indexOf(h.exchange).let { if (it < 0) exchangeOrder.size else it } }

    fun price(symbol: String): Price =
        parseChart(httpGet("https://query1.finance.yahoo.com/v8/finance/chart/${enc(symbol)}?range=1d&interval=1d"))

    /** How many units of [to] one unit of [from] is worth. */
    fun rate(from: String, to: String): Double = if (from == to) 1.0 else price("$from$to=X").price

    fun parseSearch(json: String): List<SymbolHit> {
        val quotes = Json.parseToJsonElement(json).jsonObject["quotes"] as? JsonArray ?: return emptyList()
        return quotes.map { it as? JsonObject ?: error("Not an object: $it") }
            .filter { "symbol" in it && it.optBoolean("isYahooFinance", true) }
            .map {
                SymbolHit(
                    symbol = it.string("symbol"),
                    name = it.optString("longname").ifEmpty { it.optString("shortname") }.ifEmpty { it.string("symbol") },
                    exchange = it.optString("exchDisp").ifEmpty { it.optString("exchange") },
                    type = it.optString("typeDisp"),
                )
            }
    }

    fun parseChart(json: String): Price {
        val chart = Json.parseToJsonElement(json).jsonObject.getValue("chart").jsonObject
        val result = (chart["result"] as? JsonArray)?.getOrNull(0) as? JsonObject
            ?: error((chart["error"] as? JsonObject)?.optString("description") ?: "No price found")
        val meta = result.getValue("meta").jsonObject
        val raw = meta.double("regularMarketPrice")
        val prev = if ("chartPreviousClose" in meta) meta.double("chartPreviousClose") else null
        val currency = meta.optString("currency", "EUR")
        val pence = currency == "GBp" || currency == "GBX"
        val div = if (pence) 100.0 else 1.0
        return Price(meta.optString("symbol"), raw / div, prev?.div(div), if (pence) "GBP" else currency)
    }

    // Reads values the way Android's org.json did, which this code used before: a JSON null reads as "null".
    private fun JsonElement.asText(): String = if (this is JsonPrimitive) content else toString()

    private fun JsonObject.string(key: String): String = getValue(key).asText()

    private fun JsonObject.optString(key: String, fallback: String = ""): String = this[key]?.asText() ?: fallback

    private fun JsonObject.optBoolean(key: String, fallback: Boolean): Boolean {
        val v = this[key] as? JsonPrimitive ?: return fallback
        if (v is JsonNull) return fallback
        return v.content.lowercase().toBooleanStrictOrNull() ?: fallback
    }

    private fun JsonObject.double(key: String): Double {
        val v = getValue(key)
        if (v !is JsonPrimitive || v is JsonNull) error("$key is not a number")
        return v.content.toDouble()
    }

    /** As java.net.URLEncoder encodes form values in UTF-8. */
    internal fun enc(s: String): String = buildString {
        for (b in s.encodeToByteArray()) {
            val c = (b.toInt() and 0xFF).toChar()
            when {
                c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in ".-*_" -> append(c)
                c == ' ' -> append('+')
                else -> append('%').append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}

/** The body of a GET to [url], with a browser user agent and 10 second timeouts; error bodies are returned too. */
internal expect fun httpGet(url: String): String
