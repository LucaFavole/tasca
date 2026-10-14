package app.tasca

import app.tasca.data.Account
import app.tasca.data.Category
import app.tasca.data.CategoryKind
import app.tasca.data.Txn
import app.tasca.data.TxnKind
import app.tasca.domain.Money
import app.tasca.domain.today
import app.tasca.ui.RecentRow
import app.tasca.ui.recentRows
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The rows the recent activity widget shows on Android, and on iOS through widget.json. */
class RecentRowTest {
    private val ledger = inMemoryLedger()

    @AfterTest fun close() = ledger.close()

    @Test fun rowsNameTheNoteOrCategoryAndSignTheAmountNewestFirst() = runTest {
        val main = ledger.saveAccount(Account(name = "Main", color = 0))
        val savings = ledger.saveAccount(Account(name = "Risparmi", color = 0))
        val food = ledger.dao.upsertCategory(Category(name = "Cibo", kind = CategoryKind.EXPENSE, color = 0, icon = "cart"))
        val pay = ledger.dao.upsertCategory(Category(name = "Stipendio", kind = CategoryKind.INCOME, color = 0, icon = "work"))
        val yesterday = today().minus(1, DateTimeUnit.DAY)
        val dao = ledger.dao
        val transfer = dao.upsertTxn(Txn(kind = TxnKind.TRANSFER, amountCents = 5_000, date = yesterday, accountId = main, toAccountId = savings))
        val salary = dao.upsertTxn(Txn(kind = TxnKind.INCOME, amountCents = 150_000, date = yesterday, accountId = main, categoryId = pay))
        val lunch = dao.upsertTxn(Txn(kind = TxnKind.EXPENSE, amountCents = 850, date = today(), accountId = main, categoryId = food, note = "Pizza"))
        val shop = dao.upsertTxn(Txn(kind = TxnKind.EXPENSE, amountCents = 2_340, date = today(), accountId = main, categoryId = food))

        assertEquals(
            listOf(
                RecentRow(shop, "Cibo", "Main · Today", "−" + Money.format(2_340), TxnKind.EXPENSE),
                RecentRow(lunch, "Pizza", "Main · Today", "−" + Money.format(850), TxnKind.EXPENSE),
                RecentRow(salary, "Stipendio", "Main · Yesterday", "+" + Money.format(150_000), TxnKind.INCOME),
                RecentRow(transfer, "Transfer", "Main → Risparmi · Yesterday", Money.format(5_000), TxnKind.TRANSFER),
            ),
            ledger.recentRows(4),
        )
        assertEquals(listOf(shop, lunch), ledger.recentRows(2).map { it.id })
    }
}
