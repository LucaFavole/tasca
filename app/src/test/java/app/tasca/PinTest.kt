package app.tasca

import app.tasca.data.CategoryKind
import app.tasca.data.TxnKind
import app.tasca.domain.Calc
import app.tasca.domain.Frequency
import app.tasca.domain.Money
import app.tasca.domain.Period
import app.tasca.domain.PeriodKind
import app.tasca.domain.oneDecimal
import app.tasca.io.AccountSheetRow
import app.tasca.io.BudgetSheetRow
import app.tasca.io.Cell
import app.tasca.io.CategorySheetRow
import app.tasca.io.Config
import app.tasca.io.HoldingSheetRow
import app.tasca.io.LedgerSheets
import app.tasca.io.RecurringSheetRow
import app.tasca.io.Sheet
import app.tasca.io.SheetRow
import app.tasca.io.Xlsx
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import java.util.zip.ZipInputStream

/** Outputs the Android app shows or writes today, captured before the code moves to the shared module. */
class PinTest {
    @After fun euro() { Money.currency = Currency.getInstance("EUR") }

    @Test fun moneyFormatsTheItalianWay() {
        val nb = ' '
        val euro = mapOf(
            0L to ("0,00$nb€" to "0$nb€"), 5L to ("0,05$nb€" to "0,05$nb€"), -5L to ("-0,05$nb€" to "-0,05$nb€"),
            100L to ("1,00$nb€" to "1$nb€"), 123456L to ("1.234,56$nb€" to "1.235$nb€"), -123456L to ("-1.234,56$nb€" to "-1.235$nb€"),
            123400L to ("1.234,00$nb€" to "1.234$nb€"), 99_950L to ("999,50$nb€" to "999,50$nb€"), 100_000L to ("1.000,00$nb€" to "1.000$nb€"),
            150_050L to ("1.500,50$nb€" to "1.500$nb€"), 150_150L to ("1.501,50$nb€" to "1.502$nb€"), 250_050L to ("2.500,50$nb€" to "2.500$nb€"),
            123456789L to ("1.234.567,89$nb€" to "1.234.568$nb€"), -100_000L to ("-1.000,00$nb€" to "-1.000$nb€"),
        )
        for ((cents, expected) in euro) {
            assertEquals("$cents", expected.first, Money.format(cents))
            assertEquals("$cents compact", expected.second, Money.format(cents, compact = true))
        }
        assertEquals("+1,00$nb€", Money.signed(100))
        assertEquals("-1,00$nb€", Money.signed(-100))
        assertEquals("0,00$nb€", Money.signed(0))
        Money.currency = Currency.getInstance("USD")
        assertEquals("1.234,56${nb}USD", Money.format(123456))
        Money.currency = Currency.getInstance("GBP")
        assertEquals("-1.235$nb£", Money.format(-123456, compact = true))
        Money.currency = Currency.getInstance("JPY")
        assertEquals("1.234,56${nb}JPY", Money.format(123456))
    }

    @Test fun plainAmountsDropTrailingZeros() {
        val expected = mapOf(
            0L to "0", 5L to "0.05", -5L to "-0.05", 100L to "1", 1250L to "12.5", 1200L to "12", 123456L to "1234.56",
            -123400L to "-1234", 100000L to "1000", Long.MAX_VALUE to "92233720368547758.07", Long.MIN_VALUE to "-92233720368547758.08",
        )
        for ((cents, s) in expected) assertEquals(s, Money.plain(cents))
    }

    @Test fun doublesRoundHalfUpOnTheirDecimalForm() {
        val expected = mapOf(
            0.0 to 0L, 1.005 to 101L, 2.675 to 268L, 0.125 to 13L, -0.125 to -13L, 12.345 to 1235L, 0.001 to 0L, 1.0E7 to 1_000_000_000L,
            123456.785 to 12_345_679L, 0.004999 to 0L, -0.005 to -1L, 1.0E-5 to 0L, 99999.995 to 10_000_000L,
        )
        for ((d, cents) in expected) assertEquals("$d", cents, Money.fromDouble(d))
        assertEquals(1234.56, Money.toDouble(123456), 0.0)
        assertEquals(-0.05, Money.toDouble(-5), 0.0)
    }

