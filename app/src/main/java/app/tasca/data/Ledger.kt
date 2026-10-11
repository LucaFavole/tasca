package app.tasca.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.withTransaction
import app.tasca.domain.MonthLeft
import app.tasca.domain.Trade
import app.tasca.domain.TradeSide
import app.tasca.io.AccountSheetRow
import app.tasca.io.HoldingSheetRow
import app.tasca.io.LedgerSheets
import app.tasca.io.ParsedWorkbook
import app.tasca.io.SheetRow
import app.tasca.io.Yahoo
import java.time.LocalDate
import java.time.YearMonth

@Database(
    entities = [Account::class, Category::class, Txn::class, Recurring::class, Budget::class, Holding::class, Quote::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = AppDb.TotalIsEverything::class),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = AppDb.AccountCodes::class),
    ],
)
@TypeConverters(Converters::class)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): LedgerDao

    @DeleteColumn(tableName = "account", columnName = "includeInTotal")
    @RenameColumn(tableName = "account", fromColumnName = "archived", toColumnName = "deleted")
    class TotalIsEverything : AutoMigrationSpec

    class AccountCodes : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) = db.execSQL("UPDATE account SET code = 'A' || id")
    }
}

/** [keptAccounts]: accounts already in use whose opening balance, state or investments in the file were ignored. */
data class ImportResult(val added: Int, val skipped: Int, val newAccounts: Int, val newCategories: Int, val keptAccounts: Int)

/** The single entry point for reading and changing the user's money data. */
class Ledger(private val db: AppDb) {
    val dao = db.dao()

    suspend fun ensureSeeded() = db.withTransaction {
        if (dao.accounts().isEmpty() && dao.categories().isEmpty()) {
            saveAccount(Defaults.account("Main", 0))
            Defaults.categories().forEach { dao.upsertCategory(it) }
        }
    }

    /** Posts every due occurrence of every active recurring payment. Safe to call any number of times. */
    suspend fun postDueRecurring(today: LocalDate = LocalDate.now()): Int = db.withTransaction {
        var count = 0
        for (r in dao.activeRecurring()) {
            var cur = r
            while (!cur.finished && !cur.nextDate.isAfter(today)) {
                dao.upsertTxn(cur.toTxn())
                cur = cur.copy(posted = cur.posted + 1)
                count++
            }
            if (cur != r) dao.upsertRecurring(cur)
        }
        count
    }

    suspend fun monthLeft(today: LocalDate = LocalDate.now()): MonthLeft {
        val month = YearMonth.from(today)
        val from = month.atDay(1)
        val to = month.atEndOfMonth()
        return MonthLeft.of(today, dao.total(TxnKind.INCOME, from, to), dao.total(TxnKind.EXPENSE, from, to), dao.activeRecurring())
    }

    /** Inserts or updates an account, giving a new one the next free code. */
    suspend fun saveAccount(a: Account): Long = db.withTransaction {
        dao.upsertAccount(if (a.code.isEmpty()) a.copy(code = nextCode(dao.accounts())) else a)
    }

    private fun nextCode(accounts: List<Account>) =
        "A" + ((accounts.mapNotNull { it.code.removePrefix("A").toIntOrNull() }.maxOrNull() ?: 0) + 1)

    /** Removes an unused account; an account with history is hidden instead, so the Total and history stay intact. */
    suspend fun deleteAccount(id: Long) = db.withTransaction {
        if (dao.accountUsage(id) == 0 && dao.holdingCount(id) == 0) dao.removeAccount(id)
        else {
            dao.hideAccount(id)
            dao.pauseRecurringOf(id)
        }
    }

