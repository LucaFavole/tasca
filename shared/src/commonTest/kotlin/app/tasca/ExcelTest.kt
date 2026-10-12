package app.tasca

import app.tasca.data.CategoryKind
import app.tasca.data.TxnKind
import app.tasca.domain.Frequency
import app.tasca.io.AccountSheetRow
import app.tasca.io.BudgetSheetRow
import app.tasca.io.CategorySheetRow
import app.tasca.io.Config
import app.tasca.io.HoldingSheetRow
import app.tasca.io.LedgerSheets
import app.tasca.io.RecurringSheetRow
import app.tasca.io.SheetRow
import app.tasca.io.Xlsx
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ExcelTest {
    private val rows = listOf(
        SheetRow(TxnKind.EXPENSE, LocalDate(2026, 10, 9), "Main", null, "Attività fisica", 9000, "scarpe & <calze>"),
        SheetRow(TxnKind.EXPENSE, LocalDate(2026, 10, 9), "Main", null, "Attività fisica", 9000, "scarpe & <calze>"),
        SheetRow(TxnKind.INCOME, LocalDate(2026, 9, 27), "risparmi", null, "Stipendio", 263105, ""),
        SheetRow(TxnKind.TRANSFER, LocalDate(2026, 9, 27), "risparmi", "TR investimenti", null, 200000, "inv"),
    )

    @Test fun exportThenImportGivesBackTheSameRows() {
        val parsed = LedgerSheets.read(Xlsx.read(Xlsx.write(LedgerSheets.write(rows, "EUR"))))
        assertEquals(emptyList(), parsed.problems)
        assertEquals(rows.sortedBy { it.toString() }, parsed.rows.sortedBy { it.toString() })
    }

    @Test fun backupKeepsOpeningBalancesAndInvestments() {
        val accounts = listOf(AccountSheetRow("Main", 969_303, false), AccountSheetRow("Monaco", 0, true))
        val holdings = listOf(HoldingSheetRow("TR investimenti", "SPYY.DE", "SPDR ACWI", 41.678225, 1_106_640))
        val parsed = LedgerSheets.read(Xlsx.read(Xlsx.write(LedgerSheets.write(rows, "EUR", Config(accounts, holdings)))))
        assertEquals(accounts, parsed.config.accounts)
        assertEquals(holdings, parsed.config.holdings)
        assertEquals(rows.size, parsed.rows.size)
    }

    @Test fun accountCodesKeepSameNamedAccountsApart() {
        val coded = listOf(
            SheetRow(TxnKind.EXPENSE, LocalDate(2026, 10, 9), "Main", null, "Spesa", 1000, "", "A1"),
            SheetRow(TxnKind.EXPENSE, LocalDate(2026, 10, 9), "Main", null, "Spesa", 2000, "", "A7"),
            SheetRow(TxnKind.TRANSFER, LocalDate(2026, 10, 8), "Main", "Main", null, 500, "", "A7", "A1"),
        )
        val accounts = listOf(AccountSheetRow("Main", 0, false, "A1"), AccountSheetRow("Main", 100, true, "A7"))
        val holdings = listOf(HoldingSheetRow("Main", "XEON.DE", "Xeon", 1.0, 15_000, "A7"))
        val parsed = LedgerSheets.read(Xlsx.read(Xlsx.write(LedgerSheets.write(coded, "EUR", Config(accounts, holdings)))))
        assertEquals(coded.toSet(), parsed.rows.toSet())
        assertEquals(accounts, parsed.config.accounts)
        assertEquals(holdings, parsed.config.holdings)
    }

    @Test fun backupKeepsRegularPaymentsCategoriesBudgetsAndSettings() {
        val config = Config(
            recurring = listOf(
                RecurringSheetRow("Affitto", TxnKind.EXPENSE, 80_000, "Main", "A1", null, null, "Casa",
                    Frequency.MONTHLY, 1, LocalDate(2026, 1, 25), null, 9, true),
                RecurringSheetRow("Data", TxnKind.TRANSFER, 20_000, "Main", "A1", "risparmi", "A2", null,
                    Frequency.WEEKLY, 2, LocalDate(2026, 3, 1), LocalDate(2026, 12, 31), 15, false),
            ),
            categories = listOf(CategorySheetRow("Casa", CategoryKind.EXPENSE, 0xFF6C63FF.toInt(), "home")),
            budgets = listOf(BudgetSheetRow("Casa", 90_000)),
            settings = mapOf("currency" to "EUR", "theme" to "DARK", "taxRate" to "26.0", "reminder" to "true"),
        )
        val parsed = LedgerSheets.read(Xlsx.read(Xlsx.write(LedgerSheets.write(rows, "EUR", config))))
        assertEquals(emptyList(), parsed.problems)
        assertEquals(config, parsed.config)
        assertEquals(rows.size, parsed.rows.size, "a payment named Data is not read as a transaction")
    }
}
