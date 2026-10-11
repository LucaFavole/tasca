package app.tasca.io

import app.tasca.data.TxnKind
import app.tasca.domain.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

data class ParsedWorkbook(
    val rows: List<SheetRow>,
    val problems: List<String>,
    val accounts: List<AccountSheetRow> = emptyList(),
    val holdings: List<HoldingSheetRow> = emptyList(),
)

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

    fun write(
        rows: List<SheetRow>,
        currency: String,
        accounts: List<AccountSheetRow> = emptyList(),
        holdings: List<HoldingSheetRow> = emptyList(),
    ): List<Sheet> {
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
            Sheet("Accounts", listOf(listOf(Cell.Text("accounts")), ACCOUNTS_HEADER.map { Cell.Text(it) }) + accounts.map {
                listOf(
                    Cell.Text(it.name), Cell.Num(Money.toDouble(it.openingCents)), Cell.Text(if (it.deleted) "yes" else ""),
                    Cell.Text(it.code.orEmpty()),
                )
            }),
            Sheet("Investments", listOf(listOf(Cell.Text("investments")), HOLDINGS_HEADER.map { Cell.Text(it) }) + holdings.map {
                listOf(
                    Cell.Text(it.account), Cell.Text(it.symbol), Cell.Text(it.name), Cell.Num(it.quantity),
                    Cell.Num(Money.toDouble(it.investedCents)), Cell.Text(it.accountCode.orEmpty()),
                )
            }),
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
        return ParsedWorkbook(rows, problems, accounts, holdings)
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

    private val dateFormats = listOf("yyyy-MM-dd", "d/M/yyyy", "d.M.yyyy", "d-M-yyyy", "M/d/yyyy", "yyyy/M/d")
        .map(DateTimeFormatter::ofPattern)

    private fun dateOf(c: Cell): LocalDate? = when (c) {
        is Cell.Date -> c.value
        is Cell.Num -> Xlsx.serialToDate(c.value)
        is Cell.Text -> c.value.trim().split(' ', 'T').first().let { s ->
            dateFormats.firstNotNullOfOrNull { f -> runCatching { LocalDate.parse(s, f) }.getOrNull() }
        }
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
