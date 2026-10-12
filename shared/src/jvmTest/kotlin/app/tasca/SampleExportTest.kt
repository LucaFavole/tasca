package app.tasca

import app.tasca.data.TxnKind
import app.tasca.io.LedgerSheets
import app.tasca.io.SheetRow
import app.tasca.io.Xlsx
import kotlinx.datetime.LocalDate
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reads a real export of the previous app, kept outside the repository; skipped when it is absent. */
class SampleExportTest {
    @Test fun readsThePreviousAppExport() {
        val dir = File("../Resources/Export")
        val file = dir.listFiles { f -> f.name.endsWith(".xlsx") }?.firstOrNull()
        assumeTrue("export sample not present", file != null)
        val parsed = LedgerSheets.read(Xlsx.read(file!!.readBytes()))
        val byKind = parsed.rows.groupingBy { it.kind }.eachCount()
        assertEquals(emptyList(), parsed.problems)
        assertEquals(mapOf(TxnKind.EXPENSE to 883, TxnKind.INCOME to 43, TxnKind.TRANSFER to 39), byKind)
        val first = parsed.rows.first()
        assertEquals(SheetRow(TxnKind.EXPENSE, LocalDate(2026, 10, 11), "Main", null, "Attività fisica", 9000, "scarpe da corsa"), first)
        assertTrue(parsed.rows.any { it.kind == TxnKind.TRANSFER && it.account == "risparmi" && it.toAccount == "TR investimenti" })
    }
}
