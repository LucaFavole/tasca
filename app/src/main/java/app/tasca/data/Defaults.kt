package app.tasca.data

object Palette {
    val colors: List<Int> = listOf(
        0xFF6C63FF, 0xFFFF6B6B, 0xFF4ECDC4, 0xFFFFB347, 0xFFA66CFF, 0xFF3DDC97, 0xFFFF8FAB, 0xFF5DA9E9,
        0xFFE9C46A, 0xFF2A9D8F, 0xFFF4A261, 0xFF8D99AE, 0xFFB5179E, 0xFF06D6A0, 0xFFEF476F, 0xFF118AB2,
    ).map { it.toInt() }

    fun at(i: Int): Int = colors[Math.floorMod(i, colors.size)]
}

/** Default categories, and the keyword table that styles categories created by an import. */
object Defaults {
    private data class Style(val keywords: List<String>, val icon: String, val color: Int)

    private val styles = listOf(
        Style(listOf("casa", "home", "affitto", "rent", "housing", "deposit"), "home", 7),
        Style(listOf("svago", "fun", "leisure", "divertimento"), "celebration", 5),
        Style(listOf("aliment", "grocer", "spesa", "food"), "cart", 2),
        Style(listOf("caff", "coffee", "bar"), "coffee", 3),
        Style(listOf("ristor", "restaurant", "mensa", "pranzo", "cena"), "restaurant", 10),
        Style(listOf("wolt", "deliver", "glovo", "deliveroo"), "delivery", 13),
        Style(listOf("trasport", "transport", "bus", "treno", "auto", "car"), "bus", 15),
        Style(listOf("salute", "health", "medic", "farmac"), "health", 1),
        Style(listOf("istruz", "educat", "school", "univ", "corso"), "school", 12),
        Style(listOf("regal", "gift"), "gift", 6),
        Style(listOf("famigl", "family"), "family", 14),
        Style(listOf("fisic", "sport", "gym", "palestra", "fitness"), "fitness", 9),
        Style(listOf("vestir", "cloth", "abbigl", "scarpe"), "clothes", 10),
        Style(listOf("rimbors", "refund"), "receipt", 8),
        Style(listOf("persi", "lost"), "money_off", 11),
        Style(listOf("stipend", "salary", "salario", "paga"), "work", 5),
        Style(listOf("interess", "interest", "dividend"), "bank", 2),
        Style(listOf("commission", "fee"), "receipt", 11),
        Style(listOf("tasse", "tax", "impost"), "bank", 1),
        Style(listOf("plusval", "minusval", "capital gain"), "invest", 0),
        Style(listOf("risparm", "saving", "invest"), "savings", 0),
        Style(listOf("bolle", "bill", "utilit", "luce", "gas"), "bolt", 3),
        Style(listOf("abbonam", "subscri", "netflix", "spotify"), "subscriptions", 4),
        Style(listOf("viagg", "travel", "volo", "flight", "vacanz"), "flight", 7),
        Style(listOf("tecno", "tech", "phone", "telefon"), "phone", 15),
        Style(listOf("animal", "pet"), "pets", 9),
    )

    fun styleFor(name: String, fallbackIndex: Int): Pair<String, Int> {
        val n = name.lowercase()
        val s = styles.firstOrNull { st -> st.keywords.any { n.contains(it) } }
        return if (s != null) s.icon to Palette.at(s.color) else "category" to Palette.at(fallbackIndex)
    }

    private val expenseNames = listOf(
        "Casa", "Alimentari", "Caffè", "Svago", "Trasporti", "Salute", "Istruzione", "Regali",
        "Famiglia", "Attività fisica", "Roba da vestire", "Bollette", "Abbonamenti", "Viaggi", "Altro",
    )
    private val incomeNames = listOf("Stipendio", "Regalo", "Interessi", "Rimborsi", "Altro")

    fun categories(): List<Category> =
        expenseNames.mapIndexed { i, n -> category(n, CategoryKind.EXPENSE, i) } +
            incomeNames.mapIndexed { i, n -> category(n, CategoryKind.INCOME, i + 3) }

    fun category(name: String, kind: CategoryKind, index: Int): Category {
        val (icon, color) = styleFor(name, index)
        return Category(name = name, kind = kind, color = color, icon = if (name == "Altro") "other" else icon)
    }

    fun account(name: String, index: Int) = Account(
        name = name, color = Palette.at(index), icon = if (name.lowercase().contains("risparm")) "savings" else "wallet",
    )
}