    @Test fun oneDecimalRoundsHalfUpWithAComma() {
        val expected = mapOf(
            0.0 to "0,0", 0.04 to "0,0", -0.04 to "-0,0", 0.05 to "0,1", 0.15 to "0,2", 0.25 to "0,3", 0.35 to "0,4", 0.45 to "0,5",
            1.05 to "1,1", 2.15 to "2,2", 2.25 to "2,3", 2.35 to "2,4", 9.95 to "10,0", 9.96 to "10,0", 1234.56 to "1234,6",
            -1.25 to "-1,3", 1.0E-4 to "0,0", 3.0 to "3,0", 0.95 to "1,0", 12345678.9 to "12345678,9",
        )
        for ((d, s) in expected) assertEquals("$d", s, oneDecimal(d))
    }

    @Test fun calculatorEdgeCases() {
        val expected = mapOf(
            ".5" to 50L, "5." to 500L, "." to null, "1÷3×3" to 100L, "0,005×1" to 1L, "5-10,005" to -501L, "2÷3" to 67L, "-5" to null,
            "1,005" to 101L, "1+" to null, "×2" to null, "100÷7÷3" to 476L, "0,125" to 13L, "1 000" to 100_000L, "1..2" to null,
            "12,345×3" to 3704L,
        )
        for ((e, cents) in expected) assertEquals(e, cents, Calc.evaluate(e))
    }

    @Test fun datesInTextCells() {
        val expected = mapOf(
            "2026-10-09" to "2026-10-09", "2026-10-09T10:00" to "2026-10-09", "2026-10-09 10:00:00" to "2026-10-09",
            "9/10/2026" to "2026-10-09", "09/10/2026" to "2026-10-09", "9.10.2026" to "2026-10-09", "9-10-2026" to "2026-10-09",
            "10/31/2026" to "2026-10-31", "2026/1/5" to "2026-01-05", "31/2/2026" to "2026-02-28", "29/2/2025" to "2025-02-28",
            "29/2/2024" to "2024-02-29", "31/4/2026" to "2026-04-30", "32/1/2026" to null, "2026-1-5" to null, "26/10/26" to null,
            "0/1/2026" to null, "1/13/2026" to "2026-01-13", "2026-10-9" to null, "02026-10-09" to null, "1/1/0000" to null,
            "1/1/0001" to "0001-01-01", "  2026-10-09  " to "2026-10-09", "2026-10-09x" to null, "+2026-10-09" to null,
            "2026-02-30" to "2026-02-28", "2026-13-01" to null, "007/010/2026" to "2026-10-07", "9/10/12026" to null, "" to null,
            "abc" to null, "1/1/999999999" to null, "1/1/1000000000" to null, "1.1.2026" to "2026-01-01", "2026/02/31" to "2026-02-28",
        )
        for ((text, date) in expected) {
            val sheet = Sheet("Expenses", listOf(listOf(Cell.Text("Date"), Cell.Text("Amount")), listOf(Cell.Text(text), Cell.Num(1.0))))
            assertEquals(text, date, LedgerSheets.read(listOf(sheet)).rows.firstOrNull()?.date?.toString())
        }
    }

    @Test fun excelSerialDates() {
        val expected = mapOf(0.0 to "1899-12-30", 1.0 to "1899-12-31", 46304.0 to "2026-10-09", 46304.99 to "2026-10-09", -1.0 to "1899-12-29", 61.0 to "1900-03-01")
        for ((serial, date) in expected) assertEquals("$serial", date, Xlsx.serialToDate(serial).toString())
        assertEquals(46304L, Xlsx.dateToSerial(LocalDate.of(2026, 10, 9)))
    }

