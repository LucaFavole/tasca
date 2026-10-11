package app.tasca.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import app.tasca.domain.Frequency
import java.time.LocalDate

enum class TxnKind { EXPENSE, INCOME, TRANSFER }

enum class CategoryKind { EXPENSE, INCOME }

@Entity(tableName = "account")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val icon: String = "wallet",
    val initialCents: Long = 0,
    /** A deleted account is hidden everywhere, but its transactions stay in the history and in the Total. */
    val deleted: Boolean = false,
)

@Entity(tableName = "category", indices = [Index(value = ["kind", "name"], unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    val color: Int,
    val icon: String,
)

/**
 * One money movement. Amount is always positive; [kind] gives the direction.
 * A transfer has [toAccountId] and no category, any other kind has a category and no [toAccountId].
 */
@Entity(
    tableName = "txn",
    foreignKeys = [
        ForeignKey(Account::class, ["id"], ["accountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Account::class, ["id"], ["toAccountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("accountId"), Index("toAccountId"), Index("categoryId"), Index("date")],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: TxnKind,
    val amountCents: Long,
    val date: LocalDate,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val recurringId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    init {
        require(amountCents > 0) { "amount must be positive" }
        if (kind == TxnKind.TRANSFER) {
            require(toAccountId != null && categoryId == null && toAccountId != accountId) { "invalid transfer" }
        } else {
            require(categoryId != null && toAccountId == null) { "invalid $kind" }
        }
    }
}

@Entity(
    tableName = "recurring",
    foreignKeys = [
        ForeignKey(Account::class, ["id"], ["accountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Account::class, ["id"], ["toAccountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("accountId"), Index("toAccountId"), Index("categoryId")],
)
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: TxnKind,
    val amountCents: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val frequency: Frequency,
    val every: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    /** How many occurrences were already posted as transactions. */
    val posted: Int = 0,
    val active: Boolean = true,
) {
    val nextDate: LocalDate get() = frequency.occurrence(startDate, every, posted)
    val finished: Boolean get() = endDate != null && nextDate.isAfter(endDate)

    fun toTxn(): Txn = Txn(
        kind = kind, amountCents = amountCents, date = nextDate, accountId = accountId,
        toAccountId = toAccountId, categoryId = categoryId, note = name, recurringId = id,
    )
}

@Entity(
    tableName = "budget",
    foreignKeys = [ForeignKey(Category::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE)],
)
data class Budget(
    @PrimaryKey val categoryId: Long,
    val monthlyCents: Long,
)

/** Shares of one instrument held in an account. [investedCents] is what was paid in total, in the app currency. */
@Entity(
    tableName = "holding",
    foreignKeys = [ForeignKey(Account::class, ["id"], ["accountId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("accountId")],
)
data class Holding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val symbol: String,
    val name: String,
    val quantity: Double,
    val investedCents: Long,
)

/** Last fetched price of a symbol, with the rate that converts it into the app currency. */
@Entity(tableName = "quote")
data class Quote(
    @PrimaryKey val symbol: String,
    val price: Double,
    val previousClose: Double?,
    val currency: String,
    val rateToApp: Double,
    val fetchedAt: Long,
) {
    fun valueCents(quantity: Double): Long = Math.round(quantity * price * rateToApp * 100)
}

data class AccountBalance(
    val id: Long,
    val name: String,
    val color: Int,
    val icon: String,
    val initialCents: Long,
    val deleted: Boolean,
    @ColumnInfo(name = "balance") val balanceCents: Long,
) {
    fun toAccount() = Account(id, name, color, icon, initialCents, deleted)
}

data class CategoryTotal(
    val categoryId: Long,
    val name: String,
    val color: Int,
    val icon: String,
    val totalCents: Long,
)

data class MonthTotal(val month: String, val kind: TxnKind, val totalCents: Long)

/** A transaction joined with the names it shows in lists. */
data class TxnRow(
    val id: Long,
    val kind: TxnKind,
    val amountCents: Long,
    val date: LocalDate,
    val note: String,
    val accountId: Long,
    val accountName: String,
    val toAccountId: Long?,
    val toAccountName: String?,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryColor: Int?,
    val categoryIcon: String?,
    val recurringId: Long?,
)

class Converters {
    @TypeConverter fun dateToLong(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun longToDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)
    @TypeConverter fun freqToString(f: Frequency): String = f.name
    @TypeConverter fun stringToFreq(s: String): Frequency = Frequency.valueOf(s)
}

/** Market value against cost for a set of holdings; holdings without a price yet count at cost. */
data class Portfolio(val valueCents: Long, val investedCents: Long, val unpriced: Int) {
    val gainCents: Long get() = valueCents - investedCents
    val gainPct: Double? get() = if (investedCents > 0) gainCents * 100.0 / investedCents else null

    companion object {
        fun of(holdings: List<Holding>, quotes: Map<String, Quote>): Portfolio {
            var value = 0L
            var unpriced = 0
            for (h in holdings) {
                val q = quotes[h.symbol]
                if (q == null) unpriced++
                value += q?.valueCents(h.quantity) ?: h.investedCents
            }
            return Portfolio(value, holdings.sumOf { it.investedCents }, unpriced)
        }
    }
}
