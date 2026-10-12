package app.tasca.domain

import app.tasca.data.Category
import app.tasca.data.CategoryKind
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** One printed row of a receipt as OCR read it, and its text height in pixels: totals are often printed larger. */
data class ReceiptLine(val text: String, val height: Int = 0)

/**
 * What a scanned receipt says. [confident] is false when the total had to be guessed (no clear total line,
 * or two amounts scored about the same), so the screen can ask to check it.
 */
data class ReceiptScan(
    val amountCents: Long?,
    val date: LocalDate?,
    val store: String?,
    val confident: Boolean,
    /** Words of the shop name and of the items bought, which hint at the category. */
    val storeWords: List<String> = emptyList(),
    val itemWords: List<String> = emptyList(),
) {
    /** The shop decides first ("Esselunga" is groceries); only an unknown shop falls back to what was bought. */
    fun categoryIn(categories: List<Category>): Long? {
        val expense = categories.filter { it.kind == CategoryKind.EXPENSE }
        return (guessCategory(storeWords, expense) ?: guessCategory(itemWords, expense))?.id
    }
}

/**
 * Reads Italian (and most European) receipts. Instead of trusting the first "TOTALE" line, every amount
 * becomes a candidate and collects evidence:
 * - its row's label, matched fuzzily so OCR slips like "T0TALE" or "COMPLESSlVO" still count
 *   (the idea of ReceiptManager/receipt-parser-legacy), while subtotal, VAT and discount lines earn nothing;
 * - larger print than the other rows;
 * - the same amount on a payment line ("PAGAMENTO ELETTRONICO", "BANCOMAT"), or equal to cash minus change;
 * - the items above it adding up to it;
 * - appearing more than once.
 * The best-scoring amount wins.
 */
object ReceiptParser {
    private val STRONG = listOf("totale complessivo", "importo pagato", "totale da pagare", "da pagare", "totale euro", "totale eur", "importo totale")
    private val TOTAL = listOf("totale", "total", "importo", "tot", "summe", "amount")
    private val PAID = listOf("elettronico", "bancomat", "carta", "pos", "credito", "debito", "satispay", "pagamento")
    private val CASH = listOf("contante", "contanti", "cash")
    private val CHANGE = listOf("resto", "change")
    private val NOT_TOTAL = listOf(
        "subtotale", "subtot", "iva", "imposta", "imponibile", "sconto", "sconti", "risparmio", "risparmiato", "punti",
        "pezzi", "articoli", "resto", "buono", "aliquota", "netto", "esente",
    )
    private val NOT_STORE = listOf(
        "documento", "commerciale", "p.iva", "piva", "partita", "via ", "viale", "piazza", "corso ", "tel", "cod", "scontrino",
        "c.f", "www", "vendita", "prestazione", "cassa", "operatore",
    )

    /** Chains spelled out, so OCR damage on the logo still finds the name. Aliases are compared word by word. */
    private val STORES = mapOf(
        "Esselunga" to listOf("esselunga"), "Conad" to listOf("conad"), "Coop" to listOf("coop", "ipercoop"),
        "Lidl" to listOf("lidl"), "Carrefour" to listOf("carrefour"), "Eurospin" to listOf("eurospin"),
        "Penny" to listOf("penny"), "Aldi" to listOf("aldi"), "Despar" to listOf("despar", "eurospar", "interspar"),
        "Pam" to listOf("pam", "panorama"), "Tigros" to listOf("tigros"), "Bennet" to listOf("bennet"), "MD" to listOf("md"),
        "Crai" to listOf("crai"), "Sigma" to listOf("sigma"), "Unes" to listOf("unes"), "Famila" to listOf("famila"),
        "Iper" to listOf("iper"), "Il Gigante" to listOf("gigante"), "Decathlon" to listOf("decathlon"), "Ikea" to listOf("ikea"),
        "McDonald's" to listOf("mcdonald's", "mcdonalds", "mcdonald"), "Burger King" to listOf("burger"),
        "Autogrill" to listOf("autogrill"), "Tigotà" to listOf("tigota", "tigotà"), "Acqua & Sapone" to listOf("sapone"),
        "Zara" to listOf("zara"), "H&M" to listOf("h&m"), "Primark" to listOf("primark"), "Mediaworld" to listOf("mediaworld"),
        "Unieuro" to listOf("unieuro"), "Feltrinelli" to listOf("feltrinelli"), "Q8" to listOf("q8"), "Eni" to listOf("eni", "enilive"),
        "Esso" to listOf("esso"), "IP" to listOf("ip"),
    )