    @Test fun periodsAndTheirLabels() {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.ITALY)
        try {
            val anchor = LocalDate.of(2026, 10, 11)
            val week = Period.of(PeriodKind.WEEK, anchor)
            assertEquals(LocalDate.of(2026, 10, 5) to LocalDate.of(2026, 10, 11), week.start to week.end)
            assertEquals(LocalDate.of(2026, 9, 28), week.shift(-1).start)
            val month = Period.of(PeriodKind.MONTH, anchor)
            assertEquals("Ottobre 2026", month.label())
            assertEquals(LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30), month.shift(-1).let { it.start to it.end })
            assertEquals("Novembre 2026", month.shift(1).label())
            assertEquals("2026", Period.of(PeriodKind.YEAR, anchor).label())
            assertEquals("All time", Period.of(PeriodKind.ALL, anchor).shift(1).label())
            assertEquals("28 dic – 3 gen", Period.of(PeriodKind.WEEK, LocalDate.of(2027, 1, 1)).label())
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test fun exportedWorkbookIsUnchanged() {
        val rows = listOf(
            SheetRow(TxnKind.EXPENSE, LocalDate.of(2026, 10, 9), "Main", null, "Attività fisica", 9000, "scarpe & <calze> \"x\" \u0001 tab\tnl\n€"),
            SheetRow(TxnKind.INCOME, LocalDate.of(2026, 9, 27), "risparmi", null, "Stipendio", 263105, "", "A2"),
            SheetRow(TxnKind.TRANSFER, LocalDate.of(2026, 9, 27), "risparmi", "TR investimenti", null, 200000, "inv", "A2", "A3"),
            SheetRow(TxnKind.EXPENSE, LocalDate.of(1999, 1, 1), "Main", null, "Altro", 1, "x"),
        )
        val config = Config(
            accounts = listOf(AccountSheetRow("Main", 969_303, false, "A1"), AccountSheetRow("Monaco", -5, true)),
            holdings = listOf(HoldingSheetRow("TR investimenti", "SPYY.DE", "SPDR ACWI", 41.678225, 1_106_640, "A3"), HoldingSheetRow("Main", "X", "Y", 1e-7, 0)),
            recurring = listOf(
                RecurringSheetRow("Affitto", TxnKind.EXPENSE, 80_000, "Main", "A1", null, null, "Casa", Frequency.MONTHLY, 1, LocalDate.of(2026, 1, 25), null, 9, true),
                RecurringSheetRow("Data", TxnKind.TRANSFER, 20_000, "Main", "A1", "risparmi", "A2", null, Frequency.WEEKLY, 2, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 31), 15, false),
            ),
            categories = listOf(
                CategorySheetRow("Casa", CategoryKind.EXPENSE, 0xFF6C63FF.toInt(), "home"), CategorySheetRow("Zero", CategoryKind.INCOME, 0, "x"),
                CategorySheetRow("Neg", CategoryKind.EXPENSE, -1, "y"), CategorySheetRow("Small", CategoryKind.EXPENSE, 0x7F, "z"),
            ),
            budgets = listOf(BudgetSheetRow("Casa", 90_000)),
            settings = mapOf("currency" to "EUR", "theme" to "DARK", "taxRate" to "26.0", "reminder" to "true"),
        )
        val bytes = ByteArrayOutputStream().also { Xlsx.write(LedgerSheets.write(rows, "EUR", config), it) }.toByteArray()
        val hashes = mutableMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { z ->
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
                "xl/worksheets/sheet2.xml" to "d8160964ca786261abcd418d44cb7e8de40a92be6dbaad979b89829b61287824",
                "xl/worksheets/sheet3.xml" to "f3fb0c0ac2f9fb09b59a49dc2107906d5211a4e23991d785d9fff0727f3b1083",
                "xl/worksheets/sheet4.xml" to "8eeb07dc7452af8dbc0d9421b54c699ac047ac4d008a723f0c2e980f6b72e64f",
                "xl/worksheets/sheet5.xml" to "ab29011a6f71517916b085df418f654ba22bc4eb30c35014d0fdd1f61991b283",
                "xl/worksheets/sheet6.xml" to "044f6a6067fa57c10f5f5e00474f646a0450d9ffdba2584f08d99ae97f42d003",
                "xl/worksheets/sheet7.xml" to "0aaf46de933d5ef3cd39ea9f56152557661369a9c6bd34cfa6aaf913ae3d0fd5",
                "xl/worksheets/sheet8.xml" to "d84bd0947371f46dac6959b1094b204c0e29c5c9493665be8ad560445ca8cab6",
                "xl/worksheets/sheet9.xml" to "27d1c98e8368ac5481d0bbb2d97d839f6a9d24efefeac47c575ac6224a2d58bf",
            ),
            hashes,
        )
    }
}
