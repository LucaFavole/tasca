package app.tasca.data

/** What one balance widget shows. Stored per widget as a Long: an account id, or a negative code for the totals. */
sealed interface WidgetFigure {
    data class Account(val id: Long) : WidgetFigure
    /** Every account added up, as the Total on the Accounts tab. */
    data object Total : WidgetFigure
    /** The Total plus what the investments gained or lost at the last fetched prices. */
    data object NetWorth : WidgetFigure

    fun encode(): Long = when (this) {
        is Account -> id
        Total -> TOTAL
        NetWorth -> NET_WORTH
    }

    companion object {
        private const val TOTAL = -10L
        private const val NET_WORTH = -11L

        fun decode(stored: Long): WidgetFigure? = when {
            stored == TOTAL -> Total
            stored == NET_WORTH -> NetWorth
            stored > 0 -> Account(stored)
            else -> null
        }
    }
}

/** The name and amount of [figure], or null when its account no longer exists. Both phones' widgets show these. */
suspend fun Ledger.figureOf(figure: WidgetFigure, accounts: List<AccountBalance>): Pair<String, Long>? {
    val total = accounts.sumOf { it.balanceCents }
    return when (figure) {
        is WidgetFigure.Account -> accounts.firstOrNull { it.id == figure.id && !it.deleted }?.let { it.name to it.balanceCents }
        WidgetFigure.Total -> "Total" to total
        WidgetFigure.NetWorth -> "Net worth" to total + Portfolio.of(dao.holdings(), dao.quotes().associateBy { it.symbol }).gainCents
    }
}