    private val MONEY = Regex("""(?<![\d.,])(-?)(\d{1,3}(?:[.' ]\d{3})+|\d+)[,.](\d{2})(-?)(?![\d.,]*\d)""")
    private val SPLIT_DECIMALS = Regex("""(\d)\s*([,.])\s+(\d{2})\b""")
    private val NUMERIC_TOKEN = Regex("""[\dOoIl|SB]+[,.][\dOoIl|SB]{2}|[\dOoIl|]{1,2}[/.\-][\dOoIl|]{1,2}[/.\-][\dOoIl|]{2,4}""")
    private val DATE_NUM = Regex("""\b(\d{1,2})\s?[/.\-]\s?(\d{1,2})\s?[/.\-]\s?(\d{4}|\d{2})\b""")
    private val DATE_ISO = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val DATE_WORD = Regex("""\b(\d{1,2})\s*(gen|feb|mar|apr|mag|giu|lug|ago|set|ott|nov|dic)[a-z]*\.?\s*(\d{4}|\d{2})\b""")
    private val MONTHS = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")
    private val TIME = Regex("""\b\d{1,2}[:.]\d{2}\b""")

    private class Row(val label: String, val words: List<String>, val amounts: List<Long>, val height: Int)

    fun parse(lines: List<ReceiptLine>, today: LocalDate): ReceiptScan {
        val rows = lines.filter { it.text.isNotBlank() }.map { row(it) }
        val (amount, confident) = total(rows)
        val store = store(lines, rows)
        val special = STRONG.flatMap { it.split(' ') } + TOTAL + PAID + CASH + CHANGE + NOT_TOTAL
        val items = rows.filter { r -> r.amounts.size == 1 && r.words.none { w -> special.any { similar(w, it) } } }.flatMap { it.words }
        return ReceiptScan(amount, date(lines, today), store, confident, store?.lowercase()?.split(' ').orEmpty(), items)
    }

    /** Also accepts plain strings, one per printed row. */
    fun parseText(rows: List<String>, today: LocalDate) = parse(rows.map { ReceiptLine(it) }, today)

    private fun row(line: ReceiptLine): Row {
        val fixed = SPLIT_DECIMALS.replace(fixDigits(line.text)) { "${it.groupValues[1]}${it.groupValues[2]}${it.groupValues[3]}" }
        val amounts = MONEY.findAll(fixed).map { m ->
            val (minus, whole, cents, trailingMinus) = m.destructured
            val v = whole.filter(Char::isDigit).toLong() * 100 + cents.toLong()
            if (minus.isNotEmpty() || trailingMinus.isNotEmpty()) -v else v
        }.toList()
        val label = MONEY.replace(fixed, " ").lowercase().replace(Regex("""[^a-zà-ù&' ]"""), " ").replace(Regex("""\s+"""), " ").trim()
        return Row(label, label.split(' ').filter { it.length >= 2 }, amounts, line.height)
    }

    /** OCR reads 0 as O, 1 as l or I, 5 as S and 8 as B; fixes them only inside tokens shaped like an amount or a date. */
    private fun fixDigits(s: String) = NUMERIC_TOKEN.replace(s) { m ->
        if (m.value.none(Char::isDigit)) m.value
        else m.value.map { c -> when (c) { 'O', 'o' -> '0'; 'I', 'l', '|' -> '1'; 'S' -> '5'; 'B' -> '8'; else -> c } }.joinToString("")
    }

