package app.tasca.io

import app.tasca.data.CategoryKind
import app.tasca.data.TxnKind
import app.tasca.domain.Frequency
import app.tasca.domain.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.math.abs

/** One transaction as it appears in a spreadsheet: accounts and categories by name. */
data class SheetRow(
    val kind: TxnKind,
    val date: LocalDate,
    val account: String,
    val toAccount: String? = null,
    val category: String? = null,
    val amountCents: Long,
    val note: String = "",
    val accountCode: String? = null,
    val toAccountCode: String? = null,
)

/** An account's opening balance, which no transaction carries. */
data class AccountSheetRow(val name: String, val openingCents: Long, val deleted: Boolean, val code: String? = null)

data class HoldingSheetRow(
    val account: String, val symbol: String, val name: String, val quantity: Double, val investedCents: Long, val accountCode: String? = null,
)

/** A regular payment with accounts and category by name; [posted] keeps a restore from posting past occurrences again. */
data class RecurringSheetRow(
    val name: String,
    val kind: TxnKind,
    val amountCents: Long,
    val account: String,
    val accountCode: String?,
    val toAccount: String?,
    val toAccountCode: String?,
    val category: String?,
    val frequency: Frequency,
    val every: Int,
    val start: LocalDate,
    val end: LocalDate?,
    val posted: Int,
    val active: Boolean,
)

data class CategorySheetRow(val name: String, val kind: CategoryKind, val color: Int, val icon: String)

/** A monthly limit on an expense category. */
data class BudgetSheetRow(val category: String, val monthlyCents: Long)

/** Everything a backup holds besides the transactions; empty in files from the previous app. */
data class Config(
    val accounts: List<AccountSheetRow> = emptyList(),
    val holdings: List<HoldingSheetRow> = emptyList(),
    val recurring: List<RecurringSheetRow> = emptyList(),
    val categories: List<CategorySheetRow> = emptyList(),
    val budgets: List<BudgetSheetRow> = emptyList(),
    /** App settings by key, e.g. "currency" to "EUR". */
    val settings: Map<String, String> = emptyMap(),
)

data class ParsedWorkbook(val rows: List<SheetRow>, val problems: List<String>, val config: Config = Config())

/**
 * The workbook layout: one sheet each for expenses, income and transfers, with the same headers the
 * previous money manager exported, so files move both ways. Reading also accepts Italian headers and a
 * single sheet with a type column or signed amounts.
 */
object LedgerSheets {
    private val TXN_HEADER = listOf(
        "Date and time", "Category", "Account", "Amount in default currency", "Default currency",
        "Amount in account currency", "Account currency", "Transaction amount in transaction currency",
        "Transaction currency", "Tags", "Comment", "Account code",
    )
    private val TRANSFER_HEADER = listOf(
        "Date and time", "Outgoing", "Incoming", "Amount in outgoing currency", "Outgoing currency",
        "Amount in incoming currency", "Incoming currency", "Comment", "Outgoing code", "Incoming code",
    )

    private enum class Field(vararg val names: String) {
        DATE("date and time", "date", "data", "giorno"),
        TYPE("type", "tipo"),
        CATEGORY("category", "categoria"),
        ACCOUNT("account", "conto"),
        FROM("outgoing", "from account", "from", "conto origine", "da"),
        TO("incoming", "to account", "to", "conto destinazione", "a"),
        AMOUNT(
            "amount in account currency", "amount in outgoing currency", "amount in default currency",
            "amount", "importo", "valore",
        ),
        NOTE("comment", "note", "notes", "commento", "nota", "description", "descrizione"),
        ACCOUNT_CODE("account code"),
        FROM_CODE("outgoing code"),
        TO_CODE("incoming code"),
    }

    private val ACCOUNTS_HEADER = listOf("Account", "Opening balance", "Deleted", "Code")
    private val HOLDINGS_HEADER = listOf("Account", "Symbol", "Name", "Shares", "Total paid", "Account code")

    private val RECURRING_HEADER = listOf(
        "Name", "Type", "Amount", "Account", "Account code", "To account", "To account code", "Category",
        "Frequency", "Every", "Start", "End", "Already posted", "Active",
    )
    private val CATEGORIES_HEADER = listOf("Category", "Type", "Color", "Icon")
    private val BUDGETS_HEADER = listOf("Category", "Monthly budget")
    private val SETTINGS_HEADER = listOf("Setting", "Value")

    /** Sheets that hold configuration, never transactions, whatever their cells say. */
    private val CONFIG_SHEETS = setOf("accounts", "investments", "regular payments", "categories", "budgets", "settings")

    private fun table(name: String, header: List<String>, rows: List<List<Cell>>) =
        Sheet(name, listOf(listOf(Cell.Text(name.lowercase())), header.map { Cell.Text(it) }) + rows)

