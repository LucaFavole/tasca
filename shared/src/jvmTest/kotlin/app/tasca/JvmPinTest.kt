package app.tasca

import app.tasca.data.CategoryKind
import app.tasca.data.TxnKind
import app.tasca.domain.Frequency
import app.tasca.domain.Period
import app.tasca.domain.PeriodKind
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
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

/** The parts of [PinTest] that depend on the JVM: the default locale, and the exact bytes java.util.zip stores. */
class JvmPinTest {
    @Test fun periodLabelsInItalian() {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.ITALY)
        try {
            val anchor = LocalDate(2026, 10, 11)
            assertEquals("5 ott – 11 ott", Period.of(PeriodKind.WEEK, anchor).label())
            assertEquals("Ottobre 2026", Period.of(PeriodKind.MONTH, anchor).label())
            assertEquals("Novembre 2026", Period.of(PeriodKind.MONTH, anchor).shift(1).label())
            assertEquals("28 dic – 3 gen", Period.of(PeriodKind.WEEK, LocalDate(2027, 1, 1)).label())
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test fun exportedWorkbookIsUnchanged() {
        val rows = listOf(
            SheetRow(TxnKind.EXPENSE, LocalDate(2026, 10, 9), "Main", null, "Attività fisica", 9000, "scarpe & <calze> \"x\" \u0001 tab\tnl\n€"),
            SheetRow(TxnKind.INCOME, LocalDate(2026, 9, 27), "risparmi", null, "Stipendio", 180000, "", "A2"),
            SheetRow(TxnKind.TRANSFER, LocalDate(2026, 9, 27), "risparmi", "TR investimenti", null, 50000, "inv", "A2", "A3"),
            SheetRow(TxnKind.EXPENSE, LocalDate(1999, 1, 1), "Main", null, "Altro", 1, "x"),
        )
        val config = Config(
            accounts = listOf(AccountSheetRow("Main", 120_000, false, "A1"), AccountSheetRow("Monaco", -5, true)),
            holdings = listOf(HoldingSheetRow("TR investimenti", "SPYY.DE", "SPDR ACWI", 12.345678, 333_333, "A3"), HoldingSheetRow("Main", "X", "Y", 1e-7, 0)),
            recurring = listOf(
                RecurringSheetRow("Affitto", TxnKind.EXPENSE, 80_000, "Main", "A1", null, null, "Casa", Frequency.MONTHLY, 1, LocalDate(2026, 1, 25), null, 9, true),
                RecurringSheetRow("Data", TxnKind.TRANSFER, 20_000, "Main", "A1", "risparmi", "A2", null, Frequency.WEEKLY, 2, LocalDate(2026, 3, 1), LocalDate(2026, 12, 31), 15, false),
            ),
            categories = listOf(
                CategorySheetRow("Casa", CategoryKind.EXPENSE, 0xFF6C63FF.toInt(), "home"), CategorySheetRow("Zero", CategoryKind.INCOME, 0, "x"),
                CategorySheetRow("Neg", CategoryKind.EXPENSE, -1, "y"), CategorySheetRow("Small", CategoryKind.EXPENSE, 0x7F, "z"),
            ),
            budgets = listOf(BudgetSheetRow("Casa", 90_000)),
            settings = mapOf("currency" to "EUR", "theme" to "DARK", "taxRate" to "26.0", "reminder" to "true"),
        )
        val hashes = mutableMapOf<String, String>()
        ZipInputStream(Xlsx.write(LedgerSheets.write(rows, "EUR", config)).inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                hashes[e.name] = MessageDigest.getInstance("SHA-256").digest(z.readBytes()).joinToString("") { "%02x".format(it) }
            }
        }
        assertEquals(
            mapOf(
                "[Content_Types].xml" to "bf380604a4f93beb7bc33eadcd149874633c47ec1e3c5320f05fcb838195457d",
                "_rels/.rels" to "f2f3948a1b6f661f2a5169a86b7029166acd4b79d7abfd37fa2b646fe302bfd6",
                "xl/workbook.xml" to "9cc8394b454b2db5a1bf3cb0e6fc77ed74c9aefc5c81a72fd962b247861fb8c7",
                "xl/_rels/workbook.xml.rels" to "aa2112fd77ae87c12c43b6f997dd51777453bb04cde896cfaa445f7335383ec7",
                "xl/styles.xml" to "fa5a42d47b48ac5976c6bab5df25fedba21e6e043bbc65345760e178983c0323",
                "xl/worksheets/sheet1.xml" to "1770deaac980016200a77e6a16c1e72c5d8a994f4ea077419120c29f115fd40b",
                "xl/worksheets/sheet2.xml" to "89f4fa682d8bd73592c6e6f73fcdaccc468bbc4a44cc792dbed91a6adf1a977f",
                "xl/worksheets/sheet3.xml" to "bf139e93aab620be1be00853f1e752f3c7544b53f9c29b0712c28eabe652b164",
                "xl/worksheets/sheet4.xml" to "aca2d29540e3ef781d2992572c8722b268e5bd65fc92634fd3c009684da794a8",
                "xl/worksheets/sheet5.xml" to "2a0f8d1ed3e336fc387aa75024dfc1620bf51cdfbf4a1e61f80b63f86ff607cf",
                "xl/worksheets/sheet6.xml" to "044f6a6067fa57c10f5f5e00474f646a0450d9ffdba2584f08d99ae97f42d003",
                "xl/worksheets/sheet7.xml" to "0aaf46de933d5ef3cd39ea9f56152557661369a9c6bd34cfa6aaf913ae3d0fd5",
                "xl/worksheets/sheet8.xml" to "d84bd0947371f46dac6959b1094b204c0e29c5c9493665be8ad560445ca8cab6",
                "xl/worksheets/sheet9.xml" to "27d1c98e8368ac5481d0bbb2d97d839f6a9d24efefeac47c575ac6224a2d58bf",
            ),
            hashes,
        )
    }
}
