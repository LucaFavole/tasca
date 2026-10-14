package app.tasca.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

private const val ACCOUNT_FILTER = "(:accountId IS NULL OR t.accountId = :accountId)"

@Dao
interface LedgerDao {
    @Query(
        """
        SELECT a.id, a.name, a.color, a.icon, a.initialCents, a.deleted, a.code,
          a.initialCents + COALESCE((
            SELECT SUM(CASE
              WHEN t.kind = 'INCOME' THEN t.amountCents
              WHEN t.kind = 'EXPENSE' THEN -t.amountCents
              WHEN t.accountId = a.id THEN -t.amountCents
              ELSE t.amountCents END)
            FROM txn t WHERE t.accountId = a.id OR t.toAccountId = a.id), 0) AS balance
        FROM account a ORDER BY a.deleted, a.id
        """
    )
    fun balances(): Flow<List<AccountBalance>>

    @Query("SELECT * FROM account ORDER BY deleted, id")
    suspend fun accounts(): List<Account>

    @Upsert suspend fun upsertAccount(a: Account): Long
    @Query("DELETE FROM account WHERE id = :id") suspend fun removeAccount(id: Long)
    @Query("UPDATE account SET deleted = 1 WHERE id = :id") suspend fun hideAccount(id: Long)
    @Query("SELECT COUNT(*) FROM txn WHERE accountId = :id OR toAccountId = :id") suspend fun accountUsage(id: Long): Int
    @Query("UPDATE recurring SET active = 0 WHERE accountId = :id OR toAccountId = :id") suspend fun pauseRecurringOf(id: Long)

    @Query("SELECT * FROM category ORDER BY kind, name COLLATE NOCASE")
    fun categoriesFlow(): Flow<List<Category>>

    @Query("SELECT * FROM category")
    suspend fun categories(): List<Category>

    @Query("SELECT * FROM category WHERE kind = :kind AND name = :name COLLATE NOCASE")
    suspend fun category(kind: CategoryKind, name: String): Category?

    @Query("SELECT * FROM holding WHERE id = :id") suspend fun holding(id: Long): Holding?
    @Query("SELECT * FROM holding") suspend fun holdings(): List<Holding>

    @Upsert suspend fun upsertCategory(c: Category): Long
    @Query("DELETE FROM category WHERE id = :id") suspend fun deleteCategory(id: Long)
    @Query("SELECT COUNT(*) FROM txn WHERE categoryId = :id") suspend fun categoryUsage(id: Long): Int
    @Query("SELECT COUNT(*) FROM recurring WHERE categoryId = :id") suspend fun categoryRecurring(id: Long): Int
    @Query("UPDATE txn SET categoryId = :to WHERE categoryId = :from") suspend fun moveCategory(from: Long, to: Long)

    @Upsert suspend fun upsertTxn(t: Txn): Long
    @Insert suspend fun insertTxns(t: List<Txn>)
    @Query("DELETE FROM txn WHERE id = :id") suspend fun deleteTxn(id: Long)
    @Query("SELECT * FROM txn WHERE id = :id") suspend fun txn(id: Long): Txn?
    @Query("SELECT * FROM txn") suspend fun allTxns(): List<Txn>

    @Query(
        """
        SELECT t.id, t.kind, t.amountCents, t.date, t.note, t.accountId, a.name AS accountName,
          t.toAccountId, b.name AS toAccountName, t.categoryId, c.name AS categoryName,
          c.color AS categoryColor, c.icon AS categoryIcon, t.recurringId
        FROM txn t JOIN account a ON a.id = t.accountId
          LEFT JOIN account b ON b.id = t.toAccountId
          LEFT JOIN category c ON c.id = t.categoryId
        WHERE t.date BETWEEN :from AND :to
          AND (:accountId IS NULL OR t.accountId = :accountId OR t.toAccountId = :accountId)
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:query = '' OR t.note LIKE '%' || :query || '%' OR c.name LIKE '%' || :query || '%'
               OR a.name LIKE '%' || :query || '%' OR b.name LIKE '%' || :query || '%')
        ORDER BY t.date DESC, t.id DESC
        LIMIT :limit
        """
    )
    fun txnRows(
        from: LocalDate, to: LocalDate, accountId: Long?, categoryId: Long?, query: String, limit: Int,
    ): Flow<List<TxnRow>>