    /** Fetches today's price of every held symbol, plus the exchange rates into [appCurrency]. Returns the symbols that failed. */
    suspend fun refreshQuotes(appCurrency: String): List<String> {
        val rates = HashMap<String, Double>()
        val failed = mutableListOf<String>()
        var lastError: Throwable? = null
        val symbols = dao.heldSymbols()
        val quotes = symbols.mapNotNull { symbol ->
            runCatching {
                val p = Yahoo.price(symbol)
                val rate = rates.getOrPut(p.currency) { Yahoo.rate(p.currency, appCurrency) }
                Quote(symbol, p.price, p.previousClose, p.currency, rate, System.currentTimeMillis())
            }.onFailure { failed += symbol; lastError = it }.getOrNull()
        }
        if (symbols.isNotEmpty() && quotes.isEmpty()) throw lastError!!
        dao.upsertQuotes(quotes)
        return failed
    }

    /**
     * Buys or sells shares of a holding inside its account. The money moves between the account's cash and the
     * holding, so the balance is unchanged; only the fee and, on a sale, the realised result become transactions.
     */
    suspend fun trade(holdingId: Long, side: TradeSide, shares: Double, unitPrice: Double, feeCents: Long, taxCents: Long, date: LocalDate) =
        db.withTransaction {
            val h = dao.holding(holdingId) ?: error("Holding not found")
            val p = Trade.apply(side, h.quantity, h.investedCents, shares, unitPrice)
            if (p.quantity == 0.0) dao.deleteHolding(h.id) else dao.upsertHolding(h.copy(quantity = p.quantity, investedCents = p.investedCents))
            val verb = if (side == TradeSide.BUY) "Buy" else "Sell"
            if (feeCents > 0) {
                dao.upsertTxn(Txn(kind = TxnKind.EXPENSE, amountCents = feeCents, date = date, accountId = h.accountId,
                    categoryId = categoryId("Commissioni", CategoryKind.EXPENSE), note = "$verb ${h.name}"))
            }
            if (taxCents > 0) {
                dao.upsertTxn(Txn(kind = TxnKind.EXPENSE, amountCents = taxCents, date = date, accountId = h.accountId,
                    categoryId = categoryId("Tasse", CategoryKind.EXPENSE), note = "Tax on ${h.name}"))
            }
            if (p.realizedCents != 0L) {
                val gain = p.realizedCents > 0
                dao.upsertTxn(Txn(
                    kind = if (gain) TxnKind.INCOME else TxnKind.EXPENSE, amountCents = kotlin.math.abs(p.realizedCents), date = date,
                    accountId = h.accountId,
                    categoryId = if (gain) categoryId("Plusvalenze", CategoryKind.INCOME) else categoryId("Minusvalenze", CategoryKind.EXPENSE),
                    note = "Sold ${h.name}",
                ))
            }
        }

    private suspend fun categoryId(name: String, kind: CategoryKind): Long =
        dao.category(kind, name)?.id ?: dao.upsertCategory(Defaults.category(name, kind, dao.categories().size))

    suspend fun deleteCategory(id: Long, replacementId: Long?) = db.withTransaction {
        if (replacementId != null) dao.moveCategory(id, replacementId)
        dao.deleteCategory(id)
    }

    suspend fun eraseAll() = db.withTransaction {
        dao.clearHoldings(); dao.clearTxns(); dao.clearRecurring(); dao.clearBudgets(); dao.clearAccounts(); dao.clearCategories()
    }