    private fun text(s: String?) = Cell.Text(s.orEmpty())
    private fun yes(b: Boolean) = Cell.Text(if (b) "yes" else "")
    private fun money(cents: Long) = Cell.Num(Money.toDouble(cents))

    fun write(rows: List<SheetRow>, currency: String, config: Config = Config()): List<Sheet> {
        fun txnSheet(name: String, title: String, kind: TxnKind) = Sheet(
            name,
            listOf(listOf(Cell.Text(title)), TXN_HEADER.map { Cell.Text(it) }) +
                rows.filter { it.kind == kind }.map {
                    val amount = Cell.Num(Money.toDouble(it.amountCents))
                    listOf(
                        Cell.Date(it.date), Cell.Text(it.category.orEmpty()), Cell.Text(it.account),
                        amount, Cell.Text(currency), amount, Cell.Text(currency),
                        Cell.Empty, Cell.Empty, Cell.Empty, Cell.Text(it.note), Cell.Text(it.accountCode.orEmpty()),
                    )
                },
        )
        val transfers = Sheet(
            "Transfers",
            listOf(listOf(Cell.Text("transfers list")), TRANSFER_HEADER.map { Cell.Text(it) }) +
                rows.filter { it.kind == TxnKind.TRANSFER }.map {
                    listOf(
                        Cell.Date(it.date), Cell.Text(it.account), Cell.Text(it.toAccount.orEmpty()),
                        Cell.Num(Money.toDouble(it.amountCents)), Cell.Text(currency), Cell.Empty, Cell.Empty, Cell.Text(it.note),
                        Cell.Text(it.accountCode.orEmpty()), Cell.Text(it.toAccountCode.orEmpty()),
                    )
                },
        )
        return listOf(
            txnSheet("Expenses", "expenses list", TxnKind.EXPENSE),
            txnSheet("Income", "income list", TxnKind.INCOME),
            transfers,
            table("Accounts", ACCOUNTS_HEADER, config.accounts.map { listOf(text(it.name), money(it.openingCents), yes(it.deleted), text(it.code)) }),
            table("Investments", HOLDINGS_HEADER, config.holdings.map {
                listOf(text(it.account), text(it.symbol), text(it.name), Cell.Num(it.quantity), money(it.investedCents), text(it.accountCode))
            }),
            table("Regular payments", RECURRING_HEADER, config.recurring.map {
                listOf(
                    text(it.name), text(it.kind.name), money(it.amountCents), text(it.account), text(it.accountCode),
                    text(it.toAccount), text(it.toAccountCode), text(it.category), text(it.frequency.name), Cell.Num(it.every.toDouble()),
                    Cell.Date(it.start), it.end?.let { e -> Cell.Date(e) } ?: Cell.Empty, Cell.Num(it.posted.toDouble()), yes(it.active),
                )
            }),
            table("Categories", CATEGORIES_HEADER, config.categories.map {
                listOf(text(it.name), text(it.kind.name), text("#" + it.color.toUInt().toString(16).uppercase().padStart(8, '0')), text(it.icon))
            }),
            table("Budgets", BUDGETS_HEADER, config.budgets.map { listOf(text(it.category), money(it.monthlyCents)) }),
            table("Settings", SETTINGS_HEADER, config.settings.map { (k, v) -> listOf(text(k), text(v)) }),
        )
    }

    /** Rows of a sheet after the header row whose first cell is [firstHeader]; empty when the sheet is absent. */
    private fun body(sheets: List<Sheet>, name: String, firstHeader: String): List<List<Cell>> {
        val sheet = sheets.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return emptyList()
        val h = sheet.rows.indexOfFirst { (it.firstOrNull() as? Cell.Text)?.value == firstHeader }
        return if (h < 0) emptyList() else sheet.rows.drop(h + 1).filter { r -> r.any { it != Cell.Empty } }
    }

    private fun Cell.text() = when (this) { is Cell.Text -> value.trim(); is Cell.Num -> value.toString(); else -> "" }
    private fun Cell.num(): Double? = when (this) { is Cell.Num -> value; is Cell.Text -> value.replace(',', '.').toDoubleOrNull(); else -> null }

