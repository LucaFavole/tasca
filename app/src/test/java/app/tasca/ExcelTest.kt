package app.tasca

import app.tasca.data.TxnKind
import app.tasca.io.AccountSheetRow
import app.tasca.io.HoldingSheetRow
import app.tasca.io.LedgerSheets
import app.tasca.io.SheetRow
import app.tasca.io.Xlsx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

class ExcelTest {
    private val rows = listOf(
        SheetRow(TxnKind.EXPENSE, LocalDate.of(2026, 10, 9), "Main", null, "Attività fisica", 9000, "scarpe & <calze>"),
        SheetRow(TxnKind.EXPENSE, LocalDate.of(2026, 10, 9), "Main", null, "Attività fisica", 9000, "scarpe & <calze>"),
        SheetRow(TxnKind.INCOME, LocalDate.of(2026, 9, 27), "risparmi", null, "Stipendio", 263105, ""),
        SheetRow(TxnKind.TRANSFER, LocalDate.of(2026, 9, 27), "risparmi", "TR investimenti", null, 200000, "inv"),
    )

    @Test fun exportThenImportGivesBackTheSameRows() {
        val out = ByteArrayOutputStream()
        Xlsx.write(LedgerSheets.write(rows, "EUR"), out)
        val parsed = LedgerSheets.read(Xlsx.read(ByteArrayInputStream(out.toByteArray())))
        assertEquals(emptyList<String>(), parsed.problems)
        assertEquals(rows.sortedBy { it.toString() }, parsed.rows.sortedBy { it.toString() })
    }

    @Test fun readsThePreviousAppExport() {
        val dir = File("../Resources/Export")
        val file = dir.listFiles { f -> f.name.endsWith(".xlsx") }?.firstOrNull()
        assumeTrue("export sample not present", file != null)
        val parsed = LedgerSheets.read(file!!.inputStream().use(Xlsx::read))
        val byKind = parsed.rows.groupingBy { it.kind }.eachCount()
        assertEquals(emptyList<String>(), parsed.problems)
        assertEquals(mapOf(TxnKind.EXPENSE to 883, TxnKind.INCOME to 43, TxnKind.TRANSFER to 39), byKind)
        val first = parsed.rows.first()
        assertEquals(SheetRow(TxnKind.EXPENSE, LocalDate.of(2026, 10, 11), "Main", null, "Attività fisica", 9000, "scarpe da corsa"), first)
        assertTrue(parsed.rows.any { it.kind == TxnKind.TRANSFER && it.account == "risparmi" && it.toAccount == "TR investimenti" })
    }

    @Test fun backupKeepsOpeningBalancesAndInvestments() {
        val accounts = listOf(AccountSheetRow("Main", 969_303, false), AccountSheetRow("Monaco", 0, true))
        val holdings = listOf(HoldingSheetRow("TR investimenti", "SPYY.DE", "SPDR ACWI", 41.678225, 1_106_640))
        val out = ByteArrayOutputStream()
        Xlsx.write(LedgerSheets.write(rows, "EUR", accounts, holdings), out)
        val parsed = LedgerSheets.read(Xlsx.read(ByteArrayInputStream(out.toByteArray())))
        assertEquals(accounts, parsed.accounts)
        assertEquals(holdings, parsed.holdings)
        assertEquals(rows.size, parsed.rows.size)
    }

    @Test fun accountCodesKeepSameNamedAccountsApart() {
        val coded = listOf(
            SheetRow(TxnKind.EXPENSE, LocalDate.of(2026, 10, 9), "Main", null, "Spesa", 1000, "", "A1"),
            SheetRow(TxnKind.EXPENSE, LocalDate.of(2026, 10, 9), "Main", null, "Spesa", 2000, "", "A7"),
            SheetRow(TxnKind.TRANSFER, LocalDate.of(2026, 10, 8), "Main", "Main", null, 500, "", "A7", "A1"),
        )
        val accounts = listOf(AccountSheetRow("Main", 0, false, "A1"), AccountSheetRow("Main", 100, true, "A7"))
        val holdings = listOf(HoldingSheetRow("Main", "XEON.DE", "Xeon", 1.0, 15_000, "A7"))
        val out = ByteArrayOutputStream()
        Xlsx.write(LedgerSheets.write(coded, "EUR", accounts, holdings), out)
        val parsed = LedgerSheets.read(Xlsx.read(ByteArrayInputStream(out.toByteArray())))
        assertEquals(coded.toSet(), parsed.rows.toSet())
        assertEquals(accounts, parsed.accounts)
        assertEquals(holdings, parsed.holdings)
    }
}