    /**
     * Adds the rows of an imported workbook. Accounts are matched by code, or by name in files without codes
     * (the old app's), preferring an account that isn't deleted; categories by name. Missing ones are created.
     * A row identical to one already stored is skipped, so importing the same file twice adds nothing,
     * while genuine same-day duplicates inside one file are all kept.
     * Opening balances, deleted state and investments from the file apply only to accounts that were still empty,
     * so restoring an old backup never overwrites what changed on the phone since.
     */
    suspend fun import(book: ParsedWorkbook): ImportResult = db.withTransaction {
        val rows = book.rows
        val accounts = dao.accounts().toMutableList()
        val empty = accounts.filter { dao.accountUsage(it.id) == 0 && dao.holdingCount(it.id) == 0 }.map { it.id }.toMutableSet()
        val categories = dao.categories().associateBy { it.kind to it.name.lowercase() }.toMutableMap()
        var newAccounts = 0
        var newCategories = 0

        suspend fun account(name: String, code: String?): Long {
            val found = if (code != null) accounts.firstOrNull { it.code == code }
            else accounts.filter { it.name.equals(name, ignoreCase = true) }.minByOrNull { it.deleted }
            if (found != null) return found.id
            newAccounts++
            val a = Defaults.account(name, accounts.size).copy(code = code ?: nextCode(accounts))
            return a.copy(id = dao.upsertAccount(a)).also { accounts += it; empty += it.id }.id
        }

        suspend fun category(name: String, kind: CategoryKind): Long = categories.getOrPut(kind to name.lowercase()) {
            newCategories++
            val c = Defaults.category(name, kind, categories.size)
            c.copy(id = dao.upsertCategory(c))
        }.id

        val existing = dao.allTxns().groupingBy(::dedupeKey).eachCount().toMutableMap()
        val toInsert = mutableListOf<Txn>()
        var skipped = 0
        for (row in rows) {
            val txn = Txn(
                kind = row.kind,
                amountCents = row.amountCents,
                date = row.date,
                accountId = account(row.account, row.accountCode),
                toAccountId = row.toAccount?.let { account(it, row.toAccountCode) },
                categoryId = row.category?.let {
                    category(it, if (row.kind == TxnKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE)
                },
                note = row.note,
            )
            val key = dedupeKey(txn)
            val left = existing[key] ?: 0
            if (left > 0) {
                existing[key] = left - 1
                skipped++
            } else {
                toInsert += txn
            }
        }
        val kept = mutableSetOf<Long>()
        for (a in book.accounts) {
            val current = accounts.first { it.id == account(a.name, a.code) }
            if (current.id in empty) dao.upsertAccount(current.copy(name = a.name, initialCents = a.openingCents, deleted = a.deleted))
            else if (current.initialCents != a.openingCents || current.deleted != a.deleted) kept += current.id
        }
        for (h in book.holdings) {
            val accountId = account(h.account, h.accountCode)
            if (accountId in empty) dao.upsertHolding(Holding(0, accountId, h.symbol, h.name, h.quantity, h.investedCents))
            else kept += accountId
        }
        dao.insertTxns(toInsert)
        ImportResult(toInsert.size, skipped, newAccounts, newCategories, kept.size)
    }

    suspend fun exportBook(currency: String): List<app.tasca.io.Sheet> {
        val accounts = dao.accounts()
        val byId = accounts.associateBy { it.id }
        return LedgerSheets.write(
            exportRows(), currency,
            accounts.map { AccountSheetRow(it.name, it.initialCents, it.deleted, it.code) },
            dao.holdings().map {
                val a = byId.getValue(it.accountId)
                HoldingSheetRow(a.name, it.symbol, it.name, it.quantity, it.investedCents, a.code)
            },
        )
    }

    suspend fun exportRows(): List<SheetRow> {
        val accounts = dao.accounts().associateBy { it.id }
        val categories = dao.categories().associateBy { it.id }
        return dao.allTxns().sortedWith(compareByDescending<Txn> { it.date }.thenByDescending { it.id }).map {
            val to = it.toAccountId?.let(accounts::getValue)
            SheetRow(
                kind = it.kind,
                date = it.date,
                account = accounts.getValue(it.accountId).name,
                toAccount = to?.name,
                category = it.categoryId?.let { id -> categories.getValue(id).name },
                amountCents = it.amountCents,
                note = it.note,
                accountCode = accounts.getValue(it.accountId).code,
                toAccountCode = to?.code,
            )
        }
    }

    private fun dedupeKey(t: Txn) = listOf(t.kind, t.date, t.amountCents, t.accountId, t.toAccountId, t.categoryId, t.note.trim())

    companion object {
        @Volatile private var instance: Ledger? = null

        fun get(context: Context): Ledger = instance ?: synchronized(this) {
            instance ?: Ledger(
                Room.databaseBuilder(context.applicationContext, AppDb::class.java, "tasca.db").build()
            ).also { instance = it }
        }
    }
}
