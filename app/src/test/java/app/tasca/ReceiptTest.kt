package app.tasca

import app.tasca.data.Category
import app.tasca.data.CategoryKind
import app.tasca.domain.ReceiptLine
import app.tasca.domain.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptTest {
    private val today = LocalDate.of(2026, 10, 11)
    private fun parse(vararg rows: String) = ReceiptParser.parseText(rows.toList(), today)

    @Test fun supermarketWithDiscountCashAndChange() {
        val r = parse(
            "ESSELUNGA S.P.A.", "Via Washington 12 Milano", "P.IVA 01255720169", "DOCUMENTO COMMERCIALE di vendita o prestazione",
            "LATTE PS 1L  1,29", "PANE  2,10", "SUBTOTALE  3,39", "SCONTO FIDATY  -1,00", "TOTALE COMPLESSIVO  2,39",
            "di cui IVA  0,22", "CONTANTE  10,00", "RESTO  7,61", "10-10-2026 18:42  DOC.N. 0123-0456",
        )
        assertEquals(239L, r.amountCents)
        assertTrue(r.confident)
        assertEquals(LocalDate.of(2026, 10, 10), r.date)
        assertEquals("Esselunga", r.store)
    }

    @Test fun ocrSlipsInLabelAndDigits() {
        val r = parse("C0NAD", "BANANE 1,8O", "T0TALE C0MPLESSlVO 12,4O", "PAGAMENTO ELETTRONICO 12,40", "O9/1O/2026 19:02")
        assertEquals(1240L, r.amountCents)
        assertTrue(r.confident)
        assertEquals(LocalDate.of(2026, 10, 9), r.date)
    }

    @Test fun totalOnTheNextRowAndShortYear() {
        val r = parse("Bar Centrale", "CAFFE  1,20", "BRIOCHE  1,50", "TOTALE EURO", "2,70", "09/10/26 08:15")
        assertEquals(270L, r.amountCents)
        assertEquals(LocalDate.of(2026, 10, 9), r.date)
        assertEquals("Bar Centrale", r.store)
    }

    @Test fun subtotalAndVatNeverWinOverTheRealTotal() {
        val r = parse("Pizzeria Da Mario", "MARGHERITA 7,00", "BIRRA 4,50", "TOTALE IVA 1,04", "SUBTOTALE 11,50", "TOTALE 11,50")
        assertEquals(1150L, r.amountCents)
    }

    @Test fun withoutATotalLineCashMinusChangeAndItemsDecide() {
        val r = parse("Trattoria", "PRIMO 9,00", "SECONDO 12,00", "CONTANTE 50,00", "RESTO 29,00")
        assertEquals(2100L, r.amountCents)
    }

    @Test fun biggerPrintMarksTheTotalWhenLabelsAreUnreadable() {
        val lines = listOf(
            ReceiptLine("Negozio", 40), ReceiptLine("ARTICOLO A 3,00", 30), ReceiptLine("ARTICOLO B 5,00", 30),
            ReceiptLine("xxxxxx 8,00", 60), ReceiptLine("xxxx 20,00", 30),
        )
        assertEquals(800L, ReceiptParser.parse(lines, today).amountCents)
    }

    @Test fun thousandsMonthNamesAndLowConfidence() {
        assertEquals(123_456L, parse("TOTALE 1.234,56").amountCents)
        assertEquals(LocalDate.of(2026, 9, 3), parse("3 SET 2026 12:10").date)
        assertFalse(parse("PANE 2,00", "LATTE 2,00").confident)
    }

    @Test fun categoryComesFromTheShopBeforeTheItems() {
        val categories = listOf(
            Category(1, "Alimentari", CategoryKind.EXPENSE, 0, "cart"),
            Category(2, "Caffè", CategoryKind.EXPENSE, 0, "coffee"),
            Category(3, "Trasporti", CategoryKind.EXPENSE, 0, "bus"),
        )
        assertEquals(1L, parse("LIDL ITALIA S.R.L.", "CAFFE MACINATO 3,49", "TOTALE 3,49", "CARTA 3,49").categoryIn(categories))
        assertEquals(2L, parse("Da Gino", "CAFFE 1,20", "TOTALE 1,20").categoryIn(categories))
    }

    @Test fun nothingReadable() {
        val r = parse("###", "12/34/5678")
        assertNull(r.amountCents)
        assertNull(r.date)
        assertNull(r.store)
    }
}