    fun read(sheets: List<Sheet>): ParsedWorkbook {
        val rows = mutableListOf<SheetRow>()
        val problems = mutableListOf<String>()
        for (sheet in sheets) {
            if (sheet.name.lowercase() in CONFIG_SHEETS) continue
            val headerIndex = sheet.rows.indexOfFirst { r -> r.any { c -> c is Cell.Text && Field.DATE.matches(c.value) } }
            if (headerIndex < 0) continue
            val header = sheet.rows[headerIndex].map { (it as? Cell.Text)?.value?.trim()?.lowercase().orEmpty() }
            val cols = Field.entries.associateWith { f -> f.names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } }
            val sheetKind = kindOf(sheet.name)
                ?: if (cols[Field.FROM] != null && cols[Field.TO] != null) TxnKind.TRANSFER else null

            sheet.rows.drop(headerIndex + 1).forEachIndexed { i, cells ->
                if (cells.all { it == Cell.Empty || (it is Cell.Text && it.value.isBlank()) }) return@forEachIndexed
                fun cell(f: Field): Cell = cols[f]?.let { cells.getOrNull(it) } ?: Cell.Empty
                fun text(f: Field): String = when (val c = cell(f)) {
                    is Cell.Text -> c.value.trim()
                    is Cell.Num -> c.value.toString()
                    else -> ""
                }
                val where = "${sheet.name} row ${headerIndex + i + 2}"
                val date = dateOf(cell(Field.DATE)) ?: return@forEachIndexed problems.plusAssign("$where: unreadable date")
                val signed = amountOf(cell(Field.AMOUNT)) ?: return@forEachIndexed problems.plusAssign("$where: unreadable amount")
                if (signed == 0L) return@forEachIndexed problems.plusAssign("$where: zero amount skipped")
                val kind = sheetKind ?: kindOf(text(Field.TYPE)) ?: if (signed < 0) TxnKind.EXPENSE else TxnKind.INCOME
                val note = text(Field.NOTE)
                val row = if (kind == TxnKind.TRANSFER) {
                    val from = text(Field.FROM).ifEmpty { text(Field.ACCOUNT) }
                    val to = text(Field.TO)
                    val fromCode = text(Field.FROM_CODE).ifEmpty { text(Field.ACCOUNT_CODE) }
                    val toCode = text(Field.TO_CODE)
                    if (from.isEmpty() || to.isEmpty() || (from.equals(to, ignoreCase = true) && fromCode == toCode)) {
                        return@forEachIndexed problems.plusAssign("$where: transfer needs two different accounts")
                    }
                    SheetRow(kind, date, from, to, null, abs(signed), note, fromCode.ifEmpty { null }, toCode.ifEmpty { null })
                } else {
                    val account = text(Field.ACCOUNT).ifEmpty { "Main" }
                    SheetRow(
                        kind, date, account, null, text(Field.CATEGORY).ifEmpty { "Altro" }, abs(signed), note,
                        text(Field.ACCOUNT_CODE).ifEmpty { null },
                    )
                }
                rows += row
            }
        }
        if (rows.isEmpty() && problems.isEmpty()) problems += "No sheet with a Date column was found"
        val accounts = body(sheets, "Accounts", "Account").mapNotNull { r ->
            val name = r.getOrNull(0)?.text().orEmpty()
            val opening = r.getOrNull(1)?.num()
            if (name.isEmpty() || opening == null) null
            else AccountSheetRow(name, Money.fromDouble(opening), r.getOrNull(2)?.text() == "yes", r.getOrNull(3)?.text()?.ifEmpty { null })
        }
        val holdings = body(sheets, "Investments", "Account").mapNotNull { r ->
            val qty = r.getOrNull(3)?.num()
            val paid = r.getOrNull(4)?.num()
            val account = r.getOrNull(0)?.text().orEmpty()
            val symbol = r.getOrNull(1)?.text().orEmpty()
            if (account.isEmpty() || symbol.isEmpty() || qty == null || paid == null) null
            else HoldingSheetRow(
                account, symbol, r.getOrNull(2)?.text().orEmpty().ifEmpty { symbol }, qty, Money.fromDouble(paid),
                r.getOrNull(5)?.text()?.ifEmpty { null },
            )
        }
        val recurring = body(sheets, "Regular payments", "Name").mapIndexedNotNull { i, r ->
            fun at(c: Int) = r.getOrNull(c) ?: Cell.Empty
            fun opt(c: Int) = at(c).text().ifEmpty { null }
            val row = runCatching {
                RecurringSheetRow(
                    name = at(0).text(),
                    kind = TxnKind.valueOf(at(1).text()),
                    amountCents = Money.fromDouble(at(2).num()!!),
                    account = opt(3)!!,
                    accountCode = opt(4), toAccount = opt(5), toAccountCode = opt(6), category = opt(7),
                    frequency = Frequency.valueOf(at(8).text()),
                    every = at(9).num()!!.toInt(),
                    start = dateOf(at(10))!!,
                    end = dateOf(at(11)),
                    posted = at(12).num()!!.toInt(),
                    active = at(13).text() == "yes",
                )
            }.getOrNull()
            val valid = row != null && row.amountCents > 0 && row.every > 0 && row.posted >= 0 &&
                if (row.kind == TxnKind.TRANSFER) row.toAccount != null else row.category != null
            if (!valid) problems += "Regular payments row ${i + 3}: unreadable"
            row.takeIf { valid }
        }
        val categories = body(sheets, "Categories", "Category").mapNotNull { r ->
            val name = r.getOrNull(0)?.text().orEmpty()
            val kind = runCatching { CategoryKind.valueOf(r.getOrNull(1)?.text().orEmpty()) }.getOrNull()
            val color = r.getOrNull(2)?.text()?.removePrefix("#")?.toLongOrNull(16)?.toInt()
            val icon = r.getOrNull(3)?.text().orEmpty()
            if (name.isEmpty() || kind == null || color == null || icon.isEmpty()) null else CategorySheetRow(name, kind, color, icon)
        }
        val budgets = body(sheets, "Budgets", "Category").mapNotNull { r ->
            val name = r.getOrNull(0)?.text().orEmpty()
            val limit = r.getOrNull(1)?.num()
            if (name.isEmpty() || limit == null || limit <= 0) null else BudgetSheetRow(name, Money.fromDouble(limit))
        }
        val settings = body(sheets, "Settings", "Setting").mapNotNull { r ->
            r.getOrNull(0)?.text()?.ifEmpty { null }?.let { it to r.getOrNull(1)?.text().orEmpty() }
        }.toMap()
        return ParsedWorkbook(rows, problems, Config(accounts, holdings, recurring, categories, budgets, settings))
    }

    private fun Field.matches(s: String) = names.contains(s.trim().lowercase())

    private fun kindOf(name: String): TxnKind? = name.trim().lowercase().let { n ->
        when {
            n.startsWith("expense") || n.startsWith("spes") || n.startsWith("uscit") -> TxnKind.EXPENSE
            n.startsWith("income") || n.startsWith("entrat") -> TxnKind.INCOME
            n.startsWith("transfer") || n.startsWith("trasferim") || n.startsWith("giroconto") -> TxnKind.TRANSFER
            else -> null
        }
    }

    private fun dateOf(c: Cell): LocalDate? = when (c) {
        is Cell.Date -> c.value
        is Cell.Num -> Xlsx.serialToDate(c.value)
        is Cell.Text -> parseDate(c.value.trim().split(' ', 'T').first())
        Cell.Empty -> null
    }

    private fun amountOf(c: Cell): Long? = when (c) {
        is Cell.Num -> Money.fromDouble(c.value)
        is Cell.Text -> parseAmount(c.value)
        else -> null
    }

    /** Accepts "1.234,56", "1,234.56", "12,5", "-7" and a currency sign. */
    fun parseAmount(raw: String): Long? {
        var s = raw.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }
        if (s.isEmpty()) return null
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        s = if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
        return s.toDoubleOrNull()?.let(Money::fromDouble)
    }
}

