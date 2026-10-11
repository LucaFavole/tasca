package app.tasca

import app.tasca.domain.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReceiptTest {
    private val today = LocalDate.of(2026, 10, 11)

    @Test fun supermarketReceipt() {
        val rows = listOf(
            "ESSELUNGA S.P.A.",
            "Via Washington 12 Milano",
            "P.IVA 01255720169",
            "DOCUMENTO COMMERCIALE di vendita o prestazione",
            "LATTE PS 1L  1,29",
            "PANE  2,10",
            "SUBTOTALE  23,40",
            "SCONTO FIDATY  -1,00",
            "TOTALE COMPLESSIVO  22,40",
            "di cui IVA  2,04",
            "CONTANTE  50,00",
            "RESTO  27,60",
            "10-10-2026 18:42  DOC.N. 0123-0456",
        )
        val r = ReceiptParser.parse(rows, today)
        assertEquals(2240L, r.amountCents)
        assertEquals(LocalDate.of(2026, 10, 10), r.date)
        assertEquals("Esselunga", r.store)
    }

    @Test fun totalOnTheNextRowAndShortYear() {
        val rows = listOf("Bar Centrale", "CAFFE  1,20", "BRIOCHE  1,50", "TOTALE EURO", "2,70", "PAGAMENTO ELETTRONICO 2,70", "09/10/26 08:15")
        val r = ReceiptParser.parse(rows, today)
        assertEquals(270L, r.amountCents)
        assertEquals(LocalDate.of(2026, 10, 9), r.date)
        assertEquals("Bar Centrale", r.store)
    }

    @Test fun withoutATotalLineTheLargestAmountWinsButNotTheCashGiven() {
        val rows = listOf("Pizzeria Da Mario", "MARGHERITA 7,00", "BIRRA 4,50", "CONTANTE 20,00")
        assertEquals(700L, ReceiptParser.parse(rows, today).amountCents)
        assertEquals(123_456L, ReceiptParser.parse(listOf("TOTALE 1.234,56"), today).amountCents)
    }

    @Test fun nothingReadable() {
        val r = ReceiptParser.parse(listOf("###", "12/34/5678"), today)
        assertNull(r.amountCents)
        assertNull(r.date)
        assertNull(r.store)
    }
}
