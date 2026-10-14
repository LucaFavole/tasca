package app.tasca

import app.tasca.domain.Calc
import app.tasca.domain.Money
import app.tasca.domain.Period
import app.tasca.domain.PeriodKind
import app.tasca.domain.oneDecimal
import app.tasca.io.Cell
import app.tasca.io.LedgerSheets
import app.tasca.io.Sheet
import app.tasca.io.Xlsx
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Outputs the Android app showed or wrote before the code moved to the shared module. */
class PinTest {
    @AfterTest fun euro() { Money.currency = "EUR" }

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
            assertEquals(expected.first, Money.format(cents), "$cents")
            assertEquals(expected.second, Money.format(cents, compact = true), "$cents compact")
        }
        assertEquals("+1,00$nb€", Money.signed(100))
        assertEquals("−1,00$nb€", Money.signed(-100))
        assertEquals("0,00$nb€", Money.signed(0))
        Money.currency = "USD"
        assertEquals("1.234,56${nb}USD", Money.format(123456))
        Money.currency = "GBP"
        assertEquals("-1.235$nb£", Money.format(-123456, compact = true))
        Money.currency = "JPY"
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
        for ((d, cents) in expected) assertEquals(cents, Money.fromDouble(d), "$d")
        assertEquals(1234.56, Money.toDouble(123456), 0.0)
        assertEquals(-0.05, Money.toDouble(-5), 0.0)
    }

    @Test fun oneDecimalRoundsHalfUpWithAComma() {
        val expected = mapOf(
            0.0 to "0,0", 0.04 to "0,0", -0.04 to "-0,0", 0.05 to "0,1", 0.15 to "0,2", 0.25 to "0,3", 0.35 to "0,4", 0.45 to "0,5",
            1.05 to "1,1", 2.15 to "2,2", 2.25 to "2,3", 2.35 to "2,4", 9.95 to "10,0", 9.96 to "10,0", 1234.56 to "1234,6",
            -1.25 to "-1,3", 1.0E-4 to "0,0", 3.0 to "3,0", 0.95 to "1,0", 12345678.9 to "12345678,9",
        )
        for ((d, s) in expected) assertEquals(s, oneDecimal(d), "$d")
    }

    @Test fun calculatorEdgeCases() {
        val expected = mapOf(
            ".5" to 50L, "5." to 500L, "." to null, "1÷3×3" to 100L, "0,005×1" to 1L, "5-10,005" to -501L, "2÷3" to 67L, "-5" to null,
            "1,005" to 101L, "1+" to null, "×2" to null, "100÷7÷3" to 476L, "0,125" to 13L, "1 000" to 100_000L, "1..2" to null,
            "12,345×3" to 3704L,
        )
        for ((e, cents) in expected) assertEquals(cents, Calc.evaluate(e), e)
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
            assertEquals(date, LedgerSheets.read(listOf(sheet)).rows.firstOrNull()?.date?.toString(), text)
        }
    }

    @Test fun excelSerialDates() {
        val expected = mapOf(0.0 to "1899-12-30", 1.0 to "1899-12-31", 46304.0 to "2026-10-09", 46304.99 to "2026-10-09", -1.0 to "1899-12-29", 61.0 to "1900-03-01")
        for ((serial, date) in expected) assertEquals(date, Xlsx.serialToDate(serial).toString(), "$serial")
        assertEquals(46304L, Xlsx.dateToSerial(LocalDate(2026, 10, 9)))
    }

    @Test fun periodRanges() {
        val anchor = LocalDate(2026, 10, 11)
        val week = Period.of(PeriodKind.WEEK, anchor)
        assertEquals(LocalDate(2026, 10, 5) to LocalDate(2026, 10, 11), week.start to week.end)
        assertEquals(LocalDate(2026, 9, 28), week.shift(-1).start)
        val month = Period.of(PeriodKind.MONTH, anchor)
        assertEquals(LocalDate(2026, 9, 1) to LocalDate(2026, 9, 30), month.shift(-1).let { it.start to it.end })
        assertEquals(LocalDate(2026, 11, 30), month.shift(1).end)
        assertEquals(LocalDate(2026, 1, 1) to LocalDate(2026, 12, 31), Period.of(PeriodKind.YEAR, anchor).let { it.start to it.end })
        assertEquals("2026", Period.of(PeriodKind.YEAR, anchor).label())
        assertEquals("All time", Period.of(PeriodKind.ALL, anchor).shift(1).label())
        assertEquals(LocalDate(2026, 12, 28), Period.of(PeriodKind.WEEK, LocalDate(2027, 1, 1)).start)
    }
}