/**
 * A date written as yyyy-MM-dd, d/M/yyyy, d.M.yyyy, d-M-yyyy, M/d/yyyy or yyyy/M/d, tried in that order.
 * Reads each layout as java.time's DateTimeFormatter.ofPattern does by default: a year has exactly four digits,
 * or a plus sign and more, and a day past the end of the month moves back to its last day (31/2 is 28/2).
 */
internal fun parseDate(text: String): LocalDate? = DATE_LAYOUTS.firstNotNullOfOrNull { it.parse(text) }

/** [fields] in order: Y year, M month, D day, separated by [separator]; [padded] month and day take exactly two digits. */
private class DateLayout(val fields: String, val separator: Char, val padded: Boolean = false) {
    fun parse(text: String): LocalDate? {
        val values = HashMap<Char, Long>()
        var pos = 0
        for ((i, field) in fields.withIndex()) {
            if (i > 0) {
                if (text.getOrNull(pos) != separator) return null
                pos++
            }
            val plus = field == 'Y' && text.getOrNull(pos) == '+'
            if (plus) pos++
            val from = pos
            while (pos < text.length && text[pos] in '0'..'9' && pos - from < 19) pos++
            val width = pos - from
            val valid = when {
                field == 'Y' -> if (plus) width > 4 else width == 4
                padded -> width == 2
                else -> width >= 1
            }
            if (!valid) return null
            values[field] = text.substring(from, pos).toLongOrNull() ?: return null
        }
        if (pos != text.length) return null
        val year = values.getValue('Y')
        val month = values.getValue('M')
        val day = values.getValue('D')
        if (year !in 1..999_999_999 || month !in 1..12 || day !in 1..31) return null
        val days = YearMonth(year.toInt(), month.toInt()).numberOfDays
        return LocalDate(year.toInt(), month.toInt(), minOf(day.toInt(), days))
    }
}

private val DATE_LAYOUTS = listOf(
    DateLayout("YMD", '-', padded = true), DateLayout("DMY", '/'), DateLayout("DMY", '.'),
    DateLayout("DMY", '-'), DateLayout("MDY", '/'), DateLayout("YMD", '/'),
)