    @Query(
        """
        SELECT c.id AS categoryId, c.name, c.color, c.icon, SUM(t.amountCents) AS totalCents
        FROM txn t JOIN category c ON c.id = t.categoryId
        WHERE t.kind = :kind AND t.date BETWEEN :from AND :to AND $ACCOUNT_FILTER
        GROUP BY c.id ORDER BY totalCents DESC
        """
    )
    fun categoryTotals(kind: TxnKind, from: LocalDate, to: LocalDate, accountId: Long?): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT strftime('%Y-%m', t.date * 86400, 'unixepoch') AS month, t.kind, SUM(t.amountCents) AS totalCents
        FROM txn t
        WHERE t.kind != 'TRANSFER' AND t.date BETWEEN :from AND :to AND $ACCOUNT_FILTER
        GROUP BY month, t.kind
        """
    )
    fun monthTotals(from: LocalDate, to: LocalDate, accountId: Long?): Flow<List<MonthTotal>>

    @Query(
        """
        SELECT strftime('%Y-%m', t.date * 86400, 'unixepoch') AS month, c.id AS categoryId, c.name, c.color, c.icon,
          SUM(t.amountCents) AS totalCents, COUNT(*) AS count
        FROM txn t JOIN category c ON c.id = t.categoryId
        WHERE t.kind = 'EXPENSE' AND t.date BETWEEN :from AND :to AND $ACCOUNT_FILTER
        GROUP BY month, c.id
        """
    )
    fun categoryMonths(from: LocalDate, to: LocalDate, accountId: Long?): Flow<List<app.tasca.domain.CategoryMonth>>

    /** Emits on every change to accounts, transactions, regular payments, holdings or prices; the value itself means nothing. */
    @Query("SELECT (SELECT COUNT(*) FROM account) + (SELECT COUNT(*) FROM txn) + (SELECT COUNT(*) FROM recurring) + (SELECT COUNT(*) FROM holding) + (SELECT COUNT(*) FROM quote)")
    fun ledgerChanges(): Flow<Int>

    @Query("SELECT * FROM recurring ORDER BY active DESC, name COLLATE NOCASE")
    fun recurringFlow(): Flow<List<Recurring>>

    @Query("SELECT * FROM recurring WHERE active = 1")
    suspend fun activeRecurring(): List<Recurring>

    @Query("SELECT * FROM recurring") suspend fun allRecurring(): List<Recurring>
    @Query("SELECT * FROM recurring WHERE id = :id") suspend fun recurring(id: Long): Recurring?
    @Upsert suspend fun upsertRecurring(r: Recurring): Long
    @Query("DELETE FROM recurring WHERE id = :id") suspend fun deleteRecurring(id: Long)

    @Query("SELECT * FROM budget") fun budgetsFlow(): Flow<List<Budget>>
    @Query("SELECT * FROM budget") suspend fun budgets(): List<Budget>
    @Upsert suspend fun upsertBudget(b: Budget)
    @Query("DELETE FROM budget WHERE categoryId = :categoryId") suspend fun deleteBudget(categoryId: Long)

    @Query("SELECT * FROM holding ORDER BY accountId, name COLLATE NOCASE") fun holdingsFlow(): Flow<List<Holding>>
    @Query("SELECT DISTINCT symbol FROM holding") suspend fun heldSymbols(): List<String>
    @Query("SELECT COUNT(*) FROM holding WHERE accountId = :id") suspend fun holdingCount(id: Long): Int
    @Upsert suspend fun upsertHolding(h: Holding)
    @Query("DELETE FROM holding WHERE id = :id") suspend fun deleteHolding(id: Long)

    @Query("SELECT * FROM quote") fun quotesFlow(): Flow<List<Quote>>
    @Query("SELECT * FROM quote") suspend fun quotes(): List<Quote>
    @Upsert suspend fun upsertQuotes(q: List<Quote>)

    @Query("DELETE FROM holding") suspend fun clearHoldings()
    @Query("DELETE FROM txn") suspend fun clearTxns()
    @Query("DELETE FROM recurring") suspend fun clearRecurring()
    @Query("DELETE FROM budget") suspend fun clearBudgets()
    @Query("DELETE FROM account") suspend fun clearAccounts()
    @Query("DELETE FROM category") suspend fun clearCategories()
}