    private fun total(rows: List<Row>): Pair<Long?, Boolean> {
        // A label alone on its row lends itself to the amount alone on the next one ("TOTALE EURO" / "4,38").
        val labelled = rows.mapIndexed { i, r ->
            val next = rows.getOrNull(i + 1)
            when {
                r.amounts.isEmpty() && next != null && next.amounts.isNotEmpty() && next.words.isEmpty() -> Row(r.label, r.words, next.amounts, maxOf(r.height, next.height))
                r.words.isEmpty() && i > 0 && rows[i - 1].amounts.isEmpty() -> Row("", emptyList(), emptyList(), r.height)
                else -> r
            }
        }
        val heights = labelled.filter { it.amounts.isNotEmpty() && it.height > 0 }.map { it.height }.sorted()
        val medianHeight = heights.getOrNull(heights.size / 2) ?: 0

        val score = HashMap<Long, Double>()
        val seen = HashMap<Long, Int>()
        fun add(v: Long, s: Double) { score[v] = (score[v] ?: 0.0) + s }

        var cash: Long? = null
        var change: Long? = null
        var firstTotalRow = labelled.size
        for ((i, r) in labelled.withIndex()) {
            if (r.amounts.isEmpty()) continue
            val v = r.amounts.last()
            if (v <= 0) continue
            val negative = r.words.any { w -> NOT_TOTAL.any { similar(w, it) } }
            val strong = STRONG.any { phrase(r.words, it) }
            val total = TOTAL.any { k -> r.words.any { similar(it, k) } }
            when {
                CHANGE.any { k -> r.words.any { similar(it, k) } } -> { change = v; continue }
                CASH.any { k -> r.words.any { similar(it, k) } } -> { cash = v; continue }
            }
            if (strong || total) firstTotalRow = minOf(firstTotalRow, i)
            add(v, when {
                strong -> 6.0
                negative -> 0.0
                total -> 4.0
                PAID.any { k -> r.words.any { similar(it, k) } } -> 3.0
                else -> 0.0
            })
            if (!negative && medianHeight > 0 && r.height >= medianHeight * 1.4) add(v, 2.0)
            if (!negative) seen[v] = (seen[v] ?: 0) + 1
        }
        seen.forEach { (v, n) -> if (n > 1) add(v, minOf(n - 1, 2).toDouble()) }
        val c = cash
        val ch = change
        if (c != null && ch != null && c - ch > 0) add(c - ch, 4.0)
        val itemsSum = labelled.take(firstTotalRow).filter { r ->
            r.amounts.size == 1 && r.words.isNotEmpty() && r.words.none { w -> NOT_TOTAL.filter { it != "sconto" && it != "sconti" }.any { similar(w, it) } }
        }.sumOf { it.amounts.single() }
        if (itemsSum > 0 && score.containsKey(itemsSum)) add(itemsSum, 3.0)
        score.keys.maxOrNull()?.let { add(it, 1.0) }

        val ranked = score.entries.sortedByDescending { it.value }
        val best = ranked.firstOrNull()?.takeIf { it.value > 0 } ?: return (if (c != null && ch == null) c else null) to false
        val margin = best.value - (ranked.getOrNull(1)?.value ?: 0.0)
        return best.key to (best.value >= 5 && margin >= 2)
    }

    private fun date(lines: List<ReceiptLine>, today: LocalDate): LocalDate? {
        val found = lines.flatMap { line ->
            val t = fixDigits(line.text).lowercase()
            val dates = DATE_NUM.findAll(t).map { m -> m.groupValues.let { dateOf(it[3], it[2].toInt(), it[1].toInt()) } } +
                DATE_ISO.findAll(t).map { m -> m.groupValues.let { dateOf(it[1], it[2].toInt(), it[3].toInt()) } } +
                DATE_WORD.findAll(t).map { m -> m.groupValues.let { dateOf(it[3], MONTHS.indexOf(it[2]) + 1, it[1].toInt()) } }
            dates.filterNotNull().filter { it <= today && it > today.minus(2, DateTimeUnit.YEAR) }
                .map { it to TIME.containsMatchIn(t.replace(DATE_NUM, " ")) }.toList()
        }
        return (found.firstOrNull { it.second } ?: found.firstOrNull())?.first
    }

    private fun dateOf(year: String, month: Int, day: Int): LocalDate? =
        LocalDate.orNull(if (year.length == 2) 2000 + year.toInt() else year.toInt(), month, day)

    private fun store(lines: List<ReceiptLine>, rows: List<Row>): String? {
        val top = rows.take(8).flatMap { it.words }
        STORES.entries.firstOrNull { (_, aliases) ->
            aliases.any { a -> top.any { w -> if (a.length <= 4) w == a else similar(w, a, 0.8) } }
        }?.let { return it.key }
        return lines.take(6).filter { l ->
            val r = l.text.lowercase()
            l.text.count(Char::isLetter) >= 3 && NOT_STORE.none { it in r } && MONEY.find(fixDigits(l.text)) == null
        }.maxByOrNull { it.height }?.let { l ->
            l.text.replace(Regex("""\b(s\.?p\.?a\.?|s\.?r\.?l\.?|s\.?n\.?c\.?|s\.?a\.?s\.?)\s*$""", RegexOption.IGNORE_CASE), "")
                .trim().lowercase().split(Regex("""\s+""")).joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) }
        }
    }

    /** Every word of [phrase] fuzzily present, in order, among [words]. */
    private fun phrase(words: List<String>, phrase: String): Boolean {
        var from = 0
        for (p in phrase.split(' ')) {
            val at = (from until words.size).firstOrNull { similar(words[it], p) } ?: return false
            from = at + 1
        }
        return true
    }

    /** Edit-distance similarity; short words must match exactly, since one wrong letter changes them completely. */
    private fun similar(a: String, b: String, min: Double = 0.75): Boolean {
        if (a == b) return true
        if (b.length < 5 || a.length < 4) return false
        return 1.0 - levenshtein(a, b).toDouble() / maxOf(a.length, b.length) >= min
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }
}
