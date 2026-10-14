package app.tasca.ui

import app.tasca.data.Ledger
import app.tasca.data.TxnKind
import app.tasca.data.TxnRow
import app.tasca.domain.Money
import app.tasca.ui.screens.relativeDay
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/** One row of the recent activity widget, as both phones' widgets print it. */
data class RecentRow(val id: Long, val name: String, val detail: String, val amount: String, val kind: TxnKind)

/** The latest [count] transactions, newest first, as the recent activity widget lists them. */
suspend fun Ledger.recentRows(count: Int): List<RecentRow> =
    dao.txnRows(LocalDate(1900, 1, 1), LocalDate(2999, 12, 31), null, null, "", count).first().map { it.recentRow() }

private fun TxnRow.recentRow() = RecentRow(
    id = id,
    name = note.ifBlank { categoryName ?: "Transfer" },
    detail = (if (kind == TxnKind.TRANSFER) "$accountName → $toAccountName" else accountName) + " · " + relativeDay(date),
    amount = when (kind) {
        TxnKind.EXPENSE -> "−" + Money.format(amountCents)
        TxnKind.INCOME -> "+" + Money.format(amountCents)
        TxnKind.TRANSFER -> Money.format(amountCents)
    },
    kind = kind,
)
