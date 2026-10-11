package app.tasca.domain

import java.time.LocalDate

/** What a scanned receipt says: the total paid, the date and the shop. Any of them can be missing. */
data class ReceiptScan(val amountCents: Long?, val date: LocalDate?, val store: String?)

/** Reads a receipt's text, one string per printed row, as Italian (and most European) receipts lay it out. */
object ReceiptParser {
    private val TOTAL = listOf("totale complessivo", "totale euro", "totale eur", "importo pagato", "da pagare", "totale", "total", "importo")
    private val NOT_TOTAL = listOf("subtot", "iva", "sconto", "resto", "pezzi", "articoli", "risparmi", "punti")
    private val PAID_WITH = listOf("contante", "resto", "pagamento", "carta", "bancomat")
    private val MONEY = Regex("""(?<![\d.,])(\d{1,3}(?:\.\d{3})+|\d+)[,.](\d{2})(?![\d.,]*\d)""")
    private val DATE = Regex("""\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{4}|\d{2})\b""")
    private val NOT_STORE = listOf("documento", "commerciale", "p.iva", "partita", "via ", "viale", "piazza", "tel", "cod", "scontrino", "c.f", "www")

    fun parse(rows: List<String>, today: LocalDate): ReceiptScan =
        ReceiptScan(total(rows.map { it.lowercase() }), date(rows, today), store(rows))

    private fun money(row: String): List<Long> = MONEY.findAll(row).map { m ->
        m.groupValues[1].replace(".", "").toLong() * 100 + m.groupValues[2].toLong()
    }.toList()

    private fun total(rows: List<String>): Long? {
        for (label in TOTAL) {
            for ((i, row) in rows.withIndex()) {
                if (label !in row || NOT_TOTAL.any { it in row }) continue
                val amount = money(row).lastOrNull() ?: rows.getOrNull(i + 1)?.let { money(it).lastOrNull() }
                if (amount != null && amount > 0) return amount
            }
        }
        return rows.filter { r -> PAID_WITH.none { it in r } }.flatMap(::money).maxOrNull()?.takeIf { it > 0 }
    }

    private fun date(rows: List<String>, today: LocalDate): LocalDate? = rows.firstNotNullOfOrNull { row ->
        DATE.findAll(row).firstNotNullOfOrNull { m ->
            val (d, mo, y) = m.destructured
            val year = if (y.length == 2) 2000 + y.toInt() else y.toInt()
            runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull()
                ?.takeIf { !it.isAfter(today) && it.isAfter(today.minusYears(2)) }
        }
    }

    private fun store(rows: List<String>): String? = rows.take(5).firstOrNull { row ->
        val r = row.lowercase()
        row.count { it.isLetter() } >= 3 && NOT_STORE.none { it in r } && money(r).isEmpty()
    }?.let { row ->
        row.replace(Regex("""\b(s\.?p\.?a\.?|s\.?r\.?l\.?|s\.?n\.?c\.?)\s*$""", RegexOption.IGNORE_CASE), "")
            .trim().lowercase().split(Regex("""\s+""")).joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) }
    }
}
