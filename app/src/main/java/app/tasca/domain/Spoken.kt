package app.tasca.domain

import app.tasca.data.Category
import app.tasca.data.CategoryKind
import app.tasca.data.Defaults
import app.tasca.data.TxnKind
import java.time.LocalDate

/** What a short spoken sentence such as "15 euro pranzo" or "ricevuto 200 stipendio" says about a new transaction. */
data class Spoken(val amountCents: Long?, val kind: TxnKind, val categoryId: Long?, val note: String, val date: LocalDate)

object SpokenParser {
    private val INCOME = listOf("entrat", "ricevut", "incass", "guadagn", "stipend", "income", "received", "salary")
    private val FILLER = setOf(
        "euro", "euros", "eur", "€", "e", "di", "per", "il", "la", "lo", "un", "una", "ho", "speso", "spesi", "pagato",
        "ricevuto", "ricevuti", "entrata", "oggi", "ieri", "altroieri", "l'altro", "spent", "paid", "for", "on", "today", "yesterday",
    )
    private val CENTS = Regex("""(\d+)\s*(?:euro|€)\s*e\s*(\d{1,2})\b""")
    private val NUMBER = Regex("""\d[\d.,]*""")

    fun parse(text: String, categories: List<Category>, today: LocalDate): Spoken {
        val t = text.lowercase().replace("€", " euro ").trim()
        val words = t.split(Regex("""\s+"""))
        val kind = if (INCOME.any { k -> words.any { it.startsWith(k) } }) TxnKind.INCOME else TxnKind.EXPENSE
        val catKind = if (kind == TxnKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE

        val cents = CENTS.find(t)
        val amount = cents?.let { it.groupValues[1].toLong() * 100 + it.groupValues[2].padEnd(2, '0').toLong() }
            ?: NUMBER.find(t)?.value?.let(::amountOf)
        val withoutAmount = (cents?.let { t.removeRange(it.range) } ?: NUMBER.replaceFirst(t, " "))

        val ofKind = categories.filter { it.kind == catKind }
        val category = ofKind.firstOrNull { c -> c.name.lowercase() != "altro" && words.any { it.startsWith(c.name.lowercase().take(5)) } }
            ?: Defaults.iconFor(words)?.let { icon -> ofKind.firstOrNull { it.icon == icon } }

        val date = when {
            "altroieri" in words || "l'altro ieri" in t -> today.minusDays(2)
            "ieri" in words || "yesterday" in words -> today.minusDays(1)
            else -> today
        }
        val note = withoutAmount.split(Regex("""\s+""")).filter { it.isNotBlank() && it !in FILLER }.joinToString(" ")
        return Spoken(amount, kind, category?.id, note, date)
    }

    /** "12", "12,50", "12.50" and "1.200" (thousands); null when it isn't a positive amount. */
    private fun amountOf(raw: String): Long? {
        val s = raw.trimEnd('.', ',')
        val normal = when {
            ',' in s -> s.replace(".", "").replace(',', '.')
            Regex("""\d{1,3}(\.\d{3})+""").matches(s) -> s.replace(".", "")
            else -> s
        }
        return normal.toDoubleOrNull()?.takeIf { it > 0 && it < 1e9 }?.let(Money::fromDouble)
    }
}
