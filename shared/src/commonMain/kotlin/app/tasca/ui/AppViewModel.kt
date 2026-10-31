package app.tasca.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tasca.data.Account
import app.tasca.data.AccountBalance
import app.tasca.data.Budget
import app.tasca.data.Category
import app.tasca.data.Closing
import app.tasca.data.Holding
import app.tasca.data.Ledger
import app.tasca.data.Recurring
import app.tasca.data.Txn
import app.tasca.data.TxnKind
import app.tasca.domain.InsightsCalc
import app.tasca.domain.StatsRange
import app.tasca.domain.Money
import app.tasca.domain.Spoken
import app.tasca.domain.SpokenParser
import app.tasca.domain.TradeSide
import app.tasca.domain.Period
import app.tasca.domain.PeriodKind
import app.tasca.domain.isoCurrency
import app.tasca.domain.today
import app.tasca.io.LedgerSheets
import app.tasca.io.SymbolHit
import app.tasca.io.Yahoo
import app.tasca.io.Xlsx
import app.tasca.ui.theme.ColorTheme
import app.tasca.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.yearMonth

enum class HomeView { CATEGORIES, TRANSACTIONS }

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val ledger: Ledger, private val settings: Settings) : ViewModel() {
    private val dao = ledger.dao
    // Read before the first frame, so a chosen theme never starts as the system one.
    private val stored = runBlocking { settings.current() }

    private val _theme = MutableStateFlow(stored.theme)
    val theme = _theme.asStateFlow()
    private val _colors = MutableStateFlow(stored.colors)
    val colors = _colors.asStateFlow()
    private val _currency = MutableStateFlow(stored.currency)
    val currency = _currency.asStateFlow()
    private val _reminder = MutableStateFlow(stored.reminder)
    val reminder = _reminder.asStateFlow()
    private val _defaultAccounts = MutableStateFlow(stored.defaultAccounts)
    val defaultAccounts = _defaultAccounts.asStateFlow()

    val messages = MutableSharedFlow<Message>(extraBufferCapacity = 4)

    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val balances = dao.balances().state(emptyList())
    val categories = dao.categoriesFlow().state(emptyList())
    val recurring = dao.recurringFlow().state(emptyList())
    val budgets = dao.budgetsFlow().state(emptyList())

    /** Home and Stats look at one account or, when null, the total of all accounts counted in the total. */
    val selectedAccount = MutableStateFlow<Long?>(null)
    val homePeriod = MutableStateFlow(Period.of(PeriodKind.MONTH))
    val homeKind = MutableStateFlow(TxnKind.EXPENSE)
    val homeView = MutableStateFlow(HomeView.CATEGORIES)
    /** Narrows the transaction list to one category; set by tapping the category. */
    val homeCategory = MutableStateFlow<Long?>(null)
    val homeQuery = MutableStateFlow("")
    /** In the transaction list, only expenses or only income; null shows everything, transfers included. */
    val homeListKind = MutableStateFlow<TxnKind?>(null)

    val homeRows = combine(selectedAccount, homePeriod, homeCategory, homeQuery) { acc, p, cat, q ->
        dao.txnRows(p.start, p.end, acc, cat, q.trim(), 2000)
    }.flatMapLatest { it }.state(emptyList())

    /**
     * The account a new transaction of [kind] starts on: the one chosen in Settings, else "Main" for expenses and
     * "Risparmi" for income when such accounts exist, else the first account.
     */
    fun defaultAccount(kind: TxnKind): Long? = defaultAccount(_defaultAccounts.value, balances.value, kind)

    fun setDefaultAccount(kind: TxnKind, id: Long) {
        save { it.copy(defaultAccounts = it.defaultAccounts.with(kind, id)) }
        _defaultAccounts.value = _defaultAccounts.value.with(kind, id)
    }

    /** Whether Home shows anything other than its default: all accounts, this month, categories. */
    val homeNarrowed = combine(
        combine(selectedAccount, homePeriod, ::Pair), homeView, homeCategory, homeListKind,
    ) { (acc, p), v, cat, k -> acc != null || p != Period.of(PeriodKind.MONTH) || v != HomeView.CATEGORIES || cat != null || k != null }
        .state(false)

    /** Undoes the narrowest Home filter, so Back steps out of filters one at a time before leaving the app. */
    fun homeBack() {
        when {
            homeCategory.value != null -> homeCategory.value = null
            homeListKind.value != null -> homeListKind.value = null
            homeView.value != HomeView.CATEGORIES -> homeView.value = HomeView.CATEGORIES
            else -> {
                selectedAccount.value = null
                homePeriod.value = Period.of(PeriodKind.MONTH)
            }
        }
    }

    /** Shows one account's whole history as a list, as "See transactions" on Accounts asks. */
    fun showTransactions(accountId: Long) {
        selectedAccount.value = accountId
        homePeriod.value = Period.of(PeriodKind.ALL)
        homeCategory.value = null
        homeQuery.value = ""
        homeListKind.value = null
        homeView.value = HomeView.TRANSACTIONS
    }

    val homeTotals = combine(selectedAccount, homePeriod, homeKind, ::Triple)
        .flatMapLatest { (acc, p, kind) -> dao.categoryTotals(kind, p.start, p.end, acc) }
        .state(emptyList())

    val homeSummary = combine(selectedAccount, homePeriod, ::Pair)
        .flatMapLatest { (acc, p) -> dao.monthTotals(p.start, p.end, acc) }
        .state(emptyList())

    val monthBudgetSpend = selectedAccount
        .flatMapLatest { acc ->
            val p = Period.of(PeriodKind.MONTH)
            dao.categoryTotals(TxnKind.EXPENSE, p.start, p.end, acc)
        }
        .state(emptyList())

    /** How many complete months the Stats averages look back over. */
    val statsRange = MutableStateFlow(StatsRange.last(6))
    val statsTotals = combine(selectedAccount, statsRange, ::Pair)
        .flatMapLatest { (acc, r) -> dao.monthTotals(r.from.firstDay, r.to.lastDay, acc) }
        .state(emptyList())

    val insights = combine(selectedAccount, statsRange, ::Pair)
        .flatMapLatest { (acc, r) ->
            val now = today().yearMonth
            val from = minOf(r.from, now).firstDay
            val to = maxOf(r.to, now).lastDay
            combine(
                dao.monthTotals(from, to, acc),
                dao.categoryMonths(from, to, acc),
                dao.balances(),
                dao.holdingsFlow(),
            ) { months, cats, balances, holdings ->
                val liquid = if (acc == null) balances.sumOf { it.balanceCents } - holdings.sumOf { it.investedCents } else null
                InsightsCalc.compute(
                    today(), r.completeMonths(now),
                    months.filter { it.kind == TxnKind.INCOME }.associate { it.month to it.totalCents },
                    months.filter { it.kind == TxnKind.EXPENSE }.associate { it.month to it.totalCents },
                    cats, liquid,
                )
            }
        }
        .state(null)

    val holdings = dao.holdingsFlow().state(emptyList())
    val quotes = dao.quotesFlow().state(emptyList())
    val refreshing = MutableStateFlow(false)

    /** Fetches prices for every held symbol. Called on opening the Investments page and from its refresh button. */
    fun refreshQuotes() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { ledger.refreshQuotes(_currency.value) } }
                .onSuccess { failed -> if (failed.isNotEmpty()) say("No price for ${failed.joinToString()}") }
                .onFailure { say("Couldn't reach Yahoo Finance. Showing the last prices.") }
            refreshing.value = false
        }
    }

    suspend fun searchSymbols(query: String): List<SymbolHit> = withContext(Dispatchers.IO) { Yahoo.search(query) }

    fun saveHolding(h: Holding) {
        io { dao.upsertHolding(h) }.invokeOnCompletion { refreshQuotes() }
    }
    fun deleteHolding(id: Long) = io { dao.deleteHolding(id) }

    /** Capital gains tax rate used to prefill sales; 26% is the usual Italian rate on ETFs and stocks. */
    var taxRatePct: Double = stored.taxRatePct
        set(v) {
            field = v.toFloat().toDouble()
            save { it.copy(taxRatePct = v) }
        }

    fun trade(holdingId: Long, side: TradeSide, shares: Double, unitPrice: Double, feeCents: Long, taxCents: Long = 0) =
        io {
            ledger.trade(holdingId, side, shares, unitPrice, feeCents, taxCents, today())
            say(if (side == TradeSide.BUY) "Purchase recorded" else "Sale recorded")
        }

    suspend fun livePrice(symbol: String): Pair<Double, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val p = Yahoo.price(symbol)
            p.price * Yahoo.rate(p.currency, _currency.value) to p.currency
        }.getOrNull()
    }

    init {
        Money.currency = _currency.value
        viewModelScope.launch {
            ledger.ensureSeeded()
            ledger.postDueRecurring()
        }
    }

    fun setTheme(mode: ThemeMode) {
        save { it.copy(theme = mode) }
        _theme.value = mode
    }

    fun setColors(colors: ColorTheme) {
        save { it.copy(colors = colors) }
        _colors.value = colors
    }

    fun setCurrency(code: String) {
        val c = isoCurrency(code) ?: return say("Unknown currency $code")
        save { it.copy(currency = c) }
        Money.currency = c
        _currency.value = c
    }

    fun setReminder(on: Boolean) {
        save { it.copy(reminder = on) }
        _reminder.value = on
    }

    /** True only the first time: the launch prompt for notifications is shown once. */
    suspend fun firstNotificationPrompt(): Boolean = !settings.update { it.copy(askedNotifications = true) }.askedNotifications

    private fun save(change: (Prefs) -> Prefs) {
        viewModelScope.launch { settings.update(change) }
    }

    private fun say(msg: String) { messages.tryEmit(Message(msg)) }

    private fun io(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { block() } }.onFailure { say(it.message ?: "Something went wrong") }
    }

    /** A sentence heard by the voice button, read by the keypad when it opens. */
    var spoken: Spoken? = null

    suspend fun prepareSpoken(text: String) {
        spoken = withContext(Dispatchers.IO) { SpokenParser.parse(text, dao.categories(), today()) }
    }

    suspend fun txn(id: Long): Txn? = withContext(Dispatchers.IO) { dao.txn(id) }
    suspend fun recurringById(id: Long): Recurring? = withContext(Dispatchers.IO) { dao.recurring(id) }

    fun saveTxn(t: Txn) = io { dao.upsertTxn(t) }
    fun deleteTxn(id: Long) = io { dao.deleteTxn(id); say("Deleted") }

    /** Deletes from a swipe, offering Undo, which puts the same transaction back. */
    fun deleteTxnUndoable(id: Long) = io {
        val t = dao.txn(id) ?: return@io
        dao.deleteTxn(id)
        messages.tryEmit(Message("Deleted", "Undo") { saveTxn(t) })
    }

    fun saveAccount(a: Account) = io { ledger.saveAccount(a) }
    fun deleteAccount(id: Long, closing: Closing? = null) = io {
        ledger.deleteAccount(id, closing)
        if (selectedAccount.value == id) selectedAccount.value = null
    }

    fun saveCategory(c: Category) = io {
        if (categories.value.any { it.id != c.id && it.kind == c.kind && it.name.equals(c.name, true) }) {
            say("A category called ${c.name} already exists")
        } else dao.upsertCategory(c)
    }
    suspend fun categoryUsage(id: Long): CategoryUsage =
        withContext(Dispatchers.IO) { CategoryUsage(dao.categoryUsage(id), dao.categoryRecurring(id)) }
    fun deleteCategory(id: Long, replacement: Long?) = io { ledger.deleteCategory(id, replacement) }

    fun saveRecurring(r: Recurring) = io {
        dao.upsertRecurring(r)
        val n = ledger.postDueRecurring()
        if (n > 0) say("Recorded $n payment${if (n == 1) "" else "s"} due up to today")
    }
    fun deleteRecurring(id: Long) = io { dao.deleteRecurring(id) }

    fun setBudget(categoryId: Long, cents: Long?) = io {
        if (cents == null || cents <= 0) dao.deleteBudget(categoryId) else dao.upsertBudget(Budget(categoryId, cents))
    }

    fun eraseAll() = io {
        ledger.eraseAll()
        ledger.ensureSeeded()
        selectedAccount.value = null
        say("All data erased")
    }

    fun importExcel(read: () -> ByteArray) = io {
        val sheets = Xlsx.read(read())
        val parsed = LedgerSheets.read(sheets)
        val r = ledger.import(parsed)
        if (r.fresh) applySettings(parsed.config.settings)
        val extra = if (parsed.problems.isEmpty()) "" else ", ${parsed.problems.size} rows unreadable"
        val kept = if (r.keptAccounts == 0) "" else ". ${r.keptAccounts} accounts already in use kept their current balance and investments"
        val config = listOfNotNull(
            "${r.newRecurring} regular payments".takeIf { r.newRecurring > 0 },
            "${r.newBudgets} budgets".takeIf { r.newBudgets > 0 },
        ).joinToString("") { ", $it" }
        say("Imported ${r.added} (${r.skipped} already present$extra). New: ${r.newAccounts} accounts, ${r.newCategories} categories$config$kept")
    }

    /** [write] returns false when the user backed out of saving. */
    fun exportExcel(write: suspend (ByteArray) -> Boolean) = io {
        val settings = mapOf(
            "currency" to _currency.value, "theme" to _theme.value.name, "colors" to _colors.value.name, "taxRate" to taxRatePct.toString(),
            "reminder" to _reminder.value.toString(),
        )
        val book = ledger.exportBook(_currency.value, settings)
        if (write(Xlsx.write(book))) say("Exported ${book.take(3).sumOf { it.rows.size - 2 }} transactions, with accounts, regular payments, budgets and settings")
    }

    /** Settings restored from a backup onto a phone that had no data yet; unknown or broken values are skipped. */
    private suspend fun applySettings(settings: Map<String, String>) = withContext(Dispatchers.Main) {
        settings["currency"]?.let(::setCurrency)
        settings["theme"]?.let { t -> ThemeMode.entries.firstOrNull { it.name == t } }?.let(::setTheme)
        settings["colors"]?.let { t -> ColorTheme.entries.firstOrNull { it.name == t } }?.let(::setColors)
        settings["taxRate"]?.toDoubleOrNull()?.takeIf { it in 0.0..100.0 }?.let { taxRatePct = it }
        settings["reminder"]?.toBooleanStrictOrNull()?.let(::setReminder)
    }

    fun exportFileName(): String = "tasca_${today()}.xlsx"
}

/** What deleting a category touches: [txns] must move elsewhere, [recurring] payments are deleted with it. */
data class CategoryUsage(val txns: Int, val recurring: Int)

/** The default account of [kind] among [accounts]; the balance widget uses it too, so both agree. */
fun defaultAccount(chosen: DefaultAccounts, accounts: List<AccountBalance>, kind: TxnKind): Long? {
    val active = accounts.filter { !it.deleted }
    fun named(name: String) = active.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
    val picked = chosen.of(kind)?.takeIf { id -> active.any { it.id == id } }
    val expense = named("Main") ?: active.firstOrNull()?.id
    return picked ?: if (kind == TxnKind.INCOME) named("Risparmi") ?: expense else expense
}

/** A snackbar line; [action] runs when its [actionLabel] is tapped. */
class Message(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)
